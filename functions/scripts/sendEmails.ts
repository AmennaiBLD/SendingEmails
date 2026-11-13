/**
 * The email sender without Cloud Functions: sends every queued ticket email (notificationJobs,
 * channel EMAIL), with the QR image and the PDF ticket attached, then exits.
 * GitHub Actions runs it on a schedule (.github/workflows/send-emails.yml).
 *
 * Environment:
 *   GOOGLE_APPLICATION_CREDENTIALS  service-account JSON of the Firebase project
 *   GCLOUD_PROJECT                  the Firebase project id
 *   Gmail (free, no domain):        GMAIL_USER, GMAIL_APP_PASSWORD, optional EMAIL_FROM_NAME
 *   or Resend (needs a domain):     RESEND_API_KEY, EMAIL_FROM
 *
 * WhatsApp jobs are left PENDING until a WhatsApp provider is configured.
 */
import { db } from "../src/shared/firebase";
import { processDueJobs } from "../src/notifications/outbox";
import { EmailProvider, GmailEmailProvider, MockWhatsAppProvider, ResendEmailProvider } from "../src/notifications/providers";

function env(name: string): string {
  return (process.env[name] ?? "").trim();
}

function required(name: string): string {
  const value = env(name);
  if (!value) throw new Error(`${name} is not set.`);
  return value;
}

/** Resend can only send from onboarding@resend.dev or from a domain verified in the Resend account. */
const MAILBOX_PROVIDERS = /@(gmail|googlemail|yahoo|ymail|hotmail|outlook|live|msn|icloud|me|aol|proton|protonmail)\.[a-z.]+>?\s*$/i;

function emailProvider(): EmailProvider {
  if (env("GMAIL_USER") && env("GMAIL_APP_PASSWORD")) {
    console.log("Email provider: Gmail");
    return new GmailEmailProvider(env("GMAIL_USER"), env("GMAIL_APP_PASSWORD"), env("EMAIL_FROM_NAME") || "Scientific Club");
  }
  const from = required("EMAIL_FROM");
  if (MAILBOX_PROVIDERS.test(from)) {
    throw new Error(
      "EMAIL_FROM is a personal mailbox address, and Resend cannot send as Gmail, Outlook, Yahoo...\n" +
      "Either add the secrets GMAIL_USER and GMAIL_APP_PASSWORD to send through that Gmail account (free), " +
      "or set EMAIL_FROM to an address on a domain verified at https://resend.com/domains.",
    );
  }
  console.log("Email provider: Resend");
  return new ResendEmailProvider(required("RESEND_API_KEY"), from);
}

async function main() {
  required("GCLOUD_PROJECT");
  const email = emailProvider();
  let total = 0;
  // A run drains the queue in batches; the cap keeps one run short.
  for (let round = 0; round < 10; round++) {
    const processed = await processDueJobs(db, { email, whatsapp: new MockWhatsAppProvider() }, 50, ["EMAIL"]);
    total += processed;
    if (processed === 0) break;
  }
  const waiting = await db.collection("notificationJobs").where("channel", "==", "EMAIL").where("status", "==", "PENDING").get();
  const setupProblem = waiting.docs.find((d) => /^(HTTP 40[13]|Gmail:)/.test(d.data().lastError ?? ""));
  console.log(`email jobs processed: ${total}`);
  if (setupProblem) {
    // Fail the run so the problem shows in red on GitHub; the emails stay queued.
    throw new Error(`The email provider refused to send (the emails stay queued): ${setupProblem.data().lastError}`);
  }
}

main().then(() => process.exit(0), (error) => { console.error(error); process.exit(1); });
