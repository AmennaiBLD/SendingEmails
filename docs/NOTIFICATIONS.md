# Notifications

```
registration transaction ──► notificationJobs (one per channel, PENDING)
                                   │ onDocumentCreated            every 5 min (due retries, dead workers)
                                   ▼                                        │
                          processNotificationJob ◄─────────────────────────┘
                                   │ claim in a transaction (PENDING/RETRYING → PROCESSING, lease 2 min)
                                   │ re-create the ticket from the signed id + version (nothing secret was stored)
                                   ▼
                     EmailProvider / WhatsAppProvider
                                   │
              SENT ── webhook ──► DELIVERED          retryable error → RETRYING (1, 2, 4, 8 min; 5 attempts)
                                                     permanent error → FAILED
```

- Jobs are written in the same transaction as the registration, so there is never a registration without its
  jobs, and a provider outage cannot undo a registration.
- Channels are independent documents. An email failure does not touch the WhatsApp job.
- **No duplicates:** deterministic job ids; a transactional claim so concurrent workers send once; the job id
  as Resend's `Idempotency-Key`; `SENT`/`DELIVERED`/`FAILED` jobs are never reprocessed unless an admin retries.
- A job for a cancelled registration or a superseded ticket version fails permanently with that reason.

## Providers

| Interface | Implementations |
|---|---|
| `EmailProvider` | `ResendEmailProvider`, `MockEmailProvider` |
| `WhatsAppProvider` | `MetaWhatsAppCloudProvider` (official Cloud API), `MockWhatsAppProvider` |

Chosen by `EMAIL_PROVIDER` and `WHATSAPP_PROVIDER`. Mock providers send nothing and log the message; the job's
`provider` is `mock` and the admin screen labels it **Simulated**. For testing failures, a mock recipient
containing `+fail` fails permanently and `+flaky` fails temporarily.

**Email:** subject "Your registration is confirmed! 🎉", responsive HTML from `notifications/templates.ts`, the QR
inline (`cid:ticket-qr`) plus `ticket.png` and `ticket.pdf` attached. Templates exist for confirmation,
replacement, access code, reminder and cancellation; the last two are not sent by anything yet.

**WhatsApp:** only with the participant's consent. The ticket PNG is uploaded to the Cloud API and sent in the
approved template's image header, with the five body variables. Template text: see the README.

## Admin

**Notifications** lists jobs by status with provider, message id, attempts and last error, and can retry one.
**Participants → Resend ticket / Issue new ticket** queues new jobs.

## Not verified
Resend and Meta calls follow their documented APIs but have not been run against the real services.
