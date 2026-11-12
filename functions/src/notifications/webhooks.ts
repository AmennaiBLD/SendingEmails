import { createHmac, timingSafeEqual } from "node:crypto";

function safeEqual(a: string, b: string): boolean {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

/** Meta signs each webhook body with the app secret: `X-Hub-Signature-256: sha256=<hex>`. */
export function verifyMetaSignature(rawBody: Buffer, header: string | undefined, appSecret: string): boolean {
  if (!header || !appSecret) return false;
  return safeEqual(header, "sha256=" + createHmac("sha256", appSecret).update(rawBody).digest("hex"));
}

/** Resend webhooks use the Svix scheme: HMAC-SHA256 over `id.timestamp.body` with the `whsec_` secret. */
export function verifySvixSignature(rawBody: Buffer, headers: { id?: string; timestamp?: string; signature?: string }, secret: string, nowMs = Date.now()): boolean {
  if (!headers.id || !headers.timestamp || !headers.signature || !secret) return false;
  if (Math.abs(nowMs / 1000 - Number(headers.timestamp)) > 5 * 60) return false;
  const key = Buffer.from(secret.replace(/^whsec_/, ""), "base64");
  const expected = createHmac("sha256", key).update(`${headers.id}.${headers.timestamp}.${rawBody.toString("utf8")}`).digest("base64");
  return headers.signature.split(" ").some((part) => safeEqual(part.replace(/^v1,/, ""), expected));
}

export interface DeliveryUpdate {
  providerMessageId: string;
  outcome: "DELIVERED" | "FAILED";
  detail?: string;
}

export function parseMetaStatuses(body: any): DeliveryUpdate[] {
  const updates: DeliveryUpdate[] = [];
  for (const entry of body?.entry ?? []) {
    for (const change of entry?.changes ?? []) {
      for (const status of change?.value?.statuses ?? []) {
        if (status.status === "delivered" || status.status === "read") updates.push({ providerMessageId: status.id, outcome: "DELIVERED" });
        else if (status.status === "failed") updates.push({ providerMessageId: status.id, outcome: "FAILED", detail: status.errors?.[0]?.title });
      }
    }
  }
  return updates;
}

export function parseResendEvent(body: any): DeliveryUpdate | null {
  const id = body?.data?.email_id;
  if (typeof id !== "string") return null;
  if (body.type === "email.delivered") return { providerMessageId: id, outcome: "DELIVERED" };
  if (body.type === "email.bounced" || body.type === "email.failed") return { providerMessageId: id, outcome: "FAILED", detail: body.type };
  return null;
}
