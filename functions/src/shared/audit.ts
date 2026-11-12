import { DocumentReference, Firestore, Timestamp, Transaction } from "firebase-admin/firestore";
import { C } from "./firebase";

export interface AuditEntry {
  actorId: string;
  actorName: string;
  action: string;
  entityType: string;
  entityId: string;
  eventId?: string | null;
  reason?: string | null;
  previousValue?: Record<string, unknown> | null;
  newValue?: Record<string, unknown> | null;
}

function show(value: unknown): string {
  if (value === null || value === undefined) return "—";
  // Epoch milliseconds read better as an instant.
  if (typeof value === "number" && value > 1e11) return new Date(value).toISOString().slice(0, 16).replace("T", " ") + " UTC";
  return String(value);
}

/** A one-line, human-readable summary of what changed, for the audit viewer. */
function describe(entry: AuditEntry): string {
  const after = entry.newValue ?? {};
  const before = entry.previousValue ?? {};
  return Object.keys(after)
    .map((key) => (key in before ? `${key}: ${show(before[key])} → ${show(after[key])}` : `${key}: ${show(after[key])}`))
    .join("; ")
    .slice(0, 600);
}

function body(entry: AuditEntry) {
  return {
    ...entry,
    details: describe(entry),
    eventId: entry.eventId ?? null,
    reason: entry.reason ?? null,
    previousValue: entry.previousValue ?? null,
    newValue: entry.newValue ?? null,
    createdAt: Timestamp.now(),
  };
}

/** Audit entries are append-only: this is the only code that writes them, and rules deny clients. */
export function auditInTx(db: Firestore, tx: Transaction, entry: AuditEntry): DocumentReference {
  const ref = db.collection(C.auditLogs).doc();
  tx.set(ref, body(entry));
  return ref;
}

export async function audit(db: Firestore, entry: AuditEntry): Promise<void> {
  await db.collection(C.auditLogs).add(body(entry));
}
