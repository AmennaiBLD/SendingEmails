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

async function main() {
  required("GCLOUD_PROJECT");
  const email = new ResendEmailProvider(required("RESEND_API_KEY"), required("EMAIL_FROM"));
  let total = 0;
  // A run drains the queue in batches; the cap keeps one run short.
  for (let round = 0; round < 10; round++) {
    const processed = await processDueJobs(db, { email, whatsapp: new MockWhatsAppProvider() }, 50, ["EMAIL"]);
    total += processed;
    if (processed === 0) break;
  }
  console.log(`email jobs processed: ${total}`);
}

main().then(() => process.exit(0), (error) => { console.error(error); process.exit(1); });
