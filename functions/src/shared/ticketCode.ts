import { randomBytes } from "node:crypto";

/**
 * The QR on a ticket: `SC2.<registrationId>.<ticketVersion>.<secret>`, the same format the app
 * builds (shared/.../domain/ticket/TicketCode.kt). The secret is stored on the registration,
 * which only administrators and the event's organizers can read.
 */
export function ticketCode(registrationId: string, version: number, secret: string): string {
  return `SC2.${registrationId}.${version}.${secret}`;
}

/** 64 hex characters, like the app's newTicketSecret(). */
export function newTicketSecret(): string {
  return randomBytes(32).toString("hex");
}

/** One `registrationKeys` document per (event, email), like the app's registrationKeyId(). */
export function registrationKeyId(eventId: string, normalizedEmail: string): string {
  return `${eventId}_${normalizedEmail}`;
}
