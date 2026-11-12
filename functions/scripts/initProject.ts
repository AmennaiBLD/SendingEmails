/**
 * Fills a REAL Firebase project with a first, consistent data set so every collection the app
 * uses exists: settings, users, events, eventOrganizers, registrations, registrationKeys,
 * attendanceSessions, notificationJobs and auditLogs, in the shape the app writes.
 *
 *   Values come from functions/.project.local (gitignored): GCLOUD_PROJECT,
 *   ADMIN_EMAIL/PASSWORD, ORGANIZER_EMAIL/PASSWORD.
 *
 *   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> npm run init-project
 *
 * Safe to run twice: existing events and demo participants are left alone.
 * Demo participants use example.com addresses, so no real person ever receives a message.
 */
import { db } from "../src/shared/firebase";
import { writeDemoData } from "./demoData";
import { ensureStaff } from "./makeAdmin";

function required(name: string): string {
  const value = process.env[name] ?? "";
  if (value.length < 8) throw new Error(`${name} is missing (8+ characters).`);
  return value;
}

async function main() {
  if (!process.env.GCLOUD_PROJECT) throw new Error("Set GCLOUD_PROJECT to the Firebase project id.");
  const adminEmail = process.env.ADMIN_EMAIL || "admin@club.test";
  const organizerEmail = process.env.ORGANIZER_EMAIL || "organizer@club.test";
  const adminId = await ensureStaff(adminEmail, required("ADMIN_PASSWORD"), "Club Administrator", "ADMIN");
  const organizerId = await ensureStaff(organizerEmail, required("ORGANIZER_PASSWORD"), "Club Organizer", "ORGANIZER");
  await writeDemoData(db, { uid: adminId, name: "Club Administrator" }, { uid: organizerId, name: "Club Organizer", email: organizerEmail });
}

main().then(() => process.exit(0), (error) => { console.error(error); process.exit(1); });
