# Firestore schema

All instants are Firestore `Timestamp`s (UTC). Each event stores its IANA `timezone`; the app displays in it.
Only Cloud Functions write. Field names here are the ones the repositories read and the rules tests use.

## `users/{uid}` — staff profiles
`id, fullName, email, phone, profilePhotoUrl, role (PARTICIPANT|ORGANIZER|ADMIN), isActive, createdAt, updatedAt, createdBy`
Read: self, or admin.

## `events/{eventId}`
`id, title, slug, description, coverImageUrl, category, location, organizerName, startAt, endAt, timezone,
registrationOpenAt, registrationCloseAt, capacity (0 = unlimited), registrationCount, allowReentry, featured,
status (DRAFT|PUBLISHED|CANCELLED|COMPLETED|ARCHIVED), createdBy, createdAt, updatedAt, publishedAt`
Counters kept by the attendance transactions:
`attendeeCount, activeSessionCount, completedSessionCount, completedDurationSeconds`
Read: anyone when PUBLISHED or COMPLETED; admin; assigned organizer.

## `registrations/{registrationId}` (random id)
`id, eventId, participantId (null: guests), participantName, nameLower, email, normalizedEmail, phone, university,
faculty, department, studentId, status (REGISTERED|WAITLISTED|CANCELLED), ticketVersion, registeredAt, updatedAt,
deliveryPreference (EMAIL_ONLY|WHATSAPP_ONLY|BOTH), whatsappConsent, source (PUBLIC|ADMIN), registeredBy`
Attendance state, changed only inside the scan transaction:
`attendanceStatus (NONE|ACTIVE|COMPLETED), activeSessionId, sessionCount, totalDurationSeconds, firstCheckInAt, lastCheckOutAt`
Read: admin; organizer assigned to `eventId`. Never public.

There is no `qrTokenHash`: tickets are signed, not stored (see [ATTENDANCE_WORKFLOW.md](ATTENDANCE_WORKFLOW.md)).

## `registrationKeys/{eventId_hash}` — uniqueness
`registrationId, eventId, createdAt`. The id is `eventId` + SHA-256 of `eventId|normalizedEmail`. Created in the
registration transaction, so one email can hold one live registration per event. Backend only.

## `attendanceSessions/{sessionId}`
`id, eventId, registrationId, participantId, participantName, faculty, department, checkInAt, checkOutAt,
checkedInBy, checkedInByName, checkedOutBy, checkedOutByName, durationSeconds,
status (ACTIVE|COMPLETED|CORRECTED), corrected, method (QR|MANUAL), createdAt, updatedAt`
One document per visit; never replaced. Read: admin; organizer assigned to `eventId`.

## `eventOrganizers/{eventId_uid}`
`eventId, organizerId, organizerName, organizerEmail, assignedBy, assignedAt, active`
plus a copy of the event headline for "My events": `eventTitle, eventLocation, eventStartAt, eventEndAt, eventTimezone, eventStatus`.
Read: admin; the organizer themself.

## `notificationJobs/{jobId}` — the outbox
Id: `{registrationId}_v{ticketVersion}_{channel}_{kind}` (+ `_r{time}` for a manual resend).
`id, registrationId, eventId, eventTitle, participantName, channel (EMAIL|WHATSAPP),
kind (REGISTRATION_CONFIRMATION|TICKET_REPLACEMENT), recipient, ticketVersion,
status (PENDING|PROCESSING|SENT|DELIVERED|FAILED|RETRYING), provider, providerMessageId, attemptCount, lastError,
nextAttemptAt, leaseUntil, createdAt, updatedAt, sentAt, deliveredAt`
No secret and no ticket credential is stored. Read: admin only.

## `auditLogs/{logId}` — append-only
`actorId, actorName, action, entityType, entityId, eventId, reason, previousValue, newValue, details, createdAt`
Read: admin only.

## `ticketAccessRequests/{requestId}`
`keyId, registrationId, codeHash, attempts, used, createdAt, expiresAt`. One-time codes for ticket retrieval. Backend only.

## `settings/club`
`clubName, tagline, about, contactEmail, contactPhone, address, websiteUrl, instagramUrl, facebookUrl, linkedinUrl, attendanceGraceMinutes`
Read: public.

## Indexes
`firestore.indexes.json` covers every query the app and functions run. Storage is not used.
