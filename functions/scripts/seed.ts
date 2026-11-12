/**
 * Emulator-only demo data: an administrator, an organizer, club settings and three events.
 * Refuses to run against a real project. Credentials are printed, not hard-coded in the app.
 *
 *   firebase emulators:start      (in another terminal)
 *   npm run seed
 */
process.env.GCLOUD_PROJECT ??= "demo-scientific-club";
process.env.FIRESTORE_EMULATOR_HOST ??= "localhost:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= "localhost:9099";

import { db } from "../src/shared/firebase";
import { writeDemoData } from "./demoData";
import { ensureStaff } from "./makeAdmin";

async function main() {
  if (!process.env.GCLOUD_PROJECT!.startsWith("demo-")) throw new Error("The seed script only runs against a demo-* emulator project.");
  const adminId = await ensureStaff("admin@club.test", "Admin#12345", "Amina Admin", "ADMIN");
  const organizerId = await ensureStaff("organizer@club.test", "Organizer#12345", "Omar Organizer", "ORGANIZER");
  await writeDemoData(db, { uid: adminId, name: "Amina Admin" }, { uid: organizerId, name: "Omar Organizer", email: "organizer@club.test" });
  console.log("\nDemo accounts (emulator only):\n  admin@club.test      Admin#12345\n  organizer@club.test  Organizer#12345\n");
}

main().then(() => process.exit(0), (error) => { console.error(error); process.exit(1); });
