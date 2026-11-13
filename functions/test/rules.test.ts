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
    await setDoc(doc(db, "registrations/pendingApp001"), registration("pendingApp001", "pub", "applicant@example.org", { status: "PENDING", ticketVersion: 0, ticketSecret: null }));
    await setDoc(doc(db, "registrations/pendingApp002"), registration("pendingApp002", "full", "late@example.org", { status: "PENDING", ticketVersion: 0, ticketSecret: null }));
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

/** A visitor registering (RegistrationRepositoryImpl.register, asAdmin = false): PENDING, no QR, no seat, "received" email. */
async function apply(
  db: Db, eventId: string, email: string, reg: Record<string, unknown> = {}, jobOverrides: Record<string, unknown> | null = {},
  options: { seat?: boolean } = {},
) {
  const id = `reg${Math.random().toString(36).slice(2, 12)}`;
  await runTransaction(db, async (tx) => {
    const count = ((await tx.get(doc(db, `events/${eventId}`))).data()?.registrationCount ?? 0) + 1;
    const at = ts();
    tx.set(doc(db, `registrations/${id}`), registration(id, eventId, email, { status: "PENDING", ticketVersion: 0, ticketSecret: null, ...reg }));
    tx.set(doc(db, `registrationKeys/${eventId}_${email}`), { registrationId: id, eventId, createdAt: at });
    if (options.seat === true) tx.update(doc(db, `events/${eventId}`), { registrationCount: count, lastRegistrationId: id, updatedAt: at });
    if (jobOverrides !== null) {
      job(db, tx, id, eventId, email, { kind: "REGISTRATION_RECEIVED", ticketVersion: 0, ...jobOverrides });
    }
  });
  return id;
}

function job(db: Db, tx: Db, id: string, eventId: string, email: string, overrides: Record<string, unknown> = {}) {
  const at = ts();
  const fields = {
    id: "", registrationId: id, eventId, eventTitle: "AI Workshop", participantName: "Ahmed Benali", channel: "EMAIL",
    kind: "REGISTRATION_CONFIRMATION", recipient: email, ticketVersion: 1, status: "PENDING", provider: null, providerMessageId: null,
    attemptCount: 0, lastError: null, nextAttemptAt: at, leaseUntil: null, createdAt: at, updatedAt: at, sentAt: null, deliveredAt: null,
    ...overrides,
  };
  const jobId = `${id}_v${fields.ticketVersion}_${fields.channel}_${fields.kind}`;
  tx.set(doc(db, `notificationJobs/${jobId}`), { ...fields, id: jobId });
}

/** An administrator registering someone directly: approved at once, seat taken, email queued. */
async function adminRegister(db: Db, eventId: string, email: string) {
  const id = `reg${Math.random().toString(36).slice(2, 12)}`;
  await runTransaction(db, async (tx) => {
    const count = ((await tx.get(doc(db, `events/${eventId}`))).data()?.registrationCount ?? 0) + 1;
    const at = ts();
    tx.set(doc(db, `registrations/${id}`), registration(id, eventId, email, { source: "ADMIN", registeredBy: "admin" }));
    tx.set(doc(db, `registrationKeys/${eventId}_${email}`), { registrationId: id, eventId, createdAt: at });
    tx.update(doc(db, `events/${eventId}`), { registrationCount: count, lastRegistrationId: id, updatedAt: at });
    job(db, tx, id, eventId, email);
  });
  return id;
}

/** Approval (RegistrationRepositoryImpl.reviewRegistration): QR secret, seat, email job, audit. */
async function approve(db: Db, uid: string, id: string, eventId: string, email: string, options: { seat?: boolean; secret?: string | null } = {}) {
  await runTransaction(db, async (tx) => {
    const count = ((await tx.get(doc(db, `events/${eventId}`))).data()?.registrationCount ?? 0);
    const at = ts();
    tx.update(doc(db, `registrations/${id}`), {
      status: "REGISTERED", ticketVersion: 1, ticketSecret: options.secret === undefined ? SECRET : options.secret,
      reviewedAt: at, reviewedBy: uid, updatedAt: at,
    });
    if (options.seat !== false) tx.update(doc(db, `events/${eventId}`), { registrationCount: count + 1, updatedAt: at });
    job(db, tx, id, eventId, email, { kind: "REGISTRATION_APPROVED" });
  });
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

test("a visitor registers: PENDING, no QR, no seat, and a 'received' email", async () => {
  const id = await assertSucceeds(apply(visitor(), "pub", "new@example.org"));
  await env.withSecurityRulesDisabled(async (ctx) => {
    const reg = (await getDoc(doc(ctx.firestore(), `registrations/${id}`))).data()!;
    assert.equal(reg.status, "PENDING");
    assert.equal(reg.ticketSecret, null);
    assert.equal((await getDoc(doc(ctx.firestore(), "events/pub"))).data()!.registrationCount ?? 0, 0);
    assert.equal((await getDoc(doc(ctx.firestore(), `notificationJobs/${id}_v0_EMAIL_REGISTRATION_RECEIVED`))).data()!.kind, "REGISTRATION_RECEIVED");
  });
});

test("the same email cannot register twice; a closed window or a draft refuses", async () => {
  await assertFails(apply(visitor(), "pub", "taken@example.org"));
  await assertFails(apply(visitor(), "closed", "b@example.org"));
  await assertFails(apply(visitor(), "draft", "c@example.org"));
});

test("a visitor gets no QR and no seat, and cannot pretend to be approved or an administrator", async () => {
  await assertFails(apply(visitor(), "pub", "d@example.org", {}, {}, { seat: true }));
  await assertFails(apply(visitor(), "pub", "d2@example.org", { status: "REGISTERED", ticketVersion: 1, ticketSecret: SECRET }, null));
  await assertFails(apply(visitor(), "pub", "e@example.org", { ticketSecret: SECRET }));
  await assertFails(apply(visitor(), "pub", "e2@example.org", { ticketVersion: 1 }, { ticketVersion: 1 }));
  await assertFails(apply(visitor(), "pub", "f@example.org", { source: "ADMIN", registeredBy: "admin", status: "REGISTERED", ticketVersion: 1, ticketSecret: SECRET }, null, { seat: true }));
  await assertFails(apply(visitor(), "pub", "g@example.org", { attendanceStatus: "COMPLETED", sessionCount: 1 }));
  await assertFails(apply(visitor(), "pub", "h@example.org", { registeredAt: ts(-2 * HOUR), updatedAt: ts(-2 * HOUR) }));
  await assertFails(updateDoc(doc(visitor(), "events/pub"), { registrationCount: 1, lastRegistrationId: "pendingApp001", updatedAt: ts() }));
  await assertFails(updateDoc(doc(visitor(), "events/pub"), { registrationCount: 5, updatedAt: ts() }));
});

test("a new application queues only its own 'received' email: no QR email, no other address", async () => {
  await assertSucceeds(apply(visitor(), "pub", "mine@example.org"));
  await assertSucceeds(apply(visitor(), "pub", "noemail@example.org", {}, null));
  await assertFails(apply(visitor(), "pub", "other@example.org", {}, { recipient: "victim@example.org" }));
  await assertFails(apply(visitor(), "pub", "other2@example.org", {}, { kind: "REGISTRATION_CONFIRMATION" }));
  await assertFails(apply(visitor(), "pub", "other3@example.org", {}, { kind: "REGISTRATION_APPROVED" }));
  await assertFails(apply(visitor(), "pub", "other4@example.org", { deliveryPreference: "BOTH", whatsappConsent: true }, { channel: "WHATSAPP", recipient: "+213555123456" }));
});

test("nobody but an administrator queues an email for an existing registration, never a QR for a pending one", async () => {
  const db = visitor();
  const batch = writeBatch(db);
  job(db, batch, "pendingApp001", "pub", "applicant@example.org", { kind: "REGISTRATION_RECEIVED", ticketVersion: 0 });
  await assertFails(batch.commit());
  const admin = as("admin");
  const early = writeBatch(admin);
  job(admin, early, "pendingApp001", "pub", "applicant@example.org", { kind: "REGISTRATION_APPROVED", ticketVersion: 0 });
  await assertFails(early.commit());
});

// Approving / rejecting a PENDING registration.
test("an administrator approves: QR secret, one seat and the QR email in one write", async () => {
  await assertSucceeds(approve(as("admin"), "admin", "pendingApp001", "pub", "applicant@example.org"));
  await env.withSecurityRulesDisabled(async (ctx) => {
    assert.equal((await getDoc(doc(ctx.firestore(), "registrations/pendingApp001"))).data()!.status, "REGISTERED");
    assert.equal((await getDoc(doc(ctx.firestore(), "events/pub"))).data()!.registrationCount, 1);
  });
});

test("approval needs an administrator, a QR secret, a seat, and room in the event", async () => {
  await assertFails(approve(as("org1"), "org1", "pendingApp001", "pub", "applicant@example.org"));
  await assertFails(approve(as("admin"), "admin", "pendingApp001", "pub", "applicant@example.org", { secret: null }));
  await assertFails(approve(as("admin"), "admin", "pendingApp001", "pub", "applicant@example.org", { seat: false }));
  await assertFails(approve(as("admin"), "admin", "pendingApp002", "full", "late@example.org"));
});

test("an administrator rejects without sending anything", async () => {
  await assertSucceeds(updateDoc(doc(as("admin"), "registrations/pendingApp001"), { status: "REJECTED", reviewedAt: ts(), reviewedBy: "admin", updatedAt: ts() }));
  await assertFails(updateDoc(doc(as("org1"), "registrations/pendingApp002"), { status: "REJECTED", reviewedAt: ts(), reviewedBy: "org1", updatedAt: ts() }));
});

test("a visitor cannot read personal data or list registration keys", async () => {
  const db = visitor();
  await assertFails(getDoc(doc(db, "registrations/r1")));
  await assertFails(getDocs(collection(db, "registrationKeys")));
  await assertSucceeds(getDoc(doc(db, "registrationKeys/pub_taken@example.org")));
  await assertFails(getDoc(doc(db, "ticketAccessRequests/t1")));
  await assertSucceeds(getDoc(doc(db, "events/pub")));
  await assertFails(getDoc(doc(db, "events/draft")));
});

// ── Attendance ───────────────────────────────────────────────────────────────────────────

test("an assigned organizer checks an approved participant in, never a pending one", async () => {
  await assertSucceeds(checkIn(as("org1"), "org1", "r1"));
  await assertFails(checkIn(as("org1"), "org1", "pendingApp001"));
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
  await assertSucceeds(adminRegister(db, "closed", "walkin@example.org"));
  await assertFails(adminRegister(db, "full", "over@example.org"));
  await assertFails(adminRegister(db, "pub", "taken@example.org"));
});
