/**
 * Demo data in exactly the shape the app writes (shared/.../data/repository/*Impl.kt):
 * settings, events, organizer assignments, registrations with their keys and outbox jobs,
 * attendance sessions and audit entries. Used by the emulator seed and by init-project.
 */
import { DocumentData, Firestore, Timestamp } from "firebase-admin/firestore";
import { C, CLUB_SETTINGS_DOC } from "../src/shared/firebase";
import { newTicketSecret, registrationKeyId } from "../src/shared/ticketCode";

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

export interface Staff {
  uid: string;
  name: string;
}

function audit(db: Firestore, actor: Staff, action: string, entityType: string, entityId: string, eventId: string | null, details = ""): DocumentData {
  return {
    actorId: actor.uid, actorName: actor.name, action, entityType, entityId, eventId,
    reason: null, previousValue: null, newValue: null, details, createdAt: Timestamp.now(),
  };
}

function slug(title: string, id: string): string {
  return `${title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60)}-${id.slice(0, 6).toLowerCase()}`;
}

export async function writeSettings(db: Firestore, admin: Staff): Promise<void> {
  await db.collection(C.settings).doc(CLUB_SETTINGS_DOC).set({
    clubName: "Scientific Club", tagline: "Curiosity, shared.", attendanceGraceMinutes: 10,
    about: "A student-run club that organises workshops, talks and competitions across the sciences. (Placeholder text, replace it in Settings.)",
    contactEmail: "contact@example.org", contactPhone: "", address: "University campus",
    websiteUrl: "", instagramUrl: "", facebookUrl: "", linkedinUrl: "", updatedAt: Timestamp.now(),
  }, { merge: true });
  await db.collection(C.auditLogs).add(audit(db, admin, "CLUB_SETTINGS_UPDATED", "settings", CLUB_SETTINGS_DOC, null));
}

/** Three published events (one happening now), the organizer assigned to each. Returns their ids. */
export async function writeEvents(db: Firestore, admin: Staff, organizer: Staff & { email: string }): Promise<string[]> {
  const now = Date.now();
  const base = { timezone: "Africa/Algiers", organizerName: "Scientific Club", coverImageUrl: "", allowReentry: false, featured: false };
  const events = [
    { ...base, title: "AI & Robotics Workshop", category: "Workshop", location: "Innovation Lab, Building C", capacity: 60, featured: true,
      description: "A hands-on afternoon building a line-following robot and training a small vision model to steer it. Bring a laptop; kits are provided.",
      startAt: now - HOUR, endAt: now + 4 * HOUR, registrationOpenAt: now - 7 * DAY, registrationCloseAt: now + 3 * HOUR },
    { ...base, title: "Astronomy Night: Reading the Winter Sky", category: "Talk", location: "Main Amphitheatre", capacity: 150, featured: true,
      description: "A guided tour of the winter constellations followed by telescope observation on the roof terrace, weather permitting.",
      startAt: now + 6 * DAY, endAt: now + 6 * DAY + 3 * HOUR, registrationOpenAt: now - DAY, registrationCloseAt: now + 5 * DAY },
    { ...base, title: "Inter-University Physics Olympiad", category: "Competition", location: "Faculty of Physics, Hall 2", capacity: 30, allowReentry: true,
      description: "Teams of three solve experimental and theoretical problems over a full day.",
      startAt: now + 14 * DAY, endAt: now + 14 * DAY + 8 * HOUR, registrationOpenAt: now - DAY, registrationCloseAt: now + 12 * DAY },
  ];
  const ids: string[] = [];
  for (const event of events) {
    const ref = db.collection(C.events).doc();
    const at = Timestamp.now();
    const stored = {
      ...event, id: ref.id, slug: slug(event.title, ref.id), status: "PUBLISHED",
      startAt: Timestamp.fromMillis(event.startAt), endAt: Timestamp.fromMillis(event.endAt),
      registrationOpenAt: Timestamp.fromMillis(event.registrationOpenAt), registrationCloseAt: Timestamp.fromMillis(event.registrationCloseAt),
      registrationCount: 0, attendeeCount: 0, activeSessionCount: 0, completedSessionCount: 0, completedDurationSeconds: 0,
      lastRegistrationId: null, createdBy: admin.uid, createdAt: at, publishedAt: at, updatedAt: at,
    };
    await ref.set(stored);
    await db.collection(C.eventOrganizers).doc(`${ref.id}_${organizer.uid}`).set({
      eventId: ref.id, organizerId: organizer.uid, organizerName: organizer.name, organizerEmail: organizer.email,
      assignedBy: admin.uid, assignedAt: at, active: true,
      eventTitle: stored.title, eventLocation: stored.location, eventStartAt: stored.startAt, eventEndAt: stored.endAt,
      eventTimezone: stored.timezone, eventStatus: stored.status,
    });
    await db.collection(C.auditLogs).add(audit(db, admin, "EVENT_CREATED", "event", ref.id, ref.id, `title: ${event.title}`));
    ids.push(ref.id);
  }
  return ids;
}

export interface Person {
  fullName: string;
  email: string;
  phone: string;
  deliveryPreference: "EMAIL_ONLY" | "WHATSAPP_ONLY" | "BOTH";
  whatsappConsent?: boolean;
}

/** A public registration: the registration, its key, the seat and the outbox jobs, in one transaction. */
export async function writeRegistration(db: Firestore, eventId: string, person: Person): Promise<string> {
  const eventRef = db.collection(C.events).doc(eventId);
  const regRef = db.collection(C.registrations).doc();
  const normalizedEmail = person.email.trim().toLowerCase();
  await db.runTransaction(async (tx) => {
    const event = (await tx.get(eventRef)).data()!;
    const at = Timestamp.now();
    const consent = person.whatsappConsent === true;
    tx.set(regRef, {
      id: regRef.id, eventId, participantId: null, participantName: person.fullName, nameLower: person.fullName.toLowerCase(),
      email: person.email, normalizedEmail, phone: person.phone,
      university: "University of Science and Technology", faculty: "Computer Science", department: "Artificial Intelligence", studentId: null,
      status: "REGISTERED", ticketVersion: 1, ticketSecret: newTicketSecret(), registeredAt: at, updatedAt: at,
      deliveryPreference: person.deliveryPreference, whatsappConsent: consent, source: "PUBLIC", registeredBy: null,
      attendanceStatus: "NONE", activeSessionId: null, sessionCount: 0, totalDurationSeconds: 0, firstCheckInAt: null, lastCheckOutAt: null,
    });
    tx.set(db.collection(C.registrationKeys).doc(registrationKeyId(eventId, normalizedEmail)), { registrationId: regRef.id, eventId, createdAt: at });
    tx.update(eventRef, { registrationCount: (event.registrationCount ?? 0) + 1, lastRegistrationId: regRef.id, updatedAt: at });
    const channels: Array<[string, string]> = [];
    if (person.deliveryPreference !== "WHATSAPP_ONLY") channels.push(["EMAIL", person.email]);
    if (consent && person.deliveryPreference !== "EMAIL_ONLY") channels.push(["WHATSAPP", person.phone]);
    for (const [channel, recipient] of channels) {
      const id = `${regRef.id}_v1_${channel}_REGISTRATION_CONFIRMATION`;
      tx.set(db.collection(C.notificationJobs).doc(id), {
        id, registrationId: regRef.id, eventId, eventTitle: event.title, participantName: person.fullName, channel,
        kind: "REGISTRATION_CONFIRMATION", recipient, ticketVersion: 1, status: "PENDING", provider: null, providerMessageId: null,
        attemptCount: 0, lastError: null, nextAttemptAt: at, leaseUntil: null, createdAt: at, updatedAt: at, sentAt: null, deliveredAt: null,
      });
    }
  });
  return regRef.id;
}

/** Check-in (and optionally check-out `minutes` later), as the scanner writes it. */
export async function writeAttendance(db: Firestore, organizer: Staff, eventId: string, registrationId: string, checkOutAfterMinutes?: number): Promise<void> {
  const regRef = db.collection(C.registrations).doc(registrationId);
  const eventRef = db.collection(C.events).doc(eventId);
  const sessionRef = db.collection(C.attendanceSessions).doc();
  await db.runTransaction(async (tx) => {
    const [reg, event] = [(await tx.get(regRef)).data()!, (await tx.get(eventRef)).data()!];
    const nowMs = Date.now();
    const checkInMs = checkOutAfterMinutes ? nowMs - checkOutAfterMinutes * 60 * 1000 : nowMs;
    const checkIn = Timestamp.fromMillis(checkInMs);
    const done = checkOutAfterMinutes !== undefined;
    const duration = done ? Math.floor((nowMs - checkInMs) / 1000) : null;
    const at = Timestamp.fromMillis(nowMs);
    tx.set(sessionRef, {
      id: sessionRef.id, eventId, registrationId, participantId: null, participantName: reg.participantName,
      faculty: reg.faculty ?? "", department: reg.department ?? "",
      checkInAt: checkIn, checkOutAt: done ? at : null, checkedInBy: organizer.uid, checkedInByName: organizer.name,
      checkedOutBy: done ? organizer.uid : null, checkedOutByName: done ? organizer.name : null,
      durationSeconds: duration, status: done ? "COMPLETED" : "ACTIVE", corrected: false, method: "QR", createdAt: checkIn, updatedAt: at,
    });
    tx.update(regRef, {
      attendanceStatus: done ? "COMPLETED" : "ACTIVE", activeSessionId: done ? null : sessionRef.id, sessionCount: (reg.sessionCount ?? 0) + 1,
      firstCheckInAt: reg.firstCheckInAt ?? checkIn, totalDurationSeconds: (reg.totalDurationSeconds ?? 0) + (duration ?? 0),
      lastCheckOutAt: done ? at : reg.lastCheckOutAt ?? null, updatedAt: at,
    });
    tx.update(eventRef, {
      attendeeCount: (event.attendeeCount ?? 0) + ((reg.sessionCount ?? 0) === 0 ? 1 : 0),
      activeSessionCount: (event.activeSessionCount ?? 0) + (done ? 0 : 1),
      completedSessionCount: (event.completedSessionCount ?? 0) + (done ? 1 : 0),
      completedDurationSeconds: (event.completedDurationSeconds ?? 0) + (duration ?? 0), updatedAt: at,
    });
    tx.set(db.collection(C.auditLogs).doc(), audit(db, organizer, "CHECK_IN", "attendanceSession", sessionRef.id, eventId, `participantName: ${reg.participantName}`));
  });
}

export const DEMO_PEOPLE: Person[] = [
  { fullName: "Yasmine Benali", email: "yasmine.benali@example.com", phone: "+213555000101", deliveryPreference: "EMAIL_ONLY" },
  { fullName: "Karim Haddad", email: "karim.haddad@example.com", phone: "+213555000102", deliveryPreference: "BOTH", whatsappConsent: true },
  { fullName: "Lina Meziane", email: "lina.meziane@example.com", phone: "+213555000103", deliveryPreference: "EMAIL_ONLY" },
  { fullName: "Samir Toumi", email: "samir.toumi@example.com", phone: "+213555000104", deliveryPreference: "EMAIL_ONLY" },
];

/** Everything at once: events, six registrations, three check-ins (one already checked out after 95 minutes). */
export async function writeDemoData(db: Firestore, admin: Staff, organizer: Staff & { email: string }): Promise<void> {
  await writeSettings(db, admin);
  if (!(await db.collection(C.events).limit(1).get()).empty) {
    console.log("Events already exist, leaving them and the demo participants alone.");
    return;
  }
  const [live, upcoming] = await writeEvents(db, admin, organizer);
  const registrations: string[] = [];
  for (const person of DEMO_PEOPLE) registrations.push(await writeRegistration(db, live, person));
  for (const person of DEMO_PEOPLE.slice(0, 2)) await writeRegistration(db, upcoming, person);
  await writeAttendance(db, organizer, live, registrations[0], 95);
  await writeAttendance(db, organizer, live, registrations[1]);
  await writeAttendance(db, organizer, live, registrations[2]);
  console.log("events: 3, registrations: 6, attendance: 3 check-ins (1 checked out)");
}
