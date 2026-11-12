/**
 * Bootstraps the first administrator: creates (or finds) the sign-in and writes the role
 * document that only the backend may write.
 *
 *   production: GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> npm run make-admin -- <email> <password> "<Full Name>"
 *   emulator:   FIRESTORE_EMULATOR_HOST=localhost:8080 FIREBASE_AUTH_EMULATOR_HOST=localhost:9099 GCLOUD_PROJECT=demo-scientific-club npm run make-admin -- …
 */
import { getAuth } from "firebase-admin/auth";
import { Timestamp } from "firebase-admin/firestore";
import { C, db } from "../src/shared/firebase";

export async function ensureStaff(email: string, password: string, fullName: string, role: "ADMIN" | "ORGANIZER"): Promise<string> {
  const auth = getAuth();
  let uid: string;
  try {
    uid = (await auth.getUserByEmail(email)).uid;
  } catch {
    uid = (await auth.createUser({ email, password, displayName: fullName })).uid;
  }
  const now = Timestamp.now();
  await db.collection(C.users).doc(uid).set({ id: uid, fullName, email, phone: "", profilePhotoUrl: null, role, isActive: true, createdAt: now, updatedAt: now }, { merge: true });
  return uid;
}

if (require.main === module) {
  const [email, password, fullName] = process.argv.slice(2);
  if (!email || !password || password.length < 8) {
    console.error('Usage: npm run make-admin -- <email> <password (8+ chars)> "<Full Name>"');
    process.exit(1);
  }
  ensureStaff(email, password, fullName || "Administrator", "ADMIN").then((uid) => {
    console.log(`Administrator ready: ${email} (${uid})`);
    process.exit(0);
  });
}
