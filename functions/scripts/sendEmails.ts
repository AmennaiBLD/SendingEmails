/**
 * The email sender without Cloud Functions: sends every queued ticket email (notificationJobs,
 * channel EMAIL) through Resend, with the QR image and the PDF ticket attached, then exits.
 * GitHub Actions runs it on a schedule (.github/workflows/send-emails.yml).
 *
 * Environment:
 *   GOOGLE_APPLICATION_CREDENTIALS  service-account JSON of the Firebase project
 *   GCLOUD_PROJECT                  the Firebase project id
 *   RESEND_API_KEY                  secret, never in the app or the repository
 *   EMAIL_FROM                      e.g. "Scientific Club <tickets@your-domain.org>"
 *
 * WhatsApp jobs are left PENDING until a WhatsApp provider is configured.
 */
import { db } from "../src/shared/firebase";
import { processDueJobs } from "../src/notifications/outbox";
import { MockWhatsAppProvider, ResendEmailProvider } from "../src/notifications/providers";

function required(name: string): string {
  const value = (process.env[name] ?? "").trim();
  if (!value) throw new Error(`${name} is not set.`);
  return value;
}

/** Resend can only send from onboarding@resend.dev or from a domain verified in the Resend account. */
const MAILBOX_PROVIDERS = /@(gmail|googlemail|yahoo|ymail|hotmail|outlook|live|msn|icloud|me|aol|proton|protonmail)\.[a-z.]+>?\s*$/i;

async function main() {
  required("GCLOUD_PROJECT");
  const from = required("EMAIL_FROM");
  if (MAILBOX_PROVIDERS.test(from)) {
    throw new Error(
      `EMAIL_FROM is a personal mailbox address. Resend cannot send as Gmail, Outlook, Yahoo...\n` +
      `Set EMAIL_FROM to "Scientific Club <onboarding@resend.dev>" (delivers only to your Resend sign-up email) ` +
      `or to an address on a domain verified at https://resend.com/domains.`,
    );
  }
  const email = new ResendEmailProvider(required("RESEND_API_KEY"), from);
  let total = 0;
  // A run drains the queue in batches; the cap keeps one run short.
  for (let round = 0; round < 10; round++) {
    const processed = await processDueJobs(db, { email, whatsapp: new MockWhatsAppProvider() }, 50, ["EMAIL"]);
    total += processed;
    if (processed === 0) break;
  }
  const waiting = await db.collection("notificationJobs").where("channel", "==", "EMAIL").where("status", "==", "PENDING").get();
  const setupProblem = waiting.docs.find((d) => /^HTTP 40[13]/.test(d.data().lastError ?? ""));
  console.log(`email jobs processed: ${total}`);
  if (setupProblem) {
    // Fail the run so the problem shows in red on GitHub; the emails stay queued.
    throw new Error(`Resend refused to send (the emails stay queued): ${setupProblem.data().lastError}`);
  }
}

main().then(() => process.exit(0), (error) => { console.error(error); process.exit(1); });
