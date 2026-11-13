import assert from "node:assert/strict";
import { test } from "node:test";
import { NOTIFICATION_MAX_ATTEMPTS } from "../src/shared/config";
import { C, db } from "../src/shared/firebase";
import { processDueJobs, processNotificationJob, recordDeliveryStatus } from "../src/notifications/outbox";
import { gmailFailure, ProviderError } from "../src/notifications/providers";
import { parseMetaStatuses, verifyMetaSignature, verifySvixSignature } from "../src/notifications/webhooks";
import { newTicketSecret, ticketCode } from "../src/shared/ticketCode";
import { createHmac } from "node:crypto";
import { writeRegistration } from "../scripts/demoData";
import { FakeEmail, FakeWhatsApp, makeEvent, uid } from "./helpers";

/** A registration exactly as the app writes it, with its outbox jobs. */
async function registered(extra: { deliveryPreference: "EMAIL_ONLY" | "BOTH"; whatsappConsent?: boolean } = { deliveryPreference: "BOTH", whatsappConsent: true }) {
  const eventId = await makeEvent();
  const registrationId = await writeRegistration(db, eventId, { fullName: "Ahmed Benali", email: `${uid("p")}@example.org`, phone: "+213555123456", ...extra });
  const ticket = { registrationId, eventId };
  const id = (channel: string) => `${registrationId}_v1_${channel}_REGISTRATION_CONFIRMATION`;
  const job = async (channel: string) => (await db.collection(C.notificationJobs).doc(id(channel)).get()).data()!;
  return { eventId, ticket, id, job };
}

test("both channels are delivered independently, with the ticket image on WhatsApp", async () => {
  const { id, job } = await registered();
  const deps = { email: new FakeEmail(), whatsapp: new FakeWhatsApp() };
  assert.equal(await processNotificationJob(db, id("EMAIL"), deps), "SENT");
  assert.equal(await processNotificationJob(db, id("WHATSAPP"), deps), "SENT");
  assert.equal((await job("EMAIL")).providerMessageId, "e-1");
  assert.equal((await job("WHATSAPP")).provider, "fake-wa");
  assert.ok(deps.whatsapp.images[0] > 10_000, "a real PNG ticket was handed to the WhatsApp provider");
});

test("an email outage leaves the registration and WhatsApp untouched, then a retry succeeds without duplicates", async () => {
  const { ticket, id, job } = await registered();
  const email = new FakeEmail();
  email.failures.push(new ProviderError("HTTP 503", true));
  const deps = { email, whatsapp: new FakeWhatsApp() };

  assert.equal(await processNotificationJob(db, id("EMAIL"), deps), "RETRYING");
  assert.equal(await processNotificationJob(db, id("WHATSAPP"), deps), "SENT");
  const failed = await job("EMAIL");
  assert.equal(failed.lastError, "HTTP 503");
  assert.ok(failed.nextAttemptAt.toMillis() > Date.now());
  assert.equal((await db.collection(C.registrations).doc(ticket.registrationId).get()).data()!.status, "REGISTERED");

  // The scheduled worker does nothing before the retry time, and delivers after it.
  assert.equal(await processNotificationJob(db, id("WHATSAPP"), deps), "SKIPPED");
  await processDueJobs(db, { ...deps, now: () => Date.now() + 10 * 60 * 1000 }, 500);
  assert.equal((await job("EMAIL")).status, "SENT");
  // processDueJobs also drains other tests' queued jobs, so assert on this registration's own jobs.
  assert.equal((await job("EMAIL")).attemptCount, 2);
  assert.equal((await job("WHATSAPP")).attemptCount, 1, "the delivered channel is never sent again");
  assert.equal((await db.collection(C.registrations).where("eventId", "==", ticket.eventId).get()).size, 1);
});

test("a permanent failure is not retried; repeated temporary failures stop at the limit", async () => {
  const permanent = await registered({ deliveryPreference: "EMAIL_ONLY" });
  const email = new FakeEmail();
  email.failures.push(new ProviderError("HTTP 422: invalid recipient", false));
  assert.equal(await processNotificationJob(db, permanent.id("EMAIL"), { email, whatsapp: new FakeWhatsApp() }), "FAILED");

  const flaky = await registered({ deliveryPreference: "EMAIL_ONLY" });
  const down = new FakeEmail();
  down.failures.push(...Array.from({ length: 10 }, () => new ProviderError("HTTP 500", true)));
  const deps = { email: down, whatsapp: new FakeWhatsApp() };
  let status = "";
  for (let i = 0; i < NOTIFICATION_MAX_ATTEMPTS; i++) status = await processNotificationJob(db, flaky.id("EMAIL"), deps);
  assert.equal(status, "FAILED");
  assert.equal(down.calls, NOTIFICATION_MAX_ATTEMPTS);
  assert.equal((await flaky.job("EMAIL")).attemptCount, NOTIFICATION_MAX_ATTEMPTS);
});

test("a sender setup error keeps the email queued without using up an attempt", async () => {
  const { id, job } = await registered({ deliveryPreference: "EMAIL_ONLY" });
  const email = new FakeEmail();
  email.failures.push(new ProviderError("HTTP 403: The gmail.com domain is not verified", false, true));
  assert.equal(await processNotificationJob(db, id("EMAIL"), { email, whatsapp: new FakeWhatsApp() }), "PENDING");
  const queued = await job("EMAIL");
  assert.equal(queued.attemptCount, 0);
  assert.ok(queued.nextAttemptAt.toMillis() > Date.now() + 5 * 60 * 1000, "not retried in the same run");
  assert.match(queued.lastError, /not verified/);
});

test("concurrent workers send one message for one job", async () => {
  const { id } = await registered({ deliveryPreference: "EMAIL_ONLY" });
  const email = new FakeEmail();
  const deps = { email, whatsapp: new FakeWhatsApp() };
  const results = await Promise.all(Array.from({ length: 5 }, () => processNotificationJob(db, id("EMAIL"), deps)));
  assert.equal(email.calls, 1);
  assert.equal(results.filter((r) => r === "SENT").length, 1);
  assert.equal(results.filter((r) => r === "SKIPPED").length, 4);
});

test("webhooks mark delivery and are only accepted with a valid signature", async () => {
  const { id, job } = await registered({ deliveryPreference: "EMAIL_ONLY" });
  await processNotificationJob(db, id("EMAIL"), { email: new FakeEmail(), whatsapp: new FakeWhatsApp() });
  assert.equal(await recordDeliveryStatus(db, (await job("EMAIL")).providerMessageId + "-nope", "DELIVERED"), false);

  const body = Buffer.from(JSON.stringify({ entry: [{ changes: [{ value: { statuses: [{ id: "wamid.1", status: "delivered" }, { id: "wamid.2", status: "failed", errors: [{ title: "Blocked" }] }] } }] }] }));
  const signature = "sha256=" + createHmac("sha256", "app-secret").update(body).digest("hex");
  assert.equal(verifyMetaSignature(body, signature, "app-secret"), true);
  assert.equal(verifyMetaSignature(body, signature, "wrong"), false);
  assert.equal(verifyMetaSignature(body, undefined, "app-secret"), false);
  assert.deepEqual(parseMetaStatuses(JSON.parse(body.toString())), [{ providerMessageId: "wamid.1", outcome: "DELIVERED" }, { providerMessageId: "wamid.2", outcome: "FAILED", detail: "Blocked" }]);

  const key = Buffer.from("svix-secret").toString("base64");
  const ts = String(Math.floor(Date.now() / 1000));
  const svix = createHmac("sha256", Buffer.from(key, "base64")).update(`msg_1.${ts}.${body.toString()}`).digest("base64");
  assert.equal(verifySvixSignature(body, { id: "msg_1", timestamp: ts, signature: `v1,${svix}` }, `whsec_${key}`), true);
  assert.equal(verifySvixSignature(body, { id: "msg_2", timestamp: ts, signature: `v1,${svix}` }, `whsec_${key}`), false);
});

test("the email QR is built from the secret stored on the registration", async () => {
  const { ticket } = await registered({ deliveryPreference: "EMAIL_ONLY" });
  const reg = (await db.collection(C.registrations).doc(ticket.registrationId).get()).data()!;
  assert.match(reg.ticketSecret, /^[a-f0-9]{64}$/);
  assert.equal(ticketCode(ticket.registrationId, 1, reg.ticketSecret), `SC2.${ticket.registrationId}.1.${reg.ticketSecret}`);
  assert.notEqual(newTicketSecret(), newTicketSecret());
});

test("Gmail failures are sorted: setup problem, try later, or a bad recipient", () => {
  const auth = gmailFailure({ code: "EAUTH", responseCode: 535, message: "Username and Password not accepted" });
  assert.equal(auth.configuration, true, "a wrong App Password keeps the emails queued");
  assert.equal(gmailFailure({ responseCode: 550, message: "550-5.4.5 Daily user sending limit exceeded" }).retryable, true);
  assert.equal(gmailFailure({ responseCode: 421, message: "Try again later" }).retryable, true);
  assert.equal(gmailFailure({ code: "ETIMEDOUT", message: "Connection timeout" }).retryable, true);
  const bad = gmailFailure({ responseCode: 553, message: "Invalid recipient" });
  assert.equal(bad.retryable, false);
  assert.equal(bad.configuration, false);
});
