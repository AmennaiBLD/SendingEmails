# Architecture

The code follows the project owner's own style (below). Where that style and
[ARCHITECTURE_PATTERN.md](ARCHITECTURE_PATTERN.md) disagree, the owner's style wins.

```
XScreen (Compose) → XViewModel → XRepository (interface) → XRepositoryImpl → GitLive Firebase
                                                                               │
                                               reads: Firestore listeners ─────┤
                                               writes: Firestore + rules         ┘
```

## The house style

- **`RequestState`** (`core/RequestState.kt`): `Idle | Loading | Success(data) | Error(message)` with
  `isSuccess()`, `isError()`, `getSuccessData()`, `getSuccessDataOrNull()`, `getErrorMessage()`.
  `DisplayResult` is in the same file: optional `onIdle` / `onLoading` / `onError`, required `onSuccess`.
- **Navigation** (`navigation/`): one `@Serializable sealed class Screen` and one `SetupNavGraph`. Every
  destination is `composable<Screen.X> { XScreen(navigateBack = { navController.navigateUp() }, navigateToY = { … }) }`.
  Arguments are read with `it.toRoute<Screen.X>().id` and passed to the screen.
- **Repositories**: one `XRepository` interface (`domain/repository/`) and one `XRepositoryImpl`
  (`data/repository/`) per file. Reads are `fun readXFlow(): Flow<RequestState<T>> = channelFlow { try { …
  .snapshots.collectLatest { send(RequestState.Success(data = …)) } } catch (e: Exception) { send(RequestState.Error(…)) } }`.
  One-shot calls are `return try { … RequestState.Success(data = …) } catch (e: Exception) { RequestState.Error(…) }`.
- **Koin** (`di/SharedModule.kt`): `single<XRepository> { XRepositoryImpl() }` and `viewModelOf(::XViewModel)`,
  `expect val targetModule`, `initializeKoin(config)`.
- **Screens**: `XScreen(id, navigateBack, navigateToY)`; the ViewModel is taken inside the body with
  `val viewModel = koinViewModel<XViewModel>()`. A ViewModel that needs the route's id reads it from
  `SavedStateHandle` (`savedStateHandle.get<String>("id")`), which is what lets `viewModelOf` create it.

There is no use-case layer: a ViewModel validates with the pure functions in `domain/` and calls its repository.

## Layers

| Layer | Folder | Notes |
|---|---|---|
| Core | `core/` | `RequestState` + `DisplayResult`, `AppClock`, `toUserMessage()` |
| Domain | `domain/` | models, repository interfaces with outcome types, pure logic (validation, analytics, CSV/PDF), `SessionManager` |
| Data | `data/` | repository implementations; `data/dto/` holds only the request/response classes of the two email callables |
| Features | `feature/<name>/` | `XScreen` and `XViewModel`; dialogs observe nullable form state; toasts are `Channel` events |
| Navigation | `navigation/` | `Screen`, `SetupNavGraph` |
| UI | `ui/` | theme, components, formatting and labels, platform seams |
| DI | `di/SharedModule.kt` | repositories and ViewModels |

One small departure from the owner's examples: `DisplayResult` passes `contentKey = { it::class }` to
`AnimatedContent`. The lists here are live, so without it the scale-and-fade would replay every time a
registration or scan arrives.

Documents are read field by field, `document.get(field = "title")`, in a private `xFrom(document)` function in
each `XRepositoryImpl`. Firestore `Timestamp`s become epoch milliseconds there. A document that cannot be read
is skipped with a log line, so one bad document does not empty a list.

## Start-up

- **Android** (`ClubApplication`): `initializeKoin(config = { androidContext(this) })`, then `Firebase.initialize(context = this)`
  (which reads `google-services.json`), then `connectFirebaseEmulators(androidEmulator = true)`.
- **Web, desktop, iOS**: `initializeKoin()`, then `initializeFirebase()`, a plain function in that platform's source set
  that builds `FirebaseOptions` from the generated `firebase/app-config.json` values.
- `App()` asks `AuthRepository.getCurrentUserId()` and starts on `Screen.Auth` for signed-in staff and on phones,
  otherwise on `Screen.Home`.

`connectFirebaseEmulators()` does nothing unless the config names an emulator host.

## Writes go straight to Firestore; the rules are the backend

The repositories write Firestore directly, in transactions (`runTransaction`/`batch`), and `firestore.rules`
checks every write: field whitelists, the registration window and capacity, one `registrationKeys/<eventId>_<email>`
per person, organizer assignment, roles from `users/{uid}`, and device time within five minutes of `request.time`.
`functions/test/rules.test.ts` proves it. A second identical request still returns a named outcome
(`ALREADY_REGISTERED`, `ALREADY_CHECKED_IN`), never a duplicate, because the transaction re-reads the state.

Tickets: `SC2.<registrationId>.<version>.<secret>`; the 64-hex secret lives on the registration (staff-readable only).
The app draws the QR with qrose. Cloud Functions are only used to send email/WhatsApp from the `notificationJobs` outbox.

## Session

`SessionManager` builds `SessionState` from the auth listener and `users/{uid}`:
`Unknown → SignedOut | Resolving → NoAccess | Active(user) | Failed`.
The public website needs no session. `Screen.Auth` moves on by role once the session is active, and the staff
screens return to it when the session ends.
The role only decides which screens appear. The rules check it again from `users/{uid}`.

## Platform seams (`expect`/`actual`)

| Seam | Android | iOS | Web | Desktop |
|---|---|---|---|---|
| `QrCameraView` | CameraX + ML Kit | AVFoundation | `<video>` + BarcodeDetector / jsQR | none (code entry) |
| `FileSaver` | Downloads | share sheet | browser download | save dialog |
| `ScanFeedback` | vibration + tone | haptic + sound | vibration | beep on failure |
| `ensureTimeZoneDatabase` | — | — | loads `@js-joda/timezone` | — |

Two things learned on the web target:

- GitLive's `Firebase.firestore` builds a new wrapper on each call in JS, and settings or the emulator are only
  honoured on the instance created with them. The web `initializeFirebase()` therefore creates that instance once at
  start-up; every later `Firebase.firestore` is the same underlying instance. (Without this the web app silently
  talked to the production endpoint instead of the emulator.)
- Compose resources load each string on first use in the browser; `App()` warms them shortly after start-up.

## Bounded reads

Every listener has a filter and a limit (events 200, registrations per event 500–5000, sessions per event
500–5000, club session window 180 days / 3000). Club-wide totals come from counters the backend keeps on
each event document, not from reading every session.
