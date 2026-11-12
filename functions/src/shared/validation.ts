import { createHash } from "node:crypto";
import { HttpsError } from "firebase-functions/v2/https";

export function str(value: unknown, max: number): string {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

export function requireStr(value: unknown, field: string, max = 200): string {
  const text = str(value, max);
  if (!text) throw new HttpsError("invalid-argument", `${field} is required.`);
  return text;
}

export function normalizeEmail(value: unknown): string {
  return str(value, 254).toLowerCase();
}

export function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value) && value.length <= 254;
}

/** Keeps digits and a leading +; "00" prefix becomes "+". */
export function normalizePhone(value: unknown): string {
  let text = str(value, 32).replace(/[\s\-().]/g, "");
  if (text.startsWith("00")) text = "+" + text.slice(2);
  return text;
}

/** E.164: + then 8–15 digits, first digit non-zero. */
export function isInternationalPhone(value: string): boolean {
  return /^\+[1-9]\d{7,14}$/.test(value);
}

export function sha256Hex(text: string): string {
  return createHash("sha256").update(text).digest("hex");
}

export function millis(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? Math.floor(value) : null;
}

export function slugify(title: string): string {
  const base = title.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
  return base.slice(0, 60) || "event";
}
