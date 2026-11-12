import { Firestore } from "firebase-admin/firestore";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { C } from "./firebase";

export type Role = "PARTICIPANT" | "ORGANIZER" | "ADMIN";

export interface Actor {
  uid: string;
  role: Role;
  name: string;
  email: string;
}

/**
 * Resolves the caller from `users/{uid}`, which only an administrator can write. The role is
 * never taken from the client, and a deactivated account is refused immediately.
 */
export async function requireActor(db: Firestore, request: CallableRequest): Promise<Actor> {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to continue.");
  const snap = await db.collection(C.users).doc(uid).get();
  const data = snap.data();
  if (!data || data.isActive !== true) throw new HttpsError("permission-denied", "This account has no access.");
  const role: Role = data.role === "ADMIN" || data.role === "ORGANIZER" ? data.role : "PARTICIPANT";
  return { uid, role, name: data.fullName ?? "", email: data.email ?? "" };
}

export async function requireAdmin(db: Firestore, request: CallableRequest): Promise<Actor> {
  const actor = await requireActor(db, request);
  if (actor.role !== "ADMIN") throw new HttpsError("permission-denied", "Administrators only.");
  return actor;
}
