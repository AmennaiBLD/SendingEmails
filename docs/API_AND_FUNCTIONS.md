# Cloud Functions

> **Now only email/WhatsApp.** Registration, check-in/out, corrections, events, staff, assignments and settings are
> written by the apps directly (see the repositories in `shared/.../data/repository/` and `firestore.rules`).
> The tables below that describe those callables are history. What remains deployed: `requestTicketAccess`,
> `confirmTicketAccess`, `retryNotification`, `processNotificationOnCreate`, `processDueNotifications`,
> `whatsappWebhook`, `emailWebhook`.

Source: `functions/src`. Each callable is a thin wrapper in `index.ts` around a `…Core` function that takes
the Firestore instance, so the logic is tested directly against the emulator.

Business answers come back as `{ result: "NAME", … }`. An `HttpsError` means the call itself was refused
(`unauthenticated`, `permission-denied`, `invalid-argument`). Instants are epoch milliseconds.

## Public (no sign-in)

| Callable | Input | Results |
|---|---|---|
| `registerForEvent` | `eventId, fullName, email, phone, university, faculty, department, studentId, deliveryPreference, whatsappConsent, privacyAccepted` | `REGISTERED` + `ticket`, `INVALID_INPUT` + `invalidFields`, `EVENT_NOT_FOUND`, `EVENT_NOT_OPEN`, `REGISTRATION_NOT_OPEN`, `REGISTRATION_CLOSED`, `EVENT_FULL`, `ALREADY_REGISTERED` |
| `requestTicketAccess` | `eventId, email` | always `{ requestId }` (it never reveals whether the email is registered) |
| `confirmTicketAccess` | `requestId, code, replace` | `OK` + `ticket`, `INVALID_CODE`, `EXPIRED`, `TOO_MANY_ATTEMPTS` |

`ticket`: `registrationId, eventId, eventTitle, participantName, location, timezone, startAt, endAt, ticketVersion,
ticketCode, qrPngBase64, ticketPngBase64, emailQueued, whatsappQueued`.

## Organizer (assigned to the event) or administrator

| Callable | Input | Results |
|---|---|---|
| `verifyAndCheckIn` | `eventId` and `qr` or `registrationId` | `VALID_CHECK_IN`, `INVALID_QR`, `REGISTRATION_NOT_FOUND`, `REGISTRATION_CANCELLED`, `EVENT_NOT_OPEN`, `ALREADY_CHECKED_IN`, `ALREADY_CHECKED_OUT`, `ORGANIZER_NOT_AUTHORIZED`, `EVENT_MISMATCH`, `TICKET_REVOKED` |
| `verifyAndCheckOut` | same | `VALID_CHECK_OUT`, `NOT_CHECKED_IN`, `ALREADY_CHECKED_OUT`, and the refusals above |
| `correctAttendance` | `sessionId, checkInAt?, checkOutAt?, reason` | `CORRECTED`, `REASON_REQUIRED`, `INVALID_TIMES`, `NOT_AUTHORIZED`, `SESSION_NOT_FOUND` |

Scan answers carry `participantName, eventTitle, sessionId, checkInAt, checkOutAt, durationSeconds, totalDurationSeconds`.

## Administrator

| Callable | Purpose |
|---|---|
| `createEvent`, `updateEvent` | validates dates, time zone, registration window, capacity → `SAVED`, `INVALID_INPUT`, `CAPACITY_BELOW_REGISTRATIONS`, `EVENT_NOT_FOUND` |
| `setEventStatus` | DRAFT→PUBLISHED/ARCHIVED · PUBLISHED→DRAFT/CANCELLED/COMPLETED · CANCELLED→ARCHIVED · COMPLETED→PUBLISHED/ARCHIVED |
| `assignOrganizer` | `eventId, organizerId, active` |
| `createStaffAccount`, `updateStaffAccess` | creates a sign-in and its role; deactivation disables the account and revokes its sessions; nobody can change their own access |
| `adminRegisterParticipant` | registration that ignores the window, never capacity or duplicates |
| `cancelRegistration` | frees the seat; the email may register again |
| `resendEventTicket` | `registrationId, replace`; `replace` bumps the ticket version and revokes the old QR |
| `retryNotification` | re-runs a failed or stuck job |
| `updateClubSettings` | club name, contact, links, grace period |

## Background

| Function | Trigger |
|---|---|
| `processNotificationOnCreate` | a new `notificationJobs` document |
| `processDueNotifications` | every 5 minutes: retries that are due, and jobs whose worker died |
| `whatsappWebhook` | Meta delivery statuses (signature-checked) |
| `emailWebhook` | Resend delivery events (Svix signature-checked) |

## Not a function: analytics

The spec lists `getEventAnalytics`. Analytics are computed in the shared Kotlin domain
(`domain/analytics`) from rule-protected reads instead, so there is one implementation of the maths, it is
unit-tested, and the CSV/PDF exports use exactly the numbers on screen. See [ANALYTICS.md](ANALYTICS.md).

## Secrets and configuration
See `.env.example`. Secrets are declared with `defineSecret` and only bound to the functions that use them.
