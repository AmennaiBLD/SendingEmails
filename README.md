# Scientific Club — events, QR tickets and attendance

A Kotlin Multiplatform + Compose Multiplatform app for a university scientific club:

- **Public website** — browse events, register without an account, get a personal QR ticket.
- **Organizer tools** — sign in, pick an event, scan tickets to check people in and out.
- **Administration** — events, participants, organizers, attendance, analytics, notifications, reports, audit log.

One codebase runs on Android, iOS, desktop (JVM) and the web (Kotlin/JS). The backend is Firebase:
Auth and Firestore: the apps write directly and `firestore.rules` validates every write. Cloud Functions only send email/WhatsApp.

> **Status.** Verified against the Firebase Emulator Suite on web, with builds for all four targets.
> It has not been deployed to a real Firebase project, and no real email or WhatsApp message has
> been sent: those need your accounts. See [What still needs you](#what-still-needs-you).

## Required software

| Tool | Version used |
|---|---|
| JDK | 21 for Gradle (downloaded automatically by the foojay toolchain plugin) |
| Android Studio / Android SDK | compileSdk 37 |
| Xcode | 16.2+ (iOS only) |
| Node.js | 22 (Cloud Functions; 24 works locally with a warning) |
| Firebase CLI | 15+ (`npm i -g firebase-tools`) |
| Java | any JRE 11+ on the PATH, for the Firestore emulator |

## Project structure

```
shared/                 the whole app (commonMain) + platform seams
  commonMain/kotlin/com/example/checkinoutapp/
    core/               RequestState + DisplayResult, AppClock, error messages
    domain/             models, repository interfaces, pure logic (validation, analytics, CSV/PDF)
    data/               repository implementations (the only code that touches Firebase)
    feature/<name>/     one folder per screen: XScreen + XViewModel
    navigation/         Screen, SetupNavGraph
    ui/                 theme, components, formatting, platform seams (camera, file saving)
    di/                 Koin module
  androidMain · iosMain · jvmMain · jsMain    actuals only: Firebase init, camera, files, feedback
androidApp/ desktopApp/ webApp/ iosApp/       thin entry points
functions/              Cloud Functions (TypeScript): email/WhatsApp outbox, ticket code by email; rules tests
firestore.rules · firestore.indexes.json · firebase.json
docs/                   architecture, schema, API, workflows
```

The code style and layering are described in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Quick start with the emulator (no Firebase account needed)

```bash
cd functions && npm install && cd ..
```

```bash
cp firebase/app-config.emulator.json firebase/app-config.json
```

```bash
cp androidApp/google-services.emulator.json androidApp/google-services.json
```

```bash
cp functions/.secret.local.example functions/.secret.local
```

The values in it are only used by the email/WhatsApp functions. Then:

```bash
cd functions && npm run build && cd .. && firebase emulators:start --project demo-scientific-club
```

In a second terminal, create demo accounts and three events (emulator only; it refuses real projects):

```bash
cd functions && npm run seed
```

It prints the demo sign-ins: `admin@club.test` and `organizer@club.test`.
Emulator data is in memory: it is gone when the emulators stop, so seed again after a restart.

## Running each target

**Web** — http://localhost:8081 (port 8080 belongs to the Firestore emulator):

```bash
./gradlew :webApp:jsBrowserDevelopmentRun
```

**Desktop:**

```bash
./gradlew :desktopApp:run
```

**Android** — reads `androidApp/google-services.json` (`Firebase.initialize(context = this)` in `ClubApplication`).
An emulator reaches your machine's emulators at `10.0.2.2` (already in the config):

```bash
./gradlew :androidApp:installDebug
```

For a physical phone, set `androidEmulatorHost` in `firebase/app-config.json` to your computer's LAN
address. Debug builds allow plain HTTP for this; release builds do not.

**iOS** — open `iosApp/iosApp.xcodeproj` in Xcode and run. The project already references the
Firebase Swift package (FirebaseAuth, FirebaseFirestore, FirebaseFunctions); Xcode downloads it on
first open. The simulator build must be signed (ad-hoc is enough), because Firebase Auth uses the keychain.

Phones open on the staff sign-in; web and desktop open on the public site.

## Configure Firebase (a real project)

1. Create a Firebase project. Enable **Authentication → Email/Password** and **Firestore**.
2. Add a Web app, an Android app and an iOS app in the console.
3. Download the Android app's `google-services.json` into `androidApp/`.
4. Copy `firebase/app-config.example.json` to `firebase/app-config.json` and fill it from the Web
   app's config (used by web, desktop and iOS). Add `iosAppId`. Leave `emulatorHost` empty. Both files
   are gitignored; they hold public identifiers only, never a secret.
   Optionally add `iosApp/iosApp/GoogleService-Info.plist`: when present, iOS uses it instead.
5. Deploy rules and indexes:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project <your-project-id>
```

6. Set the secrets and deploy the functions (see below).
7. Create the first administrator (needs a service-account key on your machine, never committed):

```bash
cd functions && GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json npm run make-admin -- you@example.org 'a-strong-password' "Your Name"
```

Everyone else is created from **Administration → Organizers**.

## Deploy Cloud Functions

Plain settings go in `functions/.env` (template: [.env.example](.env.example)). Secrets go to Secret Manager:

```bash
firebase functions:secrets:set RESEND_API_KEY
```

Repeat for `RESEND_WEBHOOK_SECRET`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET`
and `WHATSAPP_WEBHOOK_VERIFY_TOKEN`. A provider you do not use still needs a value; `unused` is fine.

```bash
firebase deploy --only functions --project <your-project-id>
```

Functions are only needed to send email/WhatsApp; the app works without them (tickets are shown on screen and
can be saved). They need the Blaze plan. Keep `FUNCTIONS_REGION` (functions/.env) equal to `functionsRegion` in
firebase/app-config.json.

## Send ticket emails without Cloud Functions (GitHub Actions)

On the free Spark plan, `.github/workflows/send-emails.yml` sends the queued ticket emails every 10 minutes
through Resend (`functions/scripts/sendEmails.ts`, QR image and PDF attached). WhatsApp jobs stay queued.

1. Resend: verify your domain (or use the test sender, which only delivers to your own Resend address) and
   create an API key.
2. GitHub repository → Settings → Secrets and variables → Actions:
   - secrets: `FIREBASE_SERVICE_ACCOUNT` (the whole service-account JSON), `RESEND_API_KEY`
   - variables: `FIREBASE_PROJECT_ID`, `EMAIL_FROM` (e.g. `Scientific Club <tickets@your-domain.org>`)
3. Actions → "Send ticket emails" → Run workflow, to test at once.
4. Set `"emailDelivery": "true"` in `firebase/app-config.json` and rebuild the apps, so the ticket screen says
   a copy is on its way.

A private repository has 2,000 free Actions minutes a month: use `*/30` in the cron there. Public repositories
are free; no secret is in the code.

## Configure email (Resend)

1. Create a Resend account and verify your sending domain.
2. `firebase functions:secrets:set RESEND_API_KEY`
3. In `functions/.env`: `EMAIL_PROVIDER=resend` and `EMAIL_FROM="Club <tickets@your-domain>"`.
4. Optional delivery status: add a Resend webhook pointing at the deployed `emailWebhook` function URL
   and store its signing secret as `RESEND_WEBHOOK_SECRET`.

With `EMAIL_PROVIDER=mock` (the default) nothing is sent. The message is logged by the function and the
admin screen marks the job **Simulated**.

## Configure WhatsApp Business (Meta Cloud API)

This uses the official Cloud API only.

1. In Meta Business Manager create a WhatsApp Business Account and register a phone number.
2. Create and get approved a **template** (default name `event_ticket`, language `en`) with:
   - **Header:** Image
   - **Body**, five variables in this order — name, event, date, time, location:

```
Hello {{1}}! 👋

Your registration for {{2}} is confirmed!

📅 {{3}}
🕒 {{4}}
📍 {{5}}

Here is your personal event ticket.

Please present this QR code when entering
and leaving the event.

See you there! 🚀
Scientific Club Team
```

3. Create a system-user access token with `whatsapp_business_messaging`, then
   `firebase functions:secrets:set WHATSAPP_ACCESS_TOKEN`.
4. In `functions/.env`: `WHATSAPP_PROVIDER=meta`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_BUSINESS_ACCOUNT_ID`,
   `WHATSAPP_TICKET_TEMPLATE`, `WHATSAPP_TEMPLATE_LANGUAGE`, `WHATSAPP_API_VERSION`.
5. Delivery status: point the Meta webhook at the deployed `whatsappWebhook` URL, with the verify token you
   stored as `WHATSAPP_WEBHOOK_VERIFY_TOKEN` and the app secret as `WHATSAPP_APP_SECRET`.

The ticket image is uploaded and sent in the template's image header. WhatsApp is only used for
participants who ticked the consent box.

## Security rules

`firestore.rules` is the backend: it validates every write the apps make (fields, registration window, capacity,
duplicates, organizer assignment, server time). Roles come from `users/{uid}`, which only an active administrator writes.
The rules are tested in `functions/test/rules.test.ts` (`cd functions && npm test`). No Storage bucket is used, so there is no `storage.rules`.

## Tests

```bash
./gradlew :shared:jvmTest
```

33 tests: domain logic (availability, validation, durations, analytics, CSV/PDF), view models with fake
repositories, and a check that every view model resolves from Koin.

```bash
cd functions && npm test
```

31 tests against the Firestore emulator: registration races, duplicate and concurrent scans, corrections,
notification retries and idempotency, webhook signatures, and the security rules.

## Testing the QR scanner

1. Register on the web. The confirmation shows the QR ticket and a **Show ticket code** link.
2. Sign in as an organizer on a phone, open the event, keep **CHECK-IN** selected, scan the QR on the screen.
3. Select **CHECK-OUT** and scan the same QR.

Without a camera (desktop, or a browser that blocks it), paste the ticket code into **Ticket code**, or
use **Find participant**. On desktop a USB QR reader types straight into that field.
Browsers only allow the camera on `https://` or `localhost`.

Check-in is accepted from 3 hours before an event starts until 6 hours after it ends.

## Production builds

```bash
./gradlew :webApp:jsBrowserDistribution
```

Output: `webApp/build/dist/js/productionExecutable/` (`firebase deploy --only hosting` serves it; `*.map` files are excluded).

```bash
./gradlew :androidApp:assembleRelease
```

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

iOS: archive from Xcode. Android release signing, R8, desktop signing and App Store setup are not configured.

## What still needs you

- A real Firebase project, and a deploy of rules, indexes and functions.
- Resend and Meta WhatsApp credentials, and the approved WhatsApp template.
- The club's real logo and contact details (placeholders now; text is editable in **Settings**).
- A test on real Android and iOS devices: the camera scanners compile but have not been run on hardware.
- App Check or rate limiting on the public registration endpoint before going live.

More detail: [docs/](docs).
