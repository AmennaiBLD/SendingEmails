import { Timestamp } from "firebase-admin/firestore";
import { Actor } from "../src/shared/auth";
import { C, db } from "../src/shared/firebase";
import { EmailProvider, ProviderError, SendResult, WhatsAppProvider } from "../src/notifications/providers";

export const HOUR = 60 * 60 * 1000;
let counter = 0;

export function uid(prefix: string): string {
  return `${prefix}${Date.now().toString(36)}${(counter++).toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

export async function makeEvent(overrides: Record<string, unknown> = {}): Promise<string> {
  const ref = db.collection(C.events).doc();
  const now = Date.now();
  await ref.set({
    id: ref.id, title: "AI & Robotics Workshop", slug: "ai", description: "d", coverImageUrl: "", category: "Workshop",
    location: "Hall A", organizerName: "Club", timezone: "Africa/Algiers",
    startAt: Timestamp.fromMillis(now - HOUR), endAt: Timestamp.fromMillis(now + 3 * HOUR),
    registrationOpenAt: Timestamp.fromMillis(now - 24 * HOUR), registrationCloseAt: Timestamp.fromMillis(now + 2 * HOUR),
    capacity: 0, registrationCount: 0, attendeeCount: 0, activeSessionCount: 0, completedSessionCount: 0, completedDurationSeconds: 0,
    allowReentry: false, featured: false, status: "PUBLISHED", createdBy: "seed", createdAt: Timestamp.now(), updatedAt: Timestamp.now(), publishedAt: Timestamp.now(),
    ...overrides,
  });
  return ref.id;
}

export function form(eventId: string, email = `${uid("p")}@example.org`, extra: Record<string, unknown> = {}) {
  return {
    eventId, fullName: "Ahmed Benali", email, phone: "+213555123456", university: "USTHB", faculty: "Computer Science",
    department: "AI", studentId: "", deliveryPreference: "EMAIL_ONLY", whatsappConsent: false, privacyAccepted: true, ...extra,
  };
}

export async function makeStaff(role: "ADMIN" | "ORGANIZER", eventId?: string, active = true): Promise<Actor> {
  const id = uid("u");
  await db.collection(C.users).doc(id).set({ id, fullName: `Staff ${id}`, email: `${id}@example.org`, role, isActive: true });
  if (eventId) await db.collection(C.eventOrganizers).doc(`${eventId}_${id}`).set({ eventId, organizerId: id, active });
  return { uid: id, role, name: `Staff ${id}`, email: `${id}@example.org` };
}

/** Test doubles for the providers: count calls and fail on demand. */
export class FakeEmail implements EmailProvider {
  readonly name = "fake-email";
  calls = 0;
  failures: ProviderError[] = [];
  async send(): Promise<SendResult> {
    this.calls++;
    const failure = this.failures.shift();
    if (failure) throw failure;
    return { providerMessageId: `e-${this.calls}` };
  }
}

export class FakeWhatsApp implements WhatsAppProvider {
  readonly name = "fake-wa";
  calls = 0;
  images: number[] = [];
  async sendTicket(input: { ticketPng: Buffer }): Promise<SendResult> {
    this.calls++;
    this.images.push(input.ticketPng.length);
    return { providerMessageId: `w-${this.calls}` };
  }
}
