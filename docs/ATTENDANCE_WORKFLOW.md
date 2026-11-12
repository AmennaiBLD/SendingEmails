# Attendance: tickets, check-in, check-out

## The ticket

The QR holds `SC2.<registrationId>.<ticketVersion>.<secret>` (shared/.../domain/ticket/TicketCode.kt).

- `registrationId` is a random Firestore id. There is no name, email or phone in the code.
- `secret` is 64 random hex characters made on the registering device and stored on the registration, which only
  administrators and the event's assigned organizers can read (`firestore.rules`). A scan compares it in constant time.
- A replacement writes a new secret and increments `ticketVersion`. Older QR codes then answer `TICKET_REVOKED`.
- The app draws the QR itself (qrose); "Download ticket" saves it as a PNG.

A code that is not in this format, or whose secret does not match, is `INVALID_QR`.

## One QR, two modes

The organizer chooses **CHECK-IN** or **CHECK-OUT**. The mode never changes by itself.

```
scan → AttendanceRepositoryImpl.scan (one Firestore transaction, checked by firestore.rules)
     → caller's role from users/{uid}; organizer must be actively assigned to the event
     → signature check
     → ONE transaction: read registration + event
          not this event → EVENT_MISMATCH      old version → TICKET_REVOKED      cancelled → REGISTRATION_CANCELLED
        CHECK-IN:  open session exists → ALREADY_CHECKED_IN
                   closed session and re-entry off → ALREADY_CHECKED_OUT
                   else create session (ACTIVE), set registration.activeSessionId
        CHECK-OUT: no open session → NOT_CHECKED_IN / ALREADY_CHECKED_OUT
                   else close it: checkOutAt, durationSeconds = floor((out − in) / 1000)
     → audit entry, event counters
```

The attendance state lives on the registration document, which every scan transaction reads and writes.
Firestore retries a transaction whose reads changed, so of eight simultaneous scans of one ticket exactly one
wins and the rest get `ALREADY_CHECKED_IN` (tested in `attendance.test.ts`).

Time is the Cloud Functions server clock. The device's clock is never used.

**Windows.** Check-in: event `PUBLISHED`, from 3 h before start to 6 h after end. Check-out: any time while a
session is open, on a `PUBLISHED` or `COMPLETED` event, so nobody is left "inside" because the event ended.

## Re-entry

`allowReentry = false` (default): one session per registration. `true`: each visit is a new session;
`totalDurationSeconds` on the registration is the sum of the completed ones.

## In the app

- The camera reports the same code many times a second. The ViewModel submits it once: scans are ignored while
  a request is running, and the same code is ignored for 4 seconds after (a mode change resets this).
- A failed call shows "Nothing was recorded: scan again". It is never shown as a check-in.
- No camera: type or paste the ticket code, or use **Find participant** (which sends the registration id; the
  same transaction runs, and the session is marked `MANUAL`).

## Corrections

`correctAttendance` changes a session's times, or closes a forgotten check-out. It needs a reason. The session
becomes `CORRECTED`, totals and counters are adjusted by the difference, and an audit entry records who, when,
why, and the values before and after. Nothing is overwritten silently.

## Offline

Not supported. Adding it needs a signed event snapshot on the device, a durable local queue and server-side
conflict resolution. Until then a scan without a connection records nothing and the screen says so.
