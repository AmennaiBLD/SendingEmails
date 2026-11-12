import { setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { CallableRequest, HttpsError, onCall, onRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { OutboxDeps, processDueJobs, processNotificationJob, recordDeliveryStatus } from "./notifications/outbox";
import { EmailProvider, MetaWhatsAppCloudProvider, MockEmailProvider, MockWhatsAppProvider, ResendEmailProvider, WhatsAppProvider } from "./notifications/providers";
import { parseMetaStatuses, parseResendEvent, verifyMetaSignature, verifySvixSignature } from "./notifications/webhooks";
import { requireAdmin } from "./shared/auth";
import * as cfg from "./shared/config";
import { C, db } from "./shared/firebase";
import { requireStr } from "./shared/validation";
import { confirmTicketAccessCore, requestTicketAccessCore } from "./ticket/ticketAccess";

// Cloud Functions only send messages. Every other write (registration, check-in/out, events,
// staff, settings) is made by the apps directly and checked by firestore.rules.

setGlobalOptions({ region: cfg.REGION, maxInstances: 20 });

const DELIVERY = [cfg.RESEND_API_KEY, cfg.WHATSAPP_ACCESS_TOKEN];
function emailProvider(): EmailProvider {
  return cfg.EMAIL_PROVIDER.value() === "resend" ? new ResendEmailProvider(cfg.RESEND_API_KEY.value(), cfg.EMAIL_FROM.value()) : new MockEmailProvider();
}

function whatsAppProvider(): WhatsAppProvider {
  return cfg.WHATSAPP_PROVIDER.value() === "meta"
    ? new MetaWhatsAppCloudProvider({
        accessToken: cfg.WHATSAPP_ACCESS_TOKEN.value(), apiVersion: cfg.WHATSAPP_API_VERSION.value(),
        phoneNumberId: cfg.WHATSAPP_PHONE_NUMBER_ID.value(), templateName: cfg.WHATSAPP_TICKET_TEMPLATE.value(),
        templateLanguage: cfg.WHATSAPP_TEMPLATE_LANGUAGE.value(),
      })
    : new MockWhatsAppProvider();
}

function outboxDeps(): OutboxDeps {
  return { email: emailProvider(), whatsapp: whatsAppProvider() };
}

function data(request: CallableRequest): Record<string, unknown> {
  return request.data && typeof request.data === "object" ? (request.data as Record<string, unknown>) : {};
}

// ── Ticket retrieval by email code (no sign-in) ───────────────────────────────────────────

export const requestTicketAccess = onCall({ secrets: DELIVERY }, (request) =>
  requestTicketAccessCore(db, data(request), emailProvider()));

export const confirmTicketAccess = onCall((request) =>
  confirmTicketAccessCore(db, data(request)));

// ── Notifications ─────────────────────────────────────────────────────────────────────────

/** Manual retry of a failed (or stuck) notification from the admin dashboard. */
export const retryNotification = onCall({ secrets: DELIVERY }, async (request) => {
  await requireAdmin(db, request);
  const jobId = requireStr(data(request).jobId, "jobId");
  const ref = db.collection(C.notificationJobs).doc(jobId);
  const job = (await ref.get()).data();
  if (!job) throw new HttpsError("not-found", "Notification not found.");
  if (job.status === "SENT" || job.status === "DELIVERED") return { result: "ALREADY_SENT", status: job.status };
  if (job.status === "FAILED") await ref.update({ status: "PENDING", attemptCount: 0, lastError: null });
  const status = await processNotificationJob(db, jobId, outboxDeps());
  return { result: "PROCESSED", status };
});

// ── Notification outbox workers ───────────────────────────────────────────────────────────

export const processNotificationOnCreate = onDocumentCreated({ document: `${C.notificationJobs}/{jobId}`, secrets: DELIVERY }, async (event) => {
  await processNotificationJob(db, event.params.jobId, outboxDeps());
});

export const processDueNotifications = onSchedule({ schedule: "every 5 minutes", secrets: DELIVERY }, async () => {
  await processDueJobs(db, outboxDeps());
});

// ── Provider webhooks: delivery status ────────────────────────────────────────────────────

export const whatsappWebhook = onRequest({ secrets: [cfg.WHATSAPP_APP_SECRET, cfg.WHATSAPP_WEBHOOK_VERIFY_TOKEN] }, async (req, res) => {
  if (req.method === "GET") {
    const ok = req.query["hub.mode"] === "subscribe" && req.query["hub.verify_token"] === cfg.WHATSAPP_WEBHOOK_VERIFY_TOKEN.value();
    res.status(ok ? 200 : 403).send(ok ? String(req.query["hub.challenge"]) : "Forbidden");
    return;
  }
  if (!verifyMetaSignature(req.rawBody, req.header("x-hub-signature-256"), cfg.WHATSAPP_APP_SECRET.value())) {
    res.status(401).send("Bad signature");
    return;
  }
  for (const update of parseMetaStatuses(req.body)) await recordDeliveryStatus(db, update.providerMessageId, update.outcome, update.detail);
  res.status(200).send("OK");
});

export const emailWebhook = onRequest({ secrets: [cfg.RESEND_WEBHOOK_SECRET] }, async (req, res) => {
  const headers = { id: req.header("svix-id"), timestamp: req.header("svix-timestamp"), signature: req.header("svix-signature") };
  if (!verifySvixSignature(req.rawBody, headers, cfg.RESEND_WEBHOOK_SECRET.value())) {
    res.status(401).send("Bad signature");
    return;
  }
  const update = parseResendEvent(req.body);
  if (update) await recordDeliveryStatus(db, update.providerMessageId, update.outcome, update.detail);
  res.status(200).send("OK");
});
