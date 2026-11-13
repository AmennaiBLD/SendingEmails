import { randomInt } from "node:crypto";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { auditInTx } from "../shared/audit";
import { C } from "../shared/firebase";
import { newTicketSecret, registrationKeyId, ticketCode } from "../shared/ticketCode";
import { isEmail, normalizeEmail, sha256Hex, str } from "../shared/validation";
import { EmailProvider } from "../notifications/providers";
import { loadClubName } from "../notifications/outbox";
import { ticketAccessCodeEmail } from "../notifications/templates";

/** The ticket returned to its owner; the app draws the QR from `ticketCode`. */
export interface TicketView {
  registrationId: string;
  eventId: string;
  eventTitle: string;
  participantName: string;
  location: string;
  timezone: string;
  startAt: number;
  endAt: number;
  ticketVersion: number;
  ticketCode: string;
  emailQueued: boolean;
  whatsappQueued: boolean;
}

const CODE_TTL_MS = 15 * 60 * 1000;
const MAX_ATTEMPTS = 5;
const RESEND_COOLDOWN_MS = 60 * 1000;

/**
 * Step 1 of ticket retrieval for a participant without an account: email a one-time code to
 * the address on the registration. The answer is the same whether or not a registration
 * exists, so the endpoint cannot be used to find out who registered.
 */
export async function requestTicketAccessCore(
  db: Firestore, raw: Record<string, unknown>, email: EmailProvider, nowMs: number = Date.now(),
): Promise<{ requestId: string }> {
  const eventId = str(raw.eventId, 64);
  const normalizedEmail = normalizeEmail(raw.email);
  const requestRef = db.collection(C.ticketAccessRequests).doc();
  const blank = { requestId: requestRef.id };
  if (!eventId || !isEmail(normalizedEmail)) return blank;

  const keyId = registrationKeyId(eventId, normalizedEmail);
  const keySnap = await db.collection(C.registrationKeys).doc(keyId).get();
  const registrationId: string | null = keySnap.data()?.registrationId ?? null;
  const regSnap = registrationId ? await db.collection(C.registrations).doc(registrationId).get() : null;
  const reg = regSnap?.data();
  // Only an approved registration has a QR ticket to give back.
  const usable = !!reg && reg.status === "REGISTERED";

  const recent = await db.collection(C.ticketAccessRequests).where("keyId", "==", keyId).orderBy("createdAt", "desc").limit(1).get();
  const cooling = recent.docs[0] && nowMs - (recent.docs[0].data().createdAt as Timestamp).toMillis() < RESEND_COOLDOWN_MS;

  const code = String(randomInt(0, 1_000_000)).padStart(6, "0");
  await requestRef.set({
    keyId, registrationId: usable && !cooling ? registrationId : null,
    codeHash: sha256Hex(`${requestRef.id}|${code}`), attempts: 0, used: false,
    createdAt: Timestamp.fromMillis(nowMs), expiresAt: Timestamp.fromMillis(nowMs + CODE_TTL_MS),
  });
  if (usable && !cooling) {
    try {
      const eventTitle = (await db.collection(C.events).doc(eventId).get()).data()?.title ?? "your event";
      await email.send({ to: reg!.email, content: ticketAccessCodeEmail(await loadClubName(db), eventTitle, code), attachments: [], idempotencyKey: `access-${requestRef.id}` });
    } catch (error) {
      logger.warn("Ticket access code email failed", { message: (error as Error).message });
    }
  }
  return blank;
}

export type TicketAccessResult = "OK" | "INVALID_CODE" | "EXPIRED" | "TOO_MANY_ATTEMPTS";

/**
 * Step 2: the code proves control of the registered mailbox. `replace` issues a new ticket
 * (new version and secret), which revokes every QR code delivered before.
 */
export async function confirmTicketAccessCore(
  db: Firestore, raw: Record<string, unknown>, nowMs: number = Date.now(),
): Promise<{ result: TicketAccessResult; ticket?: TicketView }> {
  const requestId = str(raw.requestId, 64);
  const code = str(raw.code, 12);
  const replace = raw.replace === true;
  if (!requestId || !/^\d{6}$/.test(code)) return { result: "INVALID_CODE" };
  const requestRef = db.collection(C.ticketAccessRequests).doc(requestId);

  return db.runTransaction(async (tx): Promise<{ result: TicketAccessResult; ticket?: TicketView }> => {
    const request = (await tx.get(requestRef)).data();
    if (!request || request.used) return { result: "INVALID_CODE" };
    if ((request.expiresAt as Timestamp).toMillis() < nowMs) return { result: "EXPIRED" };
    if (request.attempts >= MAX_ATTEMPTS) return { result: "TOO_MANY_ATTEMPTS" };
    if (!request.registrationId || request.codeHash !== sha256Hex(`${requestId}|${code}`)) {
      tx.update(requestRef, { attempts: request.attempts + 1 });
      return { result: "INVALID_CODE" };
    }
    const regRef = db.collection(C.registrations).doc(request.registrationId);
    const reg = (await tx.get(regRef)).data();
    if (!reg || reg.status === "CANCELLED" || typeof reg.ticketSecret !== "string") return { result: "INVALID_CODE" };
    const event = (await tx.get(db.collection(C.events).doc(reg.eventId))).data();
    if (!event) return { result: "INVALID_CODE" };
    tx.update(requestRef, { used: true });
    let ticketVersion = reg.ticketVersion as number;
    let secret = reg.ticketSecret as string;
    if (replace) {
      ticketVersion += 1;
      secret = newTicketSecret();
      tx.update(regRef, { ticketVersion, ticketSecret: secret, updatedAt: Timestamp.fromMillis(nowMs) });
      auditInTx(db, tx, { actorId: "participant", actorName: reg.participantName, action: "TICKET_REPLACED", entityType: "registration", entityId: regRef.id, eventId: reg.eventId, reason: "Verified by email code", newValue: { ticketVersion } });
    }
    return {
      result: "OK",
      ticket: {
        registrationId: regRef.id, eventId: reg.eventId, eventTitle: event.title, participantName: reg.participantName,
        location: event.location, timezone: event.timezone,
        startAt: (event.startAt as Timestamp).toMillis(), endAt: (event.endAt as Timestamp).toMillis(),
        ticketVersion, ticketCode: ticketCode(regRef.id, ticketVersion, secret), emailQueued: false, whatsappQueued: false,
      },
    };
  });
}
