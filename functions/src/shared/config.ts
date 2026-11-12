import { defineSecret, defineString } from "firebase-functions/params";

// Secrets live in Secret Manager (emulator: functions/.secret.local). They never reach a client.
export const RESEND_API_KEY = defineSecret("RESEND_API_KEY");
export const RESEND_WEBHOOK_SECRET = defineSecret("RESEND_WEBHOOK_SECRET");
export const WHATSAPP_ACCESS_TOKEN = defineSecret("WHATSAPP_ACCESS_TOKEN");
export const WHATSAPP_APP_SECRET = defineSecret("WHATSAPP_APP_SECRET");
export const WHATSAPP_WEBHOOK_VERIFY_TOKEN = defineSecret("WHATSAPP_WEBHOOK_VERIFY_TOKEN");

// Plain configuration (functions/.env, see .env.example).
export const REGION = defineString("FUNCTIONS_REGION", { default: "us-central1" });
export const EMAIL_PROVIDER = defineString("EMAIL_PROVIDER", { default: "mock" });
export const EMAIL_FROM = defineString("EMAIL_FROM", { default: "Scientific Club <tickets@example.org>" });
export const WHATSAPP_PROVIDER = defineString("WHATSAPP_PROVIDER", { default: "mock" });
export const WHATSAPP_API_VERSION = defineString("WHATSAPP_API_VERSION", { default: "v23.0" });
export const WHATSAPP_PHONE_NUMBER_ID = defineString("WHATSAPP_PHONE_NUMBER_ID", { default: "" });
export const WHATSAPP_BUSINESS_ACCOUNT_ID = defineString("WHATSAPP_BUSINESS_ACCOUNT_ID", { default: "" });
export const WHATSAPP_TICKET_TEMPLATE = defineString("WHATSAPP_TICKET_TEMPLATE", { default: "event_ticket" });
export const WHATSAPP_TEMPLATE_LANGUAGE = defineString("WHATSAPP_TEMPLATE_LANGUAGE", { default: "en" });
export const PUBLIC_SITE_URL = defineString("PUBLIC_SITE_URL", { default: "" });

export const NOTIFICATION_MAX_ATTEMPTS = 5;
export const NOTIFICATION_LEASE_MS = 2 * 60 * 1000;
/** Retry delay after attempt n (1-based): 1, 2, 4, 8 minutes … capped at an hour. */
export function retryDelayMs(attempt: number): number {
  return Math.min(60 * 60 * 1000, 60 * 1000 * 2 ** (attempt - 1));
}
