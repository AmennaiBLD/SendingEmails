/**
 * One-off migration for data written while every write went through Cloud Functions:
 *  - registrations get a `ticketSecret` (QR codes are now `SC2.<id>.<version>.<secret>`; older
 *    signed QR codes stop working, so their ticket version goes up by one),
 *  - registrationKeys move from hashed ids to `<eventId>_<normalizedEmail>` (the rules check it).
 *
 *   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> GCLOUD_PROJECT=<id> npm run migrate
 *
 * Safe to run twice: registrations that already have a secret are skipped.
 */
import { C, db } from "../src/shared/firebase";
import { newTicketSecret, registrationKeyId } from "../src/shared/ticketCode";

async function main() {
  if (!process.env.GCLOUD_PROJECT) throw new Error("Set GCLOUD_PROJECT to the Firebase project id.");
  const registrations = await db.collection(C.registrations).get();
  let secrets = 0;
  let keys = 0;
  for (const doc of registrations.docs) {
    const reg = doc.data();
    const batch = db.batch();
    if (typeof reg.ticketSecret !== "string") {
      batch.update(doc.ref, { ticketSecret: newTicketSecret(), ticketVersion: (reg.ticketVersion ?? 1) + 1 });
      secrets++;
    }
    if (reg.status !== "CANCELLED") {
      const keyRef = db.collection(C.registrationKeys).doc(registrationKeyId(reg.eventId, reg.normalizedEmail));
      if (!(await keyRef.get()).exists) {
        batch.set(keyRef, { registrationId: doc.id, eventId: reg.eventId, createdAt: reg.registeredAt });
        keys++;
      }
    }
    await batch.commit();
  }
  // Old keys had a 40-character hash after the event id; the new ones end with the email.
  const oldKeys = (await db.collection(C.registrationKeys).get()).docs.filter((d) => !d.id.includes("@"));
  for (const key of oldKeys) await key.ref.delete();
  console.log(`registrations: ${registrations.size} checked, ${secrets} given a ticket secret; keys: ${keys} created, ${oldKeys.length} old removed`);
}

main().then(() => process.exit(0), (error) => { console.error(error); process.exit(1); });
