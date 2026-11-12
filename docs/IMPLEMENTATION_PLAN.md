# Implementation plan and status

| Phase | Scope | Status |
|---|---|---|
| 0 Discovery | repository assessment, dependency baseline on four targets | done |
| 1 Foundation | core, Firebase wiring, models, session, Koin, theme, navigation, rules | done |
| 2 Events | public list and details, admin create/edit/status, capacity, organizer assignment | done |
| 3 Registration and tickets | public form, duplicate and capacity safety, signed QR, confirmation, retrieval by email code | done |
| 4 Email and WhatsApp | outbox, retries, Resend and Meta providers, mocks, webhooks, admin resend/retry | done; real providers untested (no credentials) |
| 5 Organizer app | sign-in, my events, scanner (Android, iOS, browser), manual lookup, records | done; cameras not run on real devices |
| 6 Time tracking | sessions, server timestamps, re-entry, corrections with audit | done |
| 7 Analytics | event and club analytics, charts, CSV, PDF | done |
| 8 Administration | participants, organizers, notifications, reports, audit log, settings | done |
| 9 Hardening | tests, docs, builds on all targets | done; see "Not done" |

## Not done

- **Cover images.** The model keeps `coverImageUrl`, but the app shows generated covers; there is no image
  loader or upload. Adding one means a new dependency (an image loader) and Storage rules.
- **Waitlist.** `WAITLISTED` exists in the model; a full event simply refuses registration.
- **Reminder, cancellation and certificate messages.** Reminder and cancellation email templates exist;
  nothing sends them yet.
- **Linking guest registrations to accounts.** `participantId` is stored as null; participants do not sign in.
- **Offline scanning.** Online only. A failed call records nothing and says so.
- **Google sign-in, password reset UI.** Staff use email and password; an administrator creates accounts.
- **Browser URLs.** The web app does not put the route in the address bar, so pages cannot be deep-linked.
- **Languages.** English only. Strings are in resources, ready for more.
- **Rate limiting / App Check** on the public callables.
- **UI tests.** Logic is covered by unit tests; there are no Compose UI tests.
- **Android/iOS on real hardware, release signing, R8.**
