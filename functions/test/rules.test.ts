import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, before, beforeEach, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, runTransaction, setDoc, setLogLevel, Timestamp, updateDoc, where, writeBatch,
} from "firebase/firestore";

// The apps write to Firestore directly, so these rules are the backend. Each test performs the
// same writes the app's repositories make (shared/.../data/repository/*Impl.kt).

let env: RulesTestEnvironment;
// The test contexts hand out the compat Firestore type; the modular functions accept it at runtime.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Db = any;
const HOUR = 60 * 60 * 1000;
const SECRET = "a".repeat(64);

before(async () => {
  setLogLevel("silent"); // denied requests are the point of these tests
  const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080").split(":");
  env = await initializeTestEnvironment({
    projectId: "demo-scientific-club-rules",
    firestore: { rules: readFileSync("../firestore.rules", "utf8"), host, port: Number(port) },
  });
});

after(async () => {
  await env.cleanup();
});

function ts(offsetMs = 0): Timestamp {
  return Timestamp.fromMillis(Date.now() + offsetMs);
}

function event(overrides: Record<string, unknown> = {}) {
  return {
    id: "pub", title: "AI Workshop", description: "Hands-on.", coverImageUrl: "", category: "Workshop", location: "Hall A",
    organizerName: "Club", timezone: "Africa/Algiers", startAt: ts(-HOUR), endAt: ts(3 * HOUR),
    registrationOpenAt: ts(-24 * HOUR), registrationCloseAt: ts(2 * HOUR), capacity: 0, registrationCount: 0, attendeeCount: 0,
    activeSessionCount: 0, completedSessionCount: 0, completedDurationSeconds: 0, allowReentry: false, featured: false,
    status: "PUBLISHED", createdBy: "admin", createdAt: ts(-48 * HOUR), updatedAt: ts(-48 * HOUR), publishedAt: ts(-48 * HOUR),
    lastRegistrationId: null, ...overrides,
  };
}

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users/admin"), { id: "admin", fullName: "Amina", role: "ADMIN", isActive: true });
    await setDoc(doc(db, "users/org1"), { id: "org1", fullName: "Omar", role: "ORGANIZER", isActive: true });
    await setDoc(doc(db, "users/org2"), { id: "org2", fullName: "Other", role: "ORGANIZER", isActive: true });
    await setDoc(doc(db, "users/orgOff"), { id: "orgOff", fullName: "Off", role: "ORGANIZER", isActive: false });
    await setDoc(doc(db, "events/pub"), event());
    await setDoc(doc(db, "events/full"), event({ id: "full", capacity: 1, registrationCount: 1 }));
    await setDoc(doc(db, "events/closed"), event({ id: "closed", registrationCloseAt: ts(-HOUR + 1000), registrationOpenAt: ts(-24 * HOUR) }));
    await setDoc(doc(db, "events/draft"), event({ id: "draft", status: "DRAFT" }));
    await setDoc(doc(db, "eventOrganizers/pub_org1"), { eventId: "pub", organizerId: "org1", active: true });
    await setDoc(doc(db, "eventOrganizers/pub_org2"), { eventId: "pub", organizerId: "org2", active: false });
    await setDoc(doc(db, "eventOrganizers/pub_orgOff"), { eventId: "pub", organizerId: "orgOff", active: true });
    await setDoc(doc(db, "registrations/r1"), registration("r1", "pub", "taken@example.org"));
    await setDoc(doc(db, "registrationKeys/pub_taken@example.org"), { registrationId: "r1", eventId: "pub", createdAt: ts(-HOUR) });
    await setDoc(doc(db, "settings/club"), { clubName: "Scientific Club" });
    await setDoc(doc(db, "ticketAccessRequests/t1"), { codeHash: "x" });
  });
});

function registration(id: string, eventId: string, email: string, overrides: Record<string, unknown> = {}) {
  const at = ts();
  return {
    id, eventId, participantId: null, participantName: "Ahmed Benali", nameLower: "ahmed benali", email, normalizedEmail: email,
    phone: "+213555123456", university: "USTHB", faculty: "CS", department: "AI", studentId: null, status: "REGISTERED",
    ticketVersion: 1, ticketSecret: SECRET, registeredAt: at, updatedAt: at, deliveryPreference: "EMAIL_ONLY", whatsappConsent: false,
    source: "PUBLIC", registeredBy: null, attendanceStatus: "NONE", activeSessionId: null, sessionCount: 0, totalDurationSeconds: 0,
    firstCheckInAt: null, lastCheckOutAt: null, ...overrides,
  };
}

/** The app's public registration transaction (RegistrationRepositoryImpl.register). */
async function register(db: Db, eventId: string, email: string, options: { id?: string; reg?: Record<string, unknown>; count?: number; job?: Record<string, unknown> } = {}) {
  const id = options.id ?? `reg${Math.random().toString(36).slice(2, 12)}`;
  await runTransaction(db, async (tx) => {
    const eventSnap = await tx.get(doc(db, `events/${eventId}`));
    const count = options.count ?? (eventSnap.data()?.registrationCount ?? 0) + 1;
    const at = ts();
    tx.set(doc(db, `registrations/${id}`), registration(id, eventId, email, options.reg));
    tx.set(doc(db, `registrationKeys/${eventId}_${email}`), { registrationId: id, eventId, createdAt: at });
    tx.update(doc(db, `events/${eventId}`), { registrationCount: count, lastRegistrationId: id, updatedAt: at });
    const jobId = `${id}_v1_EMAIL_REGISTRATION_CONFIRMATION`;
    tx.set(doc(db, `notificationJobs/${jobId}`), {
      id: jobId, registrationId: id, eventId, eventTitle: "AI Workshop", participantName: "Ahmed Benali", channel: "EMAIL",
      kind: "REGISTRATION_CONFIRMATION", recipient: email, ticketVersion: 1, status: "PENDING", provider: null, providerMessageId: null,
      attemptCount: 0, lastError: null, nextAttemptAt: at, leaseUntil: null, createdAt: at, updatedAt: at, sentAt: null, deliveredAt: null,
      ...options.job,
    });
  });
  return id;
}

/** The app's check-in transaction (AttendanceRepositoryImpl.scan, CHECK_IN). */
async function checkIn(db: Db, uid: string, registrationId: string, eventId = "pub") {
  const sessionId = `s${Math.random().toString(36).slice(2, 12)}`;
  const at = ts();
  const batch = writeBatch(db);
  batch.set(doc(db, `attendanceSessions/${sessionId}`), {
    id: sessionId, eventId, registrationId, participantId: null, participantName: "Ahmed Benali", faculty: "CS", department: "AI",
    checkInAt: at, checkOutAt: null, checkedInBy: uid, checkedInByName: "Omar", checkedOutBy: null, checkedOutByName: null,
    durationSeconds: null, status: "ACTIVE", corrected: false, method: "QR", createdAt: at, updatedAt: at,
  });
  batch.update(doc(db, `registrations/${registrationId}`), { attendanceStatus: "ACTIVE", activeSessionId: sessionId, sessionCount: 1, firstCheckInAt: at, updatedAt: at });
  batch.update(doc(db, `events/${eventId}`), { attendeeCount: 1, activeSessionCount: 1, updatedAt: at });
  batch.set(doc(db, `auditLogs/${sessionId}`), {
    actorId: uid, actorName: "Omar", action: "CHECK_IN", entityType: "attendanceSession", entityId: sessionId, eventId,
    reason: null, previousValue: null, newValue: null, details: "", createdAt: at,
  });
  await batch.commit();
  return sessionId;
}

const visitor = (): Db => env.unauthenticatedContext().firestore();
const as = (uid: string): Db => env.authenticatedContext(uid).firestore();

// ── Public registration ──────────────────────────────────────────────────────────────────

test("a visitor registers: registration, key, seat and email job in one transaction", async () => {
  const id = await assertSucceeds(register(visitor(), "pub", "new@example.org"));
  await env.withSecurityRulesDisabled(async (ctx) => {
    const reg = await getDoc(doc(ctx.firestore(), `registrations/${id}`));
    assert.equal(reg.data()!.status, "REGISTERED");
    assert.equal((await getDoc(doc(ctx.firestore(), "events/pub"))).data()!.registrationCount, 1);
  });
});

test("the same email cannot register twice for an event", async () => {
  await assertFails(register(visitor(), "pub", "taken@example.org"));
});

test("a full event, a closed window or a draft event refuses registrations", async () => {
  await assertFails(register(visitor(), "full", "a@example.org"));
  await assertFails(register(visitor(), "closed", "b@example.org"));
  await assertFails(register(visitor(), "draft", "c@example.org"));
});

test("a visitor cannot forge a registration: counter, status, admin source or attendance", async () => {
  await assertFails(register(visitor(), "pub", "d@example.org", { count: 5 }));
  await assertFails(register(visitor(), "pub", "e@example.org", { reg: { status: "CANCELLED" } }));
  await assertFails(register(visitor(), "pub", "f@example.org", { reg: { source: "ADMIN", registeredBy: "admin" } }));
  await assertFails(register(visitor(), "pub", "g@example.org", { reg: { attendanceStatus: "COMPLETED", sessionCount: 1 } }));
  await assertFails(register(visitor(), "pub", "h@example.org", { reg: { registeredAt: ts(-2 * HOUR), updatedAt: ts(-2 * HOUR) } }));
  await assertFails(register(visitor(), "pub", "i@example.org", { reg: { ticketSecret: "short" } }));
});

test("a visitor cannot send email to someone else through the outbox", async () => {
  await assertFails(register(visitor(), "pub", "j@example.org", { job: { recipient: "victim@example.org" } }));
  const db = visitor();
  const jobId = "r1_v1_EMAIL_REGISTRATION_CONFIRMATION";
  await assertFails(setDoc(doc(db, `notificationJobs/${jobId}`), { id: jobId, registrationId: "r1", status: "PENDING" }));
});

test("a visitor cannot take a seat without a new registration, or read personal data", async () => {
  const db = visitor();
  await assertFails(updateDoc(doc(db, "events/pub"), { registrationCount: 1, lastRegistrationId: "r1", updatedAt: ts() }));
  await assertFails(getDoc(doc(db, "registrations/r1")));
  await assertFails(getDocs(collection(db, "registrationKeys")));
  await assertSucceeds(getDoc(doc(db, "registrationKeys/pub_taken@example.org")));
  await assertFails(getDoc(doc(db, "ticketAccessRequests/t1")));
  await assertSucceeds(getDoc(doc(db, "events/pub")));
  await assertFails(getDoc(doc(db, "events/draft")));
});

// ── Attendance ───────────────────────────────────────────────────────────────────────────

test("an assigned organizer checks a participant in", async () => {
  await assertSucceeds(checkIn(as("org1"), "org1", "r1"));
});

test("an unassigned, revoked or deactivated organizer cannot scan or read the event's people", async () => {
  await assertFails(checkIn(as("org2"), "org2", "r1"));
  await assertFails(checkIn(as("orgOff"), "orgOff", "r1"));
  await assertFails(getDoc(doc(as("org2"), "registrations/r1")));
  await assertFails(getDocs(query(collection(as("org2"), "registrations"), where("eventId", "==", "pub"))));
  await assertSucceeds(getDocs(query(collection(as("org1"), "registrations"), where("eventId", "==", "pub"))));
});

test("check-in must use the server's time and the organizer's own name", async () => {
  const db = as("org1");
  const batch = writeBatch(db);
  batch.set(doc(db, "attendanceSessions/fake"), {
    id: "fake", eventId: "pub", registrationId: "r1", status: "ACTIVE", checkOutAt: null, durationSeconds: null, corrected: false,
    checkedInBy: "org2", checkInAt: ts(),
  });
  batch.update(doc(db, "registrations/r1"), { activeSessionId: "fake", attendanceStatus: "ACTIVE", sessionCount: 1, updatedAt: ts() });
  await assertFails(batch.commit());
  const late = writeBatch(db);
  late.set(doc(db, "attendanceSessions/old"), {
    id: "old", eventId: "pub", registrationId: "r1", status: "ACTIVE", checkOutAt: null, durationSeconds: null, corrected: false,
    checkedInBy: "org1", checkInAt: ts(-2 * HOUR),
  });
  late.update(doc(db, "registrations/r1"), { activeSessionId: "old", attendanceStatus: "ACTIVE", sessionCount: 1, updatedAt: ts() });
  await assertFails(late.commit());
});

test("organizers change only attendance fields; nobody deletes attendance", async () => {
  const sessionId = await checkIn(as("org1"), "org1", "r1");
  const db = as("org1");
  await assertFails(updateDoc(doc(db, "registrations/r1"), { status: "CANCELLED", updatedAt: ts() }));
  await assertFails(updateDoc(doc(db, "events/pub"), { capacity: 999, updatedAt: ts() }));
  await assertFails(deleteDoc(doc(db, `attendanceSessions/${sessionId}`)));
  await assertSucceeds(updateDoc(doc(db, `attendanceSessions/${sessionId}`), {
    checkOutAt: ts(), checkedOutBy: "org1", checkedOutByName: "Omar", durationSeconds: 60, status: "COMPLETED", updatedAt: ts(),
  }));
});

// ── Administration ───────────────────────────────────────────────────────────────────────

test("only an active administrator creates events, staff and assignments, or edits settings", async () => {
  const newEvent = event({ id: "e2", status: "DRAFT", createdBy: "admin", createdAt: ts(), updatedAt: ts(), publishedAt: null });
  await assertSucceeds(setDoc(doc(as("admin"), "events/e2"), newEvent));
  await assertFails(setDoc(doc(as("org1"), "events/e3"), { ...newEvent, id: "e3", createdBy: "org1" }));
  await assertSucceeds(setDoc(doc(as("admin"), "eventOrganizers/pub_org2"), { eventId: "pub", organizerId: "org2", active: true }));
  await assertFails(setDoc(doc(as("org1"), "eventOrganizers/pub_org1"), { eventId: "pub", organizerId: "org1", active: true }));
  await assertSucceeds(setDoc(doc(as("admin"), "settings/club"), { clubName: "New name" }, { merge: true }));
  await assertFails(setDoc(doc(as("org1"), "settings/club"), { clubName: "Hacked" }, { merge: true }));
  const staff = { id: "u9", fullName: "New Staff", email: "u9@example.org", phone: "", profilePhotoUrl: null, role: "ORGANIZER", isActive: true, createdAt: ts(), updatedAt: ts(), createdBy: "admin" };
  await assertSucceeds(setDoc(doc(as("admin"), "users/u9"), staff));
  await assertFails(setDoc(doc(as("org1"), "users/u10"), { ...staff, id: "u10", createdBy: "org1" }));
});

test("events keep valid dates, an allowed status change and enough capacity", async () => {
  const db = as("admin");
  await assertFails(updateDoc(doc(db, "events/pub"), { endAt: ts(-2 * HOUR), updatedAt: ts() }));
  await assertFails(updateDoc(doc(db, "events/pub"), { status: "ARCHIVED", updatedAt: ts() }));
  await assertSucceeds(updateDoc(doc(db, "events/pub"), { status: "COMPLETED", updatedAt: ts() }));
  await assertFails(updateDoc(doc(db, "events/full"), { capacity: 1, registrationCount: 2, updatedAt: ts() }));
});

test("nobody gives themselves a role; organizers cannot change roles", async () => {
  await assertFails(updateDoc(doc(as("org1"), "users/org1"), { role: "ADMIN", isActive: true, updatedAt: ts() }));
  await assertFails(updateDoc(doc(as("admin"), "users/admin"), { role: "ORGANIZER", isActive: true, updatedAt: ts() }));
  await assertSucceeds(updateDoc(doc(as("admin"), "users/org1"), { role: "ADMIN", isActive: true, updatedAt: ts() }));
  await assertFails(setDoc(doc(as("stranger"), "users/stranger"), { id: "stranger", role: "ADMIN", isActive: true }));
});

test("the audit trail is append-only, written in your own name, readable by administrators", async () => {
  const entry = { actorId: "org1", actorName: "Omar", action: "CHECK_IN", entityType: "x", entityId: "y", eventId: "pub", reason: null, previousValue: null, newValue: null, details: "", createdAt: ts() };
  await assertSucceeds(setDoc(doc(as("org1"), "auditLogs/a1"), entry));
  await assertFails(setDoc(doc(as("org1"), "auditLogs/a2"), { ...entry, actorId: "admin" }));
  await assertFails(setDoc(doc(visitor(), "auditLogs/a3"), entry));
  await assertFails(updateDoc(doc(as("admin"), "auditLogs/a1"), { details: "edited" }));
  await assertFails(deleteDoc(doc(as("admin"), "auditLogs/a1")));
  await assertFails(getDoc(doc(as("org1"), "auditLogs/a1")));
  await assertSucceeds(getDoc(doc(as("admin"), "auditLogs/a1")));
});

test("an administrator registers someone outside the window, but never twice or over capacity", async () => {
  const db = as("admin");
  await assertSucceeds(register(db, "closed", "late@example.org", { reg: { source: "ADMIN", registeredBy: "admin" } }));
  await assertFails(register(db, "full", "over@example.org", { reg: { source: "ADMIN", registeredBy: "admin" } }));
  await assertFails(register(db, "pub", "taken@example.org", { reg: { source: "ADMIN", registeredBy: "admin" } }));
});
