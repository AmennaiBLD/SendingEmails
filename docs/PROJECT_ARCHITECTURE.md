# Project architecture — assessment at the start

## What existed

A fresh Kotlin Multiplatform wizard project: `:shared`, `:androidApp`, `:desktopApp`, `:webApp`, `iosApp/`,
with a "Click me" screen. No Firebase, no Koin, no navigation, no features.

| Piece | Found |
|---|---|
| Kotlin / Compose compiler | 2.4.21 |
| Compose Multiplatform | 1.12.2 (Material 3 1.12.0-alpha03) |
| AGP / Gradle | 9.1.1 / 9.5.1, daemon JDK 21 |
| Lifecycle | 2.11.0 |
| Targets | android, iosArm64, iosSimulatorArm64, jvm, js, wasmJs |

The conventions to follow came from `ARCHITECTURE_PATTERN.md` (copied into `docs/`) and from the sibling
TechStore project, which implements them with GitLive Firebase.

## What was reused

- `AppClock` and `mapDocuments` from TechStore. `RequestState` / `DisplayResult`, navigation, repositories and Koin
  follow the owner's own style (see ARCHITECTURE.md).
- The Gradle layout (AGP 9 KMP library plugin, JVM 17 in `:shared` and 11 in `:androidApp`, Firebase BoM on
  every Android classpath, `koin-core` as `api`).
- The generated-config approach for Firebase identifiers, extended to all four platforms.
- The iOS AVFoundation scanner structure and the share-sheet file export.

## What was added, and why

| Dependency | Version | Why |
|---|---|---|
| GitLive firebase-app/common/auth/firestore/functions | 2.4.0 | the backend SDK the pattern prescribes; `functions` because every write is a callable |
| firebase-java-sdk | 0.6.3 | desktop Firebase |
| Firebase Android BoM | 33.7.0 | pins the Android wrappers |
| Koin | 4.2.2 | DI |
| Navigation Compose | 2.10.0 | type-safe routes |
| kotlinx-datetime | 0.8.0 | event time zones |
| kotlinx-serialization plugin | = Kotlin | DTOs |
| material-icons-extended | 1.7.3 | icons |
| CameraX 1.4.2 + ML Kit barcode 17.3.0 | androidMain only | QR scanning |
| npm `@js-joda/timezone` 2.22.0 | jsMain | IANA zones for kotlinx-datetime in the browser |
| npm `jsqr` 1.4.0 | jsMain | QR decoding where the browser has no BarcodeDetector |

Nothing existing was upgraded. GitLive 2.4.0 (built with Kotlin 2.2) was verified to resolve and compile
with Kotlin 2.4.21 on all four targets before any feature code was written.

## What was removed

- The `wasmJs` target: GitLive publishes no wasm artifacts, so it would break `commonMain` resolution.
- The wizard's sample code and tests.

## Decisions made once

- **Rendering:** `DisplayResult` everywhere; a plain `when` only where a screen needs a different loading state.
- **No use cases:** ViewModels call repositories directly and validate with pure functions in `domain/`.
- **Language:** English, with every string in `composeResources/values/strings_<area>.xml`.
- **Single tenant:** one club, so there is no `storeId`. The session carries the user and role.
- **Writes:** all through Cloud Functions; the client has no write permission in the rules.
