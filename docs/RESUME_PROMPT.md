# Resume prompt — paste this after compacting

---

You are continuing work on **CheckInoutapp**, a Scientific Club platform: public event website, QR-ticket
registration, organizer check-in/check-out scanner, and admin dashboard with attendance analytics.

Project root: `/Users/sofian/AndroidStudioProjects/AndroidStudioProjects/Kmp_Cmp/build-using-claude/CheckInoutapp`

**Do not re-read the whole project.** Everything below is current. Open only the files the task touches.
The file map is at the end of this prompt.

## 1. Write code MY way (non-negotiable)

- **RequestState** (`core/RequestState.kt`): `Idle | Loading | Success(data) | Error(message)`, with
  `isSuccess()` / `isError()` / `isLoading()` / `isIdle()` as **functions**, `getSuccessData()`,
  `getSuccessDataOrNull()`, `getErrorMessage()`. `DisplayResult` lives in the same file.
- **Navigation**: `@Serializable sealed class Screen` (`navigation/Screen.kt`) and ONE `SetupNavGraph`
  (`navigation/SetupNavGraph.kt`). Each destination:
  `composable<Screen.X> { val id = it.toRoute<Screen.X>().id; XScreen(id = id, navigateBack = { navController.navigateUp() }, navigateToY = { navController.navigate(Screen.Y(it)) }) }`.
- **Repositories**: `interface XRepository` in `domain/repository/`, `class XRepositoryImpl` in `data/repository/`,
  one per file. Reads:
  `fun readXFlow(): Flow<RequestState<T>> = channelFlow { try { send(RequestState.Loading); Firebase.firestore.collection(collectionPath = "x")….snapshots.collectLatest { send(RequestState.Success(data = …)) } } catch (e: Exception) { send(RequestState.Error("Error while …: ${e.toUserMessage()}")) } }`.
  One-shot: `return try { … RequestState.Success(data = …) } catch (e: Exception) { RequestState.Error(…) }`.
- **Documents are read field by field**: `document.get(field = "title")`, in a private `xFrom(document)` in the Impl.
  Optional fields: `document.get<String?>(field = "x") ?: ""`. Timestamps: `document.get<Timestamp>(field = "startAt").toMillis()`.
  No `@Serializable` DTOs for Firestore documents (`data/dto/FunctionDtos.kt` is only for the two email callables).
* Writes: `Firebase.firestore.runTransaction { … }` / `batch()` with `set(ref, mapOf(…))` and `update(ref, "field" to value)`,
  instants as `millisTimestamp(nowMillis())`, an `auditLog(...)` entry in the same transaction (data/firebase/FirestoreWrites.kt).
- **Firebase is called directly**: `Firebase.auth`, `Firebase.firestore`, `Firebase.functions`.
  NO wrapper/runtime objects around Firebase (no `FirebaseRuntime`, no `isInitialized`, no `safeCall`).
- **Koin** (`di/SharedModule.kt`): `single<XRepository> { XRepositoryImpl() }` + `viewModelOf(::XViewModel)`,
  `expect val targetModule: Module`, `fun initializeKoin(config: (KoinApplication.() -> Unit)? = null)`.
  No use-case layer: ViewModels call repositories and validate with pure functions in `domain/`.
- **Screens**: named `XScreen(id, navigateBack, navigateToY)`. The ViewModel is taken INSIDE the body:
  `val viewModel = koinViewModel<XViewModel>()`, then `val x by viewModel.x.collectAsState()`.
  A ViewModel that needs the route id reads `savedStateHandle.get<String>("id")`.
- **Android start-up** (`androidApp/.../ClubApplication.kt`):
  `initializeKoin(config = { androidContext(this@ClubApplication) })` then `Firebase.initialize(context = this)`.
- **App()** (`App.kt`): `koinInject<AuthRepository>()`, `getCurrentUserId()` picks `startDestination`, then `SetupNavGraph`.
- **Add `@Preview`** composables for new UI. Sample data goes in `ui/preview/PreviewData.kt`.
- **Font**: BBH Bogle everywhere (`composeResources/font/bbh_bogle_regular.ttf`, applied in `ui/theme/Theme.kt`).
  One weight only; basic Latin only (no é, ç, Arabic, ✓, →).
- Ask me before adding a dependency. Never invent versions; versions live in `gradle/libs.versions.toml`.

## 2. Stack (verified building together)

Kotlin 2.4.21 · Compose Multiplatform 1.12.2 (Material3 1.12.0-alpha03) · AGP 9.1.1 · Gradle 9.5.1 (JDK 21) ·
compileSdk 37 / minSdk 24 · GitLive Firebase 2.4.0 (app, auth, firestore, functions) + Firebase BoM 33.7.0 ·
firebase-java-sdk 0.6.3 (desktop) · Koin 4.2.2 · Navigation 2.10.0 · Lifecycle 2.11.0 · kotlinx-datetime 0.8.0 ·
CameraX 1.4.2 + ML Kit barcode 17.3.0 (androidMain) · npm `@js-joda/timezone` 2.22.0 + `jsqr` 1.4.0 (jsMain) ·
google-services plugin 4.4.2.

Targets: android, iosArm64, iosSimulatorArm64, jvm (desktop), js (web). **No wasmJs** (GitLive has no wasm).
JVM target 17 in `:shared`, 11 in `:androidApp`. Generated resources package: `com.example.checkinoutapp.resources`.

Backend: none for data — the apps write Firestore directly and `firestore.rules` validates every write. Cloud Functions in
`functions/` (TypeScript, Node 22) ONLY send email/WhatsApp (outbox worker, retry, ticket code by email, webhooks).
qrose 1.3.1 (`io.github.alexzhirkevich:qrose`) draws the ticket QR on the device.

## 3. Architecture in one paragraph

NO Cloud Functions for data: the repositories write Firestore directly in transactions, and `firestore.rules` IS the
backend (field whitelists, registration window, capacity, one key per (event, email), organizer assignment, device
time within 5 min of `request.time`). Rules tests: `functions/test/rules.test.ts`. Roles live in `users/{uid}` (only an
active ADMIN may write it). QR ticket = `SC2.<registrationId>.<ticketVersion>.<secret>` (domain/ticket/TicketCode.kt): a
64-hex random secret stored on the registration (staff-readable only); replacing bumps the version and the secret.
Registration = one transaction: registration + `registrationKeys/<eventId>_<email>` + `events.registrationCount`/
`lastRegistrationId` + `notificationJobs` (PENDING). Check-in/out = one transaction over the registration
(`activeSessionId`, `sessionCount`) + session + event counters + audit. Staff accounts are created through a second
Firebase app (`staffAccountsApp()`), so the admin stays signed in. Notifications: the outbox jobs wait for the email
sender (Cloud Functions, needs Blaze); until then nothing is sent and the ticket screen says "save this ticket".
Analytics are computed in shared Kotlin (`domain/analytics`), not in a function.

Session: `SessionManager` → `SessionState` (`Unknown | SignedOut | Resolving | NoAccess | Active(user) | Failed`).
`AuthScreen` routes by role: ADMIN → `Screen.AdminPanel`, ORGANIZER → `Screen.OrganizerHome`.

Screens (`Screen`): `Home`, `Events`, `EventDetails(id)`, `Registration(id)`, `FindTicket(id)`, `Auth`,
`OrganizerHome`, `EventWorkspace(id, title)`, `AdminPanel` (sections inside: overview, events, participants,
organizers, attendance, analytics, notifications, reports, audit log, settings).

## 4. Firebase configuration — current state

- Real project: **`gdgcheckin-outdemo`** (`.firebaserc` default). Firestore `(default)` is in **europe-west1**.
- `androidApp/google-services.json` → Android app `com.example.checkinoutapp`.
- `firebase/app-config.json` → the real **Web** app (`CheckInout Web`), used by web and desktop. `emulatorHost` is
  empty, so `connectFirebaseEmulators()` does nothing. To go back to the emulator, copy
  `firebase/app-config.emulator.json` over it.
- `functionsRegion: europe-west1` in app-config → generated `FirebaseAppConfig.FUNCTIONS_REGION`. Every callable is
  `Firebase.functions(region = FirebaseAppConfig.FUNCTIONS_REGION)`. Backend region: `FUNCTIONS_REGION` in
  `functions/.env` (and `.env.local` for the emulator). Keep the three equal.
- Deployed: Firestore rules + indexes. Data: all 10 collections written by `cd functions && GOOGLE_APPLICATION_CREDENTIALS=~/keys/gdgcheckin-outdemo.json npm run init-project`
  (functions/scripts/initProject.ts; 3 events, staff admin@club.test + organizer@club.test, 6 registrations, attendance, audit).
- `functions/.project.local` (gitignored, never deployed): project id and the demo staff passwords.
- New rules deployed; existing data migrated (`npm run migrate`: ticket secrets + new key ids).
- Service-account key: `~/keys/gdgcheckin-outdemo.json` (outside the repo, chmod 600).
- Email WITHOUT Blaze: `.github/workflows/send-emails.yml` (every 10 min) runs `functions/scripts/sendEmails.ts`
  (Resend, EMAIL jobs only; WhatsApp jobs stay PENDING). Needs a GitHub repo + secrets FIREBASE_SERVICE_ACCOUNT,
  RESEND_API_KEY and variables FIREBASE_PROJECT_ID, EMAIL_FROM. `emailDelivery`/`whatsappDelivery` in app-config.json
  ("true"/"false") decide whether the ticket screen says a copy is on its way.
- Not done yet: WhatsApp, ticket retrieval by email code (still a callable function → needs Blaze), iOS app + `GoogleService-Info.plist` (`iosAppId` empty, so iOS skips Firebase).

## 5. Commands

```bash
./gradlew :shared:jvmTest                    # 33 shared tests
cd functions && npm test                      # 31 backend + rules tests (starts its own Firestore emulator)
firebase emulators:start --project demo-scientific-club   # local backend (ports 8080, 9099, 5001, UI 4000)
cd functions && npm run seed                  # demo data — admin@club.test / Admin#12345, organizer@club.test / Organizer#12345
./gradlew :webApp:jsBrowserDevelopmentRun     # http://localhost:8081
./gradlew :desktopApp:run
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64   # Xcode project: iosApp/iosApp.xcodeproj (Firebase SwiftPM linked)
```

After any change, build every target: `:shared:jvmTest :shared:compileKotlinJs :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :desktopApp:compileKotlin`.

## 6. Status

Done and verified (web, against the emulator): public site, registration with signed QR ticket, ticket retrieval
by email code, organizer sign-in, check-in and check-out with the same QR, attendance records, admin dashboard,
analytics (average, median, incomplete sessions), participants, notifications. All four targets build.

Not done / not verified:
- Real Firebase project not deployed; no real email or WhatsApp sent (mock providers).
- Camera scanners (Android/iOS/browser) compile but were never run on a real device.
- CSV/PDF export not clicked in the UI (content is unit-tested).
- Cover images, waitlist, reminders/cancellation messages, participant accounts, offline scanning,
  deep-link URLs on web, languages other than English, App Check/rate limiting.
- After the `document.get` change, attendance records, notifications and audit log were not re-checked in the browser.

In progress (my own edits): Google sign-in with KMPAuth 3.0.6 (`libs.auth.kmp`). `App.kt` calls
`GoogleAuthProvider.create(credentials = GoogleAuthCredentials(serverId = " "))`; the serverId is still a blank
placeholder (it must be the Web client id from google-services.json `default_web_client_id`).
The google-services plugin is applied ONLY in `androidApp/build.gradle.kts` (never in `:shared`, or Android starts
with "Default FirebaseApp failed to initialize because no default options were found").

## 7. Known traps

- Web: GitLive's `Firebase.firestore` makes a new wrapper per call in JS; settings/emulator only apply to the
  instance created with them. `jsMain/.../firebase/InitializeFirebase.js.kt` creates it once at start-up. Keep that.
- Web: Compose resources load strings on first use; `App()` warms them after 3 s.
- Web dev server is on 8081 because the Firestore emulator owns 8080.
- iOS: `FirebaseApp.configure()` must never run twice. Simulator builds must be code-signed (ad-hoc is fine).
- Kotlin/JS `Long` is not a JS number: send instants to functions as `Double`; read counters as `Int`.

## 8. File map (`shared/src/commonMain/kotlin/com/example/checkinoutapp/`)

- `App.kt`
- `core/`: AppClock, ErrorMessages (`toUserMessage()`), RequestState (+ DisplayResult)
- `domain/model/`: Attendance, Events, Notifications, Registrations, Users
- `domain/repository/`: Attendance, Audit, Auth, Event, Notification, Organizer, Registration, Settings, User (+ outcome enums)
- `domain/`: `analytics/` (EventAnalytics, ClubAnalytics), `attendance/Durations`, `event/` (EventAvailability, EventValidation),
  `registration/RegistrationValidator`, `report/` (CsvExport, EventReportPdf, SimplePdf), `session/SessionManager`, `time/EventTime`
- `data/repository/`: the nine `XRepositoryImpl`. `data/dto/FunctionDtos.kt`. `data/firebase/FirestoreHelpers.kt` (Timestamp ↔ millis)
- `feature/`: admin (AdminPanelScreen, AdminCommon, EventSelection), adminDashboard, analytics, attendance, auditLog, auth,
  eventDetails, eventManagement, events, home, notifications, organizerHome, organizerManagement, participantManagement,
  qrScanner (EventWorkspace, QrScanner), registration, reports, settings, ticket (FindTicket, TicketCard)
- `navigation/`: Screen, SetupNavGraph · `di/SharedModule` · `firebase/InitializeFirebase` (emulator hook)
- `ui/`: components (Basics, Charts, EventCard, Inputs, PublicChrome, Tables), format (Formats, Labels),
  platform (Platform: FileSaver, ScanFeedback, QrCameraView), preview/PreviewData, theme (Theme, WidthClass)
- Strings: `composeResources/values/strings_{common,public,staff,admin}.xml` — every visible string goes there.
- Platform seams: `{androidMain,iosMain,jsMain,jvmMain}/…/ui/platform/Platform.*.kt`, `…/di/TargetModule.*.kt`,
  `{jsMain,jvmMain,iosMain}/…/firebase/InitializeFirebase.*.kt`, `jsMain/…/domain/time/TimeZoneDatabase.js.kt`.
- Backend: `functions/src/{registration,attendance,events,users,ticket,notifications,shared}/`, `functions/src/index.ts`,
  tests in `functions/test/`. Rules: `firestore.rules`, `firestore.indexes.json`, `firebase.json`.
- Docs: `README.md`, `docs/ARCHITECTURE.md`, `FIREBASE_SCHEMA.md`, `API_AND_FUNCTIONS.md`, `ATTENDANCE_WORKFLOW.md`,
  `NOTIFICATIONS.md`, `ANALYTICS.md`, `IMPLEMENTATION_PLAN.md`.

## 9. My next request

(write it here)

---
