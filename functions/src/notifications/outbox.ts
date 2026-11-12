import { DocumentData, Firestore, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { NOTIFICATION_LEASE_MS, NOTIFICATION_MAX_ATTEMPTS, retryDelayMs } from "../shared/config";
import { C, CLUB_SETTINGS_DOC } from "../shared/firebase";
import { formatEventDate, formatEventTime } from "../shared/time";
import { ticketCode } from "../shared/ticketCode";
import { renderTicketAssets } from "../ticket/ticketAssets";
import { EmailProvider, ProviderError, WhatsAppProvider } from "./providers";
import { EventFacts, registrationConfirmationEmail, ticketReplacementEmail } from "./templates";

export type Channel = "EMAIL" | "WHATSAPP";
export type JobKind = "REGISTRATION_CONFIRMATION" | "TICKET_REPLACEMENT";
export type JobStatus = "PENDING" | "PROCESSING" | "SENT" | "DELIVERED" | "FAILED" | "RETRYING";

export interface OutboxDeps {
  email: EmailProvider;
  whatsapp: WhatsAppProvider;
  now?: () => number;
}

export async function loadClubName(db: Firestore): Promise<string> {
  const snap = await db.collection(C.settings).doc(CLUB_SETTINGS_DOC).get();
  return (snap.data()?.clubName as string | undefined)?.trim() || "Scientific Club";
}

export function eventFacts(clubName: string, event: DocumentData, registration: DocumentData): EventFacts {
  const zone = event.timezone ?? "UTC";
  const start = (event.startAt as Timestamp).toMillis();
  const end = (event.endAt as Timestamp).toMillis();
  return {
    clubName,
    participantName: registration.participantName,
    eventTitle: event.title,
    dateText: formatEventDate(start, zone),
    timeText: formatEventTime(start, end, zone),
    location: event.location,
  };
}

/**
 * Delivers one job. Safe to call any number of times and from concurrent workers: the job is
 * claimed in a transaction (PENDING/RETRYING, or PROCESSING with an expired lease), and a job
 * that is already SENT, DELIVERED or FAILED is left alone.
 */
export async function processNotificationJob(db: Firestore, jobId: string, deps: OutboxDeps): Promise<JobStatus | "SKIPPED"> {
  const now = deps.now ?? Date.now;
  const ref = db.collection(C.notificationJobs).doc(jobId);

  const job = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.data();
    if (!data) return null;
    const leaseExpired = data.status === "PROCESSING" && (data.leaseUntil as Timestamp | null)?.toMillis()! < now();
    if (data.status !== "PENDING" && data.status !== "RETRYING" && !leaseExpired) return null;
    const attemptCount = (data.attemptCount ?? 0) + 1;
    tx.update(ref, { status: "PROCESSING", attemptCount, leaseUntil: Timestamp.fromMillis(now() + NOTIFICATION_LEASE_MS), updatedAt: Timestamp.fromMillis(now()) });
    return { ...data, attemptCount } as DocumentData;
  });
  if (!job) return "SKIPPED";

  const finish = async (fields: DocumentData): Promise<JobStatus> => {
    await ref.update({ ...fields, leaseUntil: null, updatedAt: Timestamp.fromMillis(now()) });
    return fields.status as JobStatus;
  };

  try {
    const [regSnap, eventSnap] = await Promise.all([
      db.collection(C.registrations).doc(job.registrationId).get(),
      db.collection(C.events).doc(job.eventId).get(),
    ]);
    const reg = regSnap.data();
    const event = eventSnap.data();
    if (!reg || !event) throw new ProviderError("The registration or event no longer exists.", false);
    if (reg.status === "CANCELLED") throw new ProviderError("The registration was cancelled.", false);
    if (reg.ticketVersion !== job.ticketVersion) throw new ProviderError("Superseded by a newer ticket.", false);

    const facts = eventFacts(await loadClubName(db), event, reg);
    if (typeof reg.ticketSecret !== "string") throw new ProviderError("The registration has no ticket secret.", false);
    const payload = ticketCode(job.registrationId, job.ticketVersion, reg.ticketSecret);
    const assets = await renderTicketAssets(facts, payload);

    let providerName: string;
    let providerMessageId: string;
    if (job.channel === "EMAIL") {
      providerName = deps.email.name;
      const content = job.kind === "TICKET_REPLACEMENT" ? ticketReplacementEmail(facts) : registrationConfirmationEmail(facts);
      ({ providerMessageId } = await deps.email.send({
        to: job.recipient, content, idempotencyKey: jobId,
        attachments: [
          { filename: "qr.png", content: assets.qrPng, contentType: "image/png", contentId: "ticket-qr" },
          { filename: "ticket.png", content: assets.ticketPng, contentType: "image/png" },
          { filename: "ticket.pdf", content: assets.ticketPdf, contentType: "application/pdf" },
        ],
      }));
    } else {
      providerName = deps.whatsapp.name;
      ({ providerMessageId } = await deps.whatsapp.sendTicket({ to: job.recipient, facts, ticketPng: assets.ticketPng }));
    }
    return await finish({ status: "SENT", provider: providerName, providerMessageId, lastError: null, sentAt: Timestamp.fromMillis(now()) });
  } catch (error) {
    const retryable = error instanceof ProviderError ? error.retryable : true;
    const message = ((error as Error).message || "Unknown error").slice(0, 500);
    logger.warn("Notification attempt failed", { jobId, channel: job.channel, attempt: job.attemptCount, retryable, message });
    const provider = job.channel === "EMAIL" ? deps.email.name : deps.whatsapp.name;
    if (retryable && job.attemptCount < NOTIFICATION_MAX_ATTEMPTS) {
      return finish({ status: "RETRYING", provider, lastError: message, nextAttemptAt: Timestamp.fromMillis(now() + retryDelayMs(job.attemptCount)) });
    }
    return finish({ status: "FAILED", provider, lastError: message });
  }
}

/**
 * Picks up jobs whose retry time has come (and PROCESSING jobs whose worker died). `channels`
 * limits the run to the channels that are really configured: a channel without a provider
 * keeps its jobs PENDING instead of pretending they were sent.
 */
export async function processDueJobs(db: Firestore, deps: OutboxDeps, limit = 50, channels: Channel[] = ["EMAIL", "WHATSAPP"]): Promise<number> {
  const now = Timestamp.fromMillis((deps.now ?? Date.now)());
  const jobs = db.collection(C.notificationJobs);
  const [due, stuck] = await Promise.all([
    jobs.where("status", "in", ["PENDING", "RETRYING"]).where("nextAttemptAt", "<=", now).limit(limit).get(),
    jobs.where("status", "==", "PROCESSING").where("leaseUntil", "<=", now).limit(limit).get(),
  ]);
  const ids = [...due.docs, ...stuck.docs].filter((d) => channels.includes(d.data().channel)).map((d) => d.id);
  for (const id of ids) await processNotificationJob(db, id, deps);
  return ids.length;
}

/** Provider webhooks report the final delivery state for a message id. */
export async function recordDeliveryStatus(db: Firestore, providerMessageId: string, outcome: "DELIVERED" | "FAILED", detail?: string): Promise<boolean> {
  const snap = await db.collection(C.notificationJobs).where("providerMessageId", "==", providerMessageId).limit(1).get();
  const doc = snap.docs[0];
  if (!doc) return false;
  const now = Timestamp.now();
  if (outcome === "DELIVERED") await doc.ref.update({ status: "DELIVERED", deliveredAt: now, updatedAt: now });
  else if (doc.data().status !== "DELIVERED") await doc.ref.update({ status: "FAILED", lastError: (detail ?? "Delivery failed").slice(0, 500), updatedAt: now });
  return true;
}
