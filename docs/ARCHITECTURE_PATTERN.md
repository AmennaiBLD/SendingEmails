# Architecture pattern + dependency playbook — MVVM + Repository + `RequestState` (Kotlin Multiplatform / Compose Multiplatform)

Paste this file into the new project (e.g. `docs/ARCHITECTURE_PATTERN.md`) and tell the assistant or developer:
**"Follow this pattern exactly (Part 1). Use exactly these dependencies and versions (Part 2). Every new feature is built
the way section 12 says."**

- **Part 1 (sections 1–14): the architecture** — layers, `RequestState`, ViewModels, how the UI displays the data.
- **Part 2 (sections 15–23): the dependency playbook** — every version, every Gradle file, the traps that cost time here,
  and the procedure for changing a dependency safely.

It describes the pattern TechStore uses today. Every rule below is taken from code that exists and works. File names
are TechStore's and are shown so you can see the real example; the pattern itself is independent of the product.

> **How to read the examples.** The running example is TechStore's *Flexy* feature
> (`feature/flexy/`, `domain/usecase/RechargeUseCases.kt`, `data/repository/RechargeRepositoryImpl.kt`). In the new
> app, replace `Recharge…`/`Flexy…` with your own feature name, and `storeId` with your own tenant id (or delete it if
> the app is single-tenant).

---

## 1. The one-paragraph version

```
UI (Compose)  →  ViewModel  →  UseCase (writes only)  →  Repository (interface)  →  RepositoryImpl  →  Firebase
   renders          holds state     validates + decides       what the app needs        the ONLY code       (via one helper
   state,           for one         and returns a typed       (domain types, no         that knows the      object)
   sends actions    screen          Result                    Firebase)                 backend
```

- **Data flows up** as `Flow<RequestState<T>>` → `StateFlow<RequestState<T>>` → the screen renders it.
- **Actions flow down** as plain function calls: `viewModel.save()` → `useCase(...)` → `repository.record(...)`.
- **The UI never touches the backend.** A ViewModel never imports Firebase. Only `data/repository/*Impl` does.
- **Business rules live in `domain/`** as plain Kotlin (no Compose, no Firebase), so they are unit-testable on the JVM.

---

## 2. Folder structure (single `shared` module, `commonMain`)

```
shared/src/
  commonMain/kotlin/<package>/
    App.kt                        shared root composable (theme, language, then SessionGate)
    core/                         tiny cross-cutting building blocks
      RequestState.kt             the one generic load-state type
      SafeCall.kt                 safeCall { } + Flow.asRequestState() + Throwable.toUserMessage()
      DisplayResult.kt            optional composable that renders a RequestState
      Money.kt, AppClock.kt, TimeWindow.kt, FirestorePaths.kt …
    domain/                       PURE Kotlin. No Compose. No Firebase.
      model/                      domain models (Sale, Device, RechargeOperation …) + enums
      repository/                 repository INTERFACES + their outcome types
      usecase/                    one class per write operation (+ its sealed Result)
      <area>/                     pure logic: summaries, grouping, filters, validators …
      session/                    SessionManager + SessionState (who is signed in, which store, which role)
    data/                         the ONLY place that knows Firebase
      dto/                        @Serializable wire shapes + toDomain()/from(domain)
      repository/                 *RepositoryImpl
      firebase/                   StoreCollections (paths + transaction helper), number helpers
    feature/<name>/               one folder per screen family
      <Name>ViewModel.kt          state + actions + events
      <Name>.kt                   the screen composable(s)
      <Name>Dialogs.kt            dialogs, observing the ViewModel
    navigation/                   Route (type-safe), NavGraph, SessionGate, AppShell
    ui/                           design system: theme, components, locale
    di/                           SharedModule.kt (+ expect val targetModule)
  commonTest/                     unit tests for domain, use cases, ViewModels (fake repositories)
  androidMain / iosMain / jvmMain / jsMain/   ONLY platform seams (expect/actual) — nothing else
```

**Dependency rule (never broken):**

```
feature/ ──▶ domain/ ◀── data/
   │                        │
   └────────▶ core/ ◀───────┘          domain never imports feature or data
```

- `feature` depends on `domain` (interfaces, models, use cases). It never imports a `*Impl` or a DTO.
- `data` depends on `domain` (it implements its interfaces). It never imports `feature`.
- `domain` imports neither. It may use `core`.

---

## 3. `RequestState` — the single load-state type

`core/RequestState.kt`:

```kotlin
sealed class RequestState<out T> {
    data object Idle : RequestState<Nothing>()
    data object Loading : RequestState<Nothing>()
    data class Success<out T>(val data: T) : RequestState<T>()
    data class Error(val message: String) : RequestState<Nothing>()

    val isIdle / isLoading / isSuccess / isError: Boolean
    fun getSuccessDataOrNull(): T?
    fun getSuccessDataOrElse(default: T): T
    fun getErrorMessageOrNull(): String?
    fun <R> map(transform: (T) -> R): RequestState<R>      // Idle/Loading/Error pass through untouched
}
```

**What `RequestState` is for — and what it is not:**

| Use it for | Do NOT use it for |
|---|---|
| Anything that comes **from the backend**: a live list, a document, a one-shot read or write | **Local workflow state** — a form being filled, a cart, a scanner, a dialog. Those are their own `data class`es (`FlexyForm`, `FlexyVoidDraft`, `ScannerUiState`) |

**Meaning of the four states:**

| State | Meaning | What the screen shows |
|---|---|---|
| `Idle` | nothing requested yet | usually the same as Loading |
| `Loading` | request in flight / first snapshot not here yet | spinner |
| `Success(data)` | data is here (may be an empty list) | the content, or the *empty state* if the data is empty |
| `Error(message)` | failed; `message` is already human-readable | an error notice (with Retry where it makes sense) |

**Rule: "empty" is `Success(emptyList())`, never `Error`.** The screen decides how to draw an empty list.

**`map` is how a ViewModel turns repository data into screen data without losing the load state:**

```kotlin
repository.observeOperationsBetween(storeId, from, to)          // Flow<RequestState<List<RechargeOperation>>>
    .map { state -> state.map { ops -> FlexyToday(summarize(ops), recent(ops)) } }   // RequestState<FlexyToday>
```

---

## 4. The data layer

### 4.1 Repository interface (in `domain/repository/`)

The interface speaks only domain types and `RequestState`. It documents the *contract* (bounded reads, what is atomic,
what needs the server).

```kotlin
interface RechargeRepository {
    // LIVE read → Flow<RequestState<…>>
    fun observeOperationsBetween(storeId: String, since: Long, until: Long): Flow<RequestState<List<RechargeOperation>>>

    // ONE-SHOT write → suspend … RequestState<Outcome>
    suspend fun record(operation: RechargeOperation): RequestState<RechargeSaveOutcome>
    suspend fun void(storeId: String, operationId: String, /* … */): RequestState<RechargeVoidOutcome>
}

// An "outcome" is a sealed type for the business results that are NOT errors:
sealed interface RechargeSaveOutcome {
    data class Saved(val operation: RechargeOperation) : RechargeSaveOutcome
    data class AlreadySaved(val operation: RechargeOperation) : RechargeSaveOutcome   // idempotent retry
}
```

**Conventions**
- Live data → `fun observeX(...): Flow<RequestState<T>>` (not `suspend`).
- One-shot work → `suspend fun doX(...): RequestState<Outcome>`.
- Business results that are not failures (`AlreadySaved`, `NotFound`, `Conflict`) are **outcome types**, not exceptions
  and not `Error`. `Error` means "the call itself failed" (network, permission, server).
- **No "observe everything" methods** on large collections. Reads are bounded by a window, a limit or a filter. (This was
  a real cost lesson: unbounded listeners are what makes Firestore bills grow.)

### 4.2 Repository implementation (in `data/repository/`) — the two helpers

`core/SafeCall.kt` gives every repository the same error behaviour:

```kotlin
// ONE-SHOT: runs the block, returns Success / Error. Never throws (except cancellation).
suspend fun <T> safeCall(block: suspend () -> T): RequestState<T> {
    if (!FirebaseRuntime.isInitialized) return RequestState.Error(FIREBASE_UNAVAILABLE)
    return try { RequestState.Success(block()) }
    catch (c: CancellationException) { throw c }                    // NEVER swallow cancellation
    catch (t: Throwable) { RequestState.Error(t.toUserMessage()) }
}

// LIVE: wraps a stream. Emits Loading first, maps each value to Success, turns a failure into Error.
fun <T> Flow<T>.asRequestState(): Flow<RequestState<T>> =
    map<T, RequestState<T>> { RequestState.Success(it) }
        .onStart { emit(RequestState.Loading) }
        .catch { if (it is CancellationException) throw it; emit(RequestState.Error(it.toUserMessage())) }

// Turns raw exceptions into messages a shop employee can act on
// ("PERMISSION_DENIED" → "You do not have permission to do this.", "UNAVAILABLE" → "No connection …").
fun Throwable.toUserMessage(): String
```

A typical implementation:

```kotlin
class RechargeRepositoryImpl : RechargeRepository {

    // LIVE
    override fun observeOperationsBetween(storeId: String, since: Long, until: Long) =
        StoreCollections.rechargeOperations(storeId)
            .where { (FIELD_CREATED_AT greaterThanOrEqualTo firestoreNumber(since)) and (FIELD_CREATED_AT lessThan firestoreNumber(until)) }
            .orderBy(FIELD_CREATED_AT, Direction.DESCENDING)
            .snapshots
            .map { snapshot -> snapshot.mapDocuments<RechargeOperationDto, RechargeOperation> { dto, id -> dto.toDomain(id) } }
            .asRequestState()

    // ONE-SHOT, atomic and idempotent
    override suspend fun record(operation: RechargeOperation) = safeCall {
        val ref = StoreCollections.rechargeOperation(operation.storeId, operation.id)
        StoreCollections.transaction {
            val existing = get(ref)
            if (existing.exists) RechargeSaveOutcome.AlreadySaved(/* the stored one */)
            else { set(ref, RechargeOperationDto.from(operation)); RechargeSaveOutcome.Saved(operation) }
        }
    }
}
```

### 4.3 The rules that keep the data layer maintainable

1. **One object owns every path** — `StoreCollections` (+ `FirestorePaths` constants). No repository builds a path
   string by hand. It rejects a blank `storeId`/document id loudly (`requireStoreId()`), because Firestore would
   otherwise silently address a different collection.
2. **`storeId` (tenant id) is a parameter of every call**, never captured once in a repository field. A repository
   cannot write to "whatever store is current" by accident.
3. **DTO ≠ domain model.** The DTO is the wire shape (`@Serializable`, defaults for every field, `Long` stored as
   `amountMinor`…). It converts both ways:
   - `dto.toDomain(fallbackId): Model?` — returns **null** for a document that cannot be a valid model.
   - `Dto.from(model)` — in a `companion object`.
   Domain models carry real types (`Money`, enums, `Instant`-like longs) and no Firebase annotations.
4. **`mapDocuments { }` skips malformed documents and logs each skip** — one bad document must not blank a screen, and a
   silent skip would be indistinguishable from "not there".
5. **Writes that must not duplicate use a client-made id + a transaction** (read the id first → `AlreadySaved`, else
   `set`). The ViewModel creates the id **once per form** and reuses it on retry (section 6.4).
6. **Money is a value class in minor units** (`Money`, 100 minor = 1 major) and is stored as `…Minor` `Long` fields.
   Never store a raw `Double` price.
7. **Platform quirk, solved once:** on Kotlin/JS a `Long` must be written as a JS number. DTO `Long` fields use the
   `FirestoreLong` typealias and raw `update(...)` values go through `firestoreNumber(...)`. Do the same in any
   project that writes `Long` from Kotlin/JS.
8. **Security rules are the real authorization.** `if (role == ADMIN)` in the UI or a use case is convenience only. A
   hostile client can skip both. Every permission is repeated in the backend rules, and the rules have their own
   emulator tests.

---

## 5. The domain layer

### 5.1 Models and pure logic
- `domain/model/` — plain data classes and enums (`RechargeOperation`, `RechargeStatus`, `UserRole`…). Unknown wire values
  map to a safe default through `fromRaw(...)` on the enum (never crash on data you did not write).
- `domain/<area>/` — pure functions on models (`RechargeSummaries.summarize(ops, window)`, `rangeWindow(...)`). **Put every
  calculation here, not in the ViewModel and not in a composable.** They are trivially unit-testable and shared by every
  screen that needs them.

### 5.2 Use cases — one class per WRITE operation
Reads go ViewModel → repository directly (they are just `Flow`s). **Writes go through a use case**, because a write has
rules: validation, who may do it, how the object is built, and how repository outcomes map to something the UI can show.

```kotlin
class RecordRechargeUseCase(private val repository: RechargeRepository, private val clock: AppClock) {

    suspend operator fun invoke(storeId: String, userId: String, userName: String?, operator: RechargeOperator,
                                amount: Money?, note: String?, operationId: String): RecordRechargeResult {
        if (storeId.isBlank() || userId.isBlank()) return RecordRechargeResult.NoSession       // 1. guard
        if (amount == null || amount <= Money.Zero) return RecordRechargeResult.InvalidAmount  // 2. validate
        val operation = RechargeOperation(/* … built here, with clock.nowMillis() … */)        // 3. build the domain object
        return when (val saved = repository.record(operation)) {                               // 4. call, then map
            is RequestState.Success -> when (val o = saved.data) {
                is RechargeSaveOutcome.Saved        -> RecordRechargeResult.Saved(o.operation, alreadySaved = false)
                is RechargeSaveOutcome.AlreadySaved -> RecordRechargeResult.Saved(o.operation, alreadySaved = true)
            }
            is RequestState.Error -> RecordRechargeResult.Failed(saved.message)
            else -> RecordRechargeResult.Failed("")
        }
    }
}

sealed interface RecordRechargeResult {             // EVERY way the operation can end, named
    data class Saved(val operation: RechargeOperation, val alreadySaved: Boolean) : RecordRechargeResult
    data object InvalidAmount : RecordRechargeResult
    data object NoteTooLong : RecordRechargeResult
    data object NoSession : RecordRechargeResult
    data class Failed(val message: String) : RecordRechargeResult
}
```

**Why a sealed `Result` instead of `RequestState` here?** Because the ViewModel must react differently to each ending
(show a field error, close the form, keep the form and offer retry). `RequestState` only says "ok / failed"; the sealed
result says *what happened* in business terms, and Kotlin's exhaustive `when` forces the ViewModel to handle every case
when you add a new one.

**Use-case rules**
- Plain class, constructor-injected repositories and `AppClock` (never `Clock.System` inside — injectable time = testable).
- `suspend operator fun invoke(...)`.
- Validates and trims input. Role checks come from a capability flag on the role (`role.canVoidRecharge`), not scattered
  `== ADMIN` comparisons.
- Returns a sealed `…Result`. It never throws for business outcomes.
- Registered as `factory` in Koin (stateless).

### 5.3 The session (who, where, what role)
One `SessionManager` (a Koin `single`) exposes `state: StateFlow<SessionState>`:

```kotlin
sealed interface SessionState {
    data object Unknown; data object SignedOut
    data class Resolving(val user: AppUser)
    data class NoStoreAccess(val user: AppUser)
    data class AccessDisabled(val user: AppUser, val memberships: List<StoreMembership>)
    data class Active(val user: AppUser, val activeStore: StoreMembership, val memberships: List<StoreMembership>, …) {
        val storeId get() = activeStore.storeId
        val role get() = activeStore.role
    }
    data class Failed(val message: String)
}
```

- It is built from the auth listener + memberships + the chosen store, and it is the **only** source of "current store"
  and "current role". No screen reads Firebase Auth directly.
- **A write captures the session at the moment the user commits** (`sessionManager.state.value as? SessionState.Active`),
  and passes `storeId`/`uid` down explicitly. Nothing re-reads the "current store" later, so a store switch in the middle
  of a request cannot redirect it.
- A role is **never trusted from the device** for security — the backend rules re-check it.

---

## 6. The ViewModel — the integration point between logic and UI

A ViewModel is **one screen family's state machine**. It owns: the data the screen shows, the local state of any form or
dialog, the actions, and one-off events. It contains **no formatting, no Compose, no Firebase**.

### 6.1 Constructor

```kotlin
class FlexyViewModel(
    private val sessionManager: SessionManager,
    private val repository: RechargeRepository,
    private val recordRecharge: RecordRechargeUseCase,
    private val voidRecharge: VoidRechargeUseCase,
    private val clock: AppClock,
) : ViewModel()
```
Everything is an interface or a stateless class injected by Koin. That is what makes the ViewModel testable with fakes.

### 6.2 The four kinds of state a ViewModel exposes

| Kind | Type | Example | Rule |
|---|---|---|---|
| **Backend data** | `StateFlow<RequestState<UiData>>` | `today`, `history` | derived from repository flows; starts at `Loading` |
| **Filters / selections** | `StateFlow<…>` from a private `MutableStateFlow` | `preset`, `operatorFilter` | the screen changes them via functions; they feed the data flow |
| **Form / dialog state** | `StateFlow<FormState?>` (`null` = closed) | `form`, `void` | one `data class` per form; includes `saving`, `error`, the idempotency id |
| **One-off events** | `Flow<Event>` from a `Channel` | `events` | toasts and navigation, **not** state (see 6.5) |

Always follow the private-mutable / public-read-only pair:

```kotlin
private val _form = MutableStateFlow<FlexyForm?>(null)
val form: StateFlow<FlexyForm?> = _form.asStateFlow()
```

### 6.3 The backend-data flow (copy this shape)

```kotlin
private val active: Flow<SessionState.Active?> = sessionManager.state.map { it as? SessionState.Active }
    .distinctUntilChanged { a, b -> a?.storeId == b?.storeId && a?.role == b?.role && a?.user?.uid == b?.user?.uid }

val today: StateFlow<RequestState<FlexyToday>> = combine(active, todayWindow) { session, window -> session to window }
    .flatMapLatest { (session, window) ->
        if (session == null) flowOf(RequestState.Error(NO_SESSION))
        else repository.observeOperationsBetween(session.storeId, window.start, window.end)
            .map { state -> state.map { ops -> FlexyToday(window, summarize(ops, window), recent(ops)) } }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestState.Loading)
```

Why each piece is there:
- **`active` + `distinctUntilChanged`** — recompute only when the store/role/user actually changes.
- **`flatMapLatest`** — when the store (or any input) changes, the old Firestore listener is cancelled and a new one starts.
  Switching store therefore *replaces* the data; nothing from store A can remain on screen in store B.
- **`.map { state -> state.map { … } }`** — transform the payload, keep Loading/Error as they are.
- **Calculations come from `domain/`** (`summarize`, `recent`) — not written inline.
- **`stateIn(viewModelScope, WhileSubscribed(5_000), Loading)`** — hot, shared by all collectors, listener stops 5 seconds
  after the screen leaves (so rotation/navigation doesn't re-subscribe, and a screen nobody sees costs no reads).
  The **initial value is `Loading`**, so the UI always has something to draw on the first frame.

### 6.4 Actions that write (copy this shape)

```kotlin
fun save() {
    val form = _form.value ?: return
    if (form.saving) return                                             // 1. ignore double taps
    val session = sessionManager.state.value as? SessionState.Active ?: return   // 2. capture session NOW
    val amount = form.amount
    if (amount == null) { _form.value = form.copy(error = FlexyFormError.INVALID_AMOUNT); return }   // 3. cheap UI-level checks
    _form.value = form.copy(saving = true, error = null)                // 4. show progress
    viewModelScope.launch {
        when (val result = recordRecharge(session.storeId, session.user.uid, /* … */, operationId = form.operationId)) {
            is RecordRechargeResult.Saved         -> { _form.value = null; _events.send(FlexyEvent.Saved(result.operation, result.alreadySaved)) }
            RecordRechargeResult.InvalidAmount    -> fail(form, FlexyFormError.INVALID_AMOUNT)
            RecordRechargeResult.NoteTooLong      -> fail(form, FlexyFormError.NOTE_TOO_LONG)
            RecordRechargeResult.NoSession,
            is RecordRechargeResult.Failed        -> fail(form, FlexyFormError.SAVE_FAILED)
        }                                                                // 5. exhaustive `when` over the sealed Result
    }
}

// On failure the form STAYS OPEN with what was typed AND the same operationId → "Save" again is a safe retry.
private fun fail(form: FlexyForm, error: FlexyFormError) { _form.value = form.copy(saving = false, error = error) }
```

The checklist inside that function is the pattern:
1. **Guard against double-submit** (`saving` flag).
2. **Capture the session at commit time** and pass ids down.
3. **Cheap input checks** may live in the ViewModel for instant feedback; the **authoritative validation is in the use case**.
4. **Set `saving = true`** before launching, so the UI shows progress and blocks edits.
5. **`when` over the sealed result** — each branch updates form state or sends an event.
6. **The idempotency id is created once** when the form opens (`FlexyForm(operationId = newId())`) and **kept on failure**,
   so a retry after a lost answer never duplicates the record (the repository answers `AlreadySaved`).
7. **Never discard what the user typed because a call failed.**

### 6.5 One-off events (toasts) — `Channel`, not state

```kotlin
private val _events = Channel<FlexyEvent>(Channel.BUFFERED)
val events: Flow<FlexyEvent> = _events.receiveAsFlow()
// …
_events.send(FlexyEvent.Saved(operation, alreadySaved))
```

A "saved" toast is an *event* — it must show once, and must not reappear after rotation or when the screen is revisited.
If you modelled it as state it would. Rule of thumb: **state = what is true now; event = something that just happened.**

### 6.6 Form editing helpers
Edits go through one helper that refuses changes while saving and clears the stale error:

```kotlin
private fun editForm(change: (FlexyForm) -> FlexyForm) {
    _form.update { form -> form?.takeIf { !it.saving }?.let { change(it).copy(error = null) } ?: form }
}
fun setNote(text: String) = editForm { it.copy(note = text.take(NOTE_INPUT_LIMIT)) }
```

### 6.7 Other ViewModel rules
- A **store switch closes any open form/dialog** (collect `active`; when `storeId` changes, set them to `null`).
- A screen with unfinished work registers it with a `StoreSwitchGuard` so the switcher can warn before discarding it.
- A `viewModelScope.launch` is the only place a ViewModel starts work. No `GlobalScope`.
- Constants for limits and thresholds (`FLEXY_CONFIRM_ABOVE`, `STOP_TIMEOUT_MILLIS`) are named, top-level or in a
  `private companion object`, with the *reason* in a comment.

---

## 7. The UI layer — how the data is displayed in harmony with the logic

The screen is a **pure function of ViewModel state**. It owns no business decisions.

### 7.1 The screen skeleton

```kotlin
@Composable
fun Flexy(
    onOpenHistory: () -> Unit,                         // navigation arrives as lambdas — a screen never owns a NavController
    viewModel: FlexyViewModel = koinViewModel(),       // injected by default, replaceable in previews/tests
) {
    val today by viewModel.today.collectAsState()      // 1. collect each StateFlow
    val canVoid by viewModel.canVoid.collectAsState()
    val wide = LocalWidthClass.current >= WidthClass.Expanded     // 2. responsive layout decision

    FlexyEvents(viewModel)                             // 3. toasts: one small composable that collects `events`
    FlexyDialogs(viewModel)                            // 4. dialogs observe `form` / `void` themselves

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(consolePagePadding())) {
        Header(onOpenHistory, onNew = viewModel::openNew)           // 5. actions are method references on the ViewModel
        when (val state = today) {                                   // 6. ONE branch per RequestState
            RequestState.Idle, RequestState.Loading -> LoadingSpinner()
            is RequestState.Error                   -> ErrorNotice(stringResource(Res.string.fx_load_failed))
            is RequestState.Success                 -> Content(state.data, canVoid, onVoid = viewModel::openVoid)
        }
    }
}
```

### 7.2 Rendering a `RequestState` — two equivalent options

**A. A plain `when` (what most TechStore screens do):** explicit, easy to customise per screen.

```kotlin
when (val state = devices) {
    RequestState.Idle, RequestState.Loading -> Spinner()
    is RequestState.Error                   -> ErrorNotice(state.message)
    is RequestState.Success                 -> if (state.data.isEmpty()) EmptyState() else List(state.data)
}
```

**B. `DisplayResult` (`core/DisplayResult.kt`):** the same thing with a fade between states, which does **not** replay the
transition when only the *data* inside `Success` changes (important for lists that re-emit continuously).

```kotlin
devices.DisplayResult(
    onLoading = { InventoryLoading() },
    onError   = { message -> InventoryError(message) },
    onSuccess = { data -> InventoryContent(data) },
)
```

Pick one per project and use it everywhere. (TechStore ended up with both: `DisplayResult` on Dashboard/Reports, plain
`when` in ~28 files. For the new app, **decide once** — recommended: `DisplayResult` everywhere, with a `when` only where a
screen needs a custom Loading skeleton.)

**Every `RequestState` screen must define all four visuals:** loading, error, empty (inside `Success`), content.

### 7.3 Forms and dialogs
A dialog is a composable that **observes a nullable form state** and shows itself when it is non-null:

```kotlin
@Composable
internal fun FlexyDialogs(viewModel: FlexyViewModel) {
    val form by viewModel.form.collectAsState()
    form?.let { NewFlexyDialog(it, viewModel) }          // null = closed
    val draft by viewModel.void.collectAsState()
    draft?.let { VoidDialog(it, viewModel) }
}
```
Inside the dialog: text fields call `viewModel::setNote`, buttons call `viewModel::save` / `viewModel::closeForm`, errors come from
`form.error` (an **enum**, mapped to a localized string *in the UI*), progress from `form.saving` (disable the button, show a
spinner). The dialog holds **no state of its own** beyond transient things like focus.

### 7.4 Events → toasts
```kotlin
@Composable
internal fun FlexyEvents(viewModel: FlexyViewModel) {
    val snackbar = LocalShellSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {                                    // exhaustive over the sealed event
                is FlexyEvent.Saved -> getString(Res.string.fx_saved, /* formatted values */)
                is FlexyEvent.Voided -> getString(Res.string.fx_void_done)
                FlexyEvent.VoidNotNeeded -> getString(Res.string.fx_void_not_needed)
            }
            snackbar.showSnackbar(ShellToastVisuals(message, Res.drawable.ic_check_circle))
        }
    }
}
```

### 7.5 Presentation rules (this is what keeps UI and logic in harmony)
1. **The ViewModel emits meaning; the composable emits text.** State carries enums/ids (`FlexyFormError.SAVE_FAILED`,
   `RechargeOperator.DJEZZY`), never display strings. The composable maps them with `stringResource(...)`. That is why one
   ViewModel serves three languages (EN/FR/AR, with RTL) and why tests assert on enums, not text.
2. **Formatting lives in the UI layer** (`LocalizedFormats.money(amount, language)`, date formatting) — never in the ViewModel
   or domain.
3. **Error text:** repository errors arrive as user-readable `message` (`toUserMessage()`); screens may show that or a
   localized generic string. Business errors (`FlexyFormError`) are always localized in the UI.
4. **Responsiveness is a UI concern:** one `LocalWidthClass` (Compact / Medium / Expanded / Large) provided at the shell. The
   same ViewModel feeds phone, tablet, desktop and web layouts; only the composable arranges differently.
5. **Navigation is lambdas passed in** (`onOpenHistory`). The graph (`NavGraph.kt`) wires them to routes. Screens never
   import `NavController`.
6. **Every screen takes `viewModel: X = koinViewModel()`** so previews and tests can pass a different one.
7. **A composable never calls a repository, a use case or `Firebase`.** It calls `viewModel` methods only.
8. **Design-system components, not raw Material, in screens** (`DesignButton`, `DesignChip`, `FormField`, `DialogFrame`…).
   Colors/typography/spacing come from the theme (`MaterialTheme.colorScheme`, `TechStockType`, `Spacing`), never literals.
9. **All user-visible text is in `composeResources/values*/strings_<feature>.xml`**, one file per feature, with a prefix
   (`fx_…`), in every supported language, added in the same change as the screen.

---

## 8. Dependency injection (Koin)

`di/SharedModule.kt` is the one wiring file. Choose the scope by what the thing holds:

```kotlin
val sharedModule = module {
    single<AppClock> { AppClock.System }

    // Repositories: single (cheap, stateless wrappers over the backend), bound to the INTERFACE
    single<RechargeRepository> { RechargeRepositoryImpl() }

    // Use cases: factory — they hold no state, so a shared instance could only acquire some
    factory { RecordRechargeUseCase(repository = get(), clock = get()) }

    // Session: single — one source of truth for the whole process
    single { SessionManager(authRepository = get(), userRepository = get(), /* … */) }

    // ViewModels: viewModelOf (constructor injection) — one per screen
    viewModelOf(::FlexyViewModel)
}

expect val targetModule: Module        // platform-only bindings (see §9)
```

Entry points call `initializeKoin()` once per process: `Application.onCreate` (Android), `main()` (desktop/web),
`MainViewController()` (iOS). Firebase initialises **before** Koin because repositories reach for it.

**Rule: adding a feature = adding exactly three kinds of lines here** (repository `single`, use case `factory`, ViewModel
`viewModelOf`) **and a test that resolves the ViewModel** (`SharedModuleTest`), which catches a missing binding before a user does.

---

## 9. Platform differences — `expect`/`actual` seams only

Everything shared lives in `commonMain`. A platform source set contains **only** things that cannot be shared, behind a tiny
`expect` declaration:

| Seam | Why it is platform code |
|---|---|
| `InitializeFirebase()` | each platform initialises Firebase differently |
| `targetModule` (Koin) | preference stores, credential cleaner, haptics, receipt output… |
| `GoogleSignInButton` | native sign-in UI per platform |
| `CameraScanner` | CameraX/ML Kit on Android, AVFoundation on iOS, none on desktop/web |
| `SystemBackHandler`, `SystemBarsAppearance`, `LocalAppLocale` | OS integration |
| `FIRESTORE_LONG_AS_NUMBER` | Kotlin/JS needs a JS number for `Long` |

Rules: keep each `expect` as small as possible; give every platform a real `actual` (a no-op **by design** is fine; a
stub that "compiles and does nothing" is not); never put business logic in `androidMain`/`iosMain`/….

---

## 10. Navigation and the session gate

- Routes are a `sealed interface Route` of `@Serializable` objects/classes (type-safe, no strings): `data object Dashboard`,
  `data class DeviceDetails(val deviceId: String)`.
- **Sign-in is not a route.** `SessionGate` sits above the NavHost and switches on `SessionState`: signed out → Auth, no store →
  explanation screen, access disabled → explanation, active → the app shell. A signed-out user therefore has no back stack to
  return into, and a token expiry drops straight to Auth.
- Each top-level destination = one composable + one ViewModel; the graph passes navigation lambdas in.

---

## 11. Testing pattern (`commonTest`, runs on the JVM)

- **No mocking library.** Write a small **fake** that implements the repository interface (e.g. `Repo : RechargeRepository`
  backed by `MutableStateFlow`, with `failSaves = N` to simulate failures and a `reads` list to assert which queries ran).
- Use the **real** `SessionManager` with fake auth/user repositories; use a fixed `AppClock { NOW }`.
- ViewModel tests: `Dispatchers.setMain(StandardTestDispatcher())`, `runTest`, `runCurrent()`, then assert on `viewModel.today.value`,
  `viewModel.form.value`, and the events.
- **Domain tests are plain functions** (summaries, ranges, validation) — fastest and most valuable.
- Always cover: success, validation failure, **failed write then retry with the same id**, store switch mid-flow, role permissions,
  bounded-read arguments, and "voided/deleted doesn't count".
- `SharedModuleTest` resolves every ViewModel from Koin.
- Backend security rules have their own emulator test suite (`firebase/rules-tests/`) — test **every forbidden case**, with the real
  field names used by the DTOs (a rules test that uses a different field name than the app passes while protecting nothing).

---

## 12. Checklist — adding a feature (do these in order)

Say "Build feature X following ARCHITECTURE_PATTERN.md". The steps are:

1. **Domain model + enums** in `domain/model/` (with `fromRaw` for enums read from the backend; constants for limits).
2. **Pure logic** in `domain/<area>/` (summaries, grouping, windows) + its unit tests.
3. **Repository interface** in `domain/repository/` (`observe…` → `Flow<RequestState<…>>`, writes → `suspend … RequestState<Outcome>`,
   `Outcome` sealed types). Bounded reads only.
4. **DTO** in `data/dto/` (`toDomain` returns `null` for invalid documents; `from(model)`), money in minor units, `FirestoreLong`.
5. **RepositoryImpl** in `data/repository/`, using `StoreCollections`, `.asRequestState()`, `safeCall`, `mapDocuments`, a transaction for
   anything that must be atomic/idempotent. Add the path to `StoreCollections`/`FirestorePaths`.
6. **Use case + sealed Result** in `domain/usecase/` for each write.
7. **Backend security rules** for the new collection (default-deny, membership, role, field allow-list, validated types) + emulator tests.
8. **ViewModel** in `feature/<name>/`: state flows (`stateIn`/`WhileSubscribed(5_000)`/`Loading`), form state classes, actions,
   `Channel` events, store-switch handling.
9. **Strings** in `values/`, `values-fr/`, `values-ar/` (`strings_<feature>.xml`).
10. **Screen + dialogs + events composables** in `feature/<name>/` (all four visuals for every `RequestState`; responsive via `LocalWidthClass`).
11. **Route + NavGraph entry + menu entry**; screen gets navigation as lambdas.
12. **Koin**: repository `single`, use case `factory`, `viewModelOf`; add the ViewModel to `SharedModuleTest`.
13. **ViewModel tests** with a fake repository; run the whole suite and every platform build.
14. **Docs**: a short `design/implementation/<FEATURE>.md` (model, path, permissions, queries, cost estimate, what is *not* done).

---

## 13. The non-negotiables (one screen to remember)

1. UI → ViewModel → (UseCase) → Repository interface → Impl → Firebase. **Never skip a layer, never go backwards.**
2. `RequestState` is for backend data only. Forms/carts/scanners are their own state classes.
3. Reads are `Flow<RequestState<T>>` + `stateIn(WhileSubscribed(5_000), Loading)`. Writes are use cases returning a **sealed Result**.
4. Business outcomes that are not failures are **outcome types**, not exceptions and not `Error`.
5. ViewModel state carries **enums and ids, not strings**; the UI localizes and formats.
6. One-off things (toasts, navigation) are **events (`Channel`)**, not state.
7. `storeId`/`uid` are **parameters captured at commit time**, never re-read later, never hardcoded.
8. Every non-duplicable write: **client id made once per form + transaction + `AlreadySaved`**; a failed save keeps the form and the id.
9. Bounded queries only. No "observe all".
10. Never swallow `CancellationException`. Always give a malformed document a log line, not a crash.
11. The backend rules are the real authorization; the UI/use-case checks are convenience.
12. Platform source sets hold seams only. Business logic is in `commonMain`.
13. No secrets in the client. Public Firebase config only; service accounts, client secrets and signing keys never ship.
14. Tests use fakes of the repository interfaces. Rules have their own tests.
15. One feature = one folder, one strings file, one doc, three Koin lines.

---

## 14. Adapting it to the new app

- **Rename the tenant concept** (`storeId` → your tenant, or remove it if single-tenant). Keep "passed in every call, captured at commit".
- **Keep the names of the generic parts** (`RequestState`, `safeCall`, `asRequestState`, `DisplayResult`, `SessionManager`/`SessionState`,
  `StoreCollections`-style path object, `Money`, `AppClock`) — they carry no product logic.
- **Decide once, up front:** `DisplayResult` vs plain `when` for rendering; supported languages (add all strings together); whether
  to use extension functions (TechStore's feature code avoids new ones; its few shared helpers — `asRequestState`, `DisplayResult`,
  `mapDocuments` — are the exceptions).
- **Start the new project with:** `core/` (RequestState, SafeCall, DisplayResult, Money, AppClock) → Koin `SharedModule` +
  `expect val targetModule` → `SessionManager` + `SessionGate` → one small feature built by section 12 as the reference for the rest.
- **Offline:** this pattern assumes the backend is reachable for writes (a failed save is shown and retried, not queued). If the new app
  needs real offline writes, design that as its own layer *before* building features (an outbox with idempotent operation ids — the
  same id discipline as section 4.3 point 5 makes it possible).

---
---

# PART 2 — DEPENDENCY PLAYBOOK

**Purpose:** a new project starts from a *known-good, already-resolved* set of versions, so nobody loses days to a
"works on my machine" version clash. Everything below was verified building together in TechStore on **2026-11-08**
(Android, iOS, desktop and web all build; 786 unit tests green).

**Rules for using this playbook**
1. **Copy the versions as a set.** Kotlin, Compose Multiplatform, the Compose compiler plugin, AGP, Gradle and GitLive are
   one compatibility chain. Never bump one of them on its own.
2. **Everything goes through `gradle/libs.versions.toml`.** No version literals inside module build files (TechStore has
   one leftover violation, listed in §21 — do not copy it).
3. **A new dependency needs a written justification** (§22) before it is added.
4. **After any dependency change, run the full verification** (§23) — all four platforms, not just the one you work on.
5. These are the versions that worked *here*. Where §16 marks one as **pre-release**, re-check that it is still the
   right choice before starting; if you move to newer ones, move the whole chain and run §23.

---

## 15. Verified baseline (the chain)

### 15.0 Restora: the chain in use (verified 2026-11-08, `docs/decisions/versions.md`)
Restora uses the newer wizard chain below, not TechStore's. The authoritative catalog is the repo's `gradle/libs.versions.toml`; §16 below is TechStore's catalog, kept for reference (its Firebase/GitLive/KMPAuth/credentials/material-icons entries are **not used** by Restora).

| Piece | Version | Notes |
|---|---|---|
| Kotlin + Compose compiler plugin + serialization plugin | **2.4.21** | |
| Compose Multiplatform | **1.12.2** | |
| AGP / Gradle | **9.1.1** / **9.5.1** | daemon JDK 21 (foojay) |
| compileSdk / targetSdk / minSdk | **37 / 37 / 24** | |
| JVM target | **17** in KMP libraries, **11** in `:androidApp` | |
| JetBrains Lifecycle / Navigation Compose | **2.11.0** / **2.10.0** | both stable |
| Material 3 (CMP) | **1.12.0-alpha03** | pre-release; only `:designsystem` depends on it |
| Koin | **4.2.2** | |
| kotlinx-coroutines / datetime / serialization-json | **1.11.0** / **0.8.0** / **1.11.0** | serialization declared explicitly (DTOs in `:core-domain`) |
| supabase-kt (supabase-kt, auth-kt, postgrest-kt, realtime-kt, functions-kt) | **3.9.0** | replaces GitLive Firebase |
| Ktor engines | **3.6.0**: okhttp (android, jvm), darwin (ios), js | |
| kotlin-wrappers (`kotlin-browser`) | **2026.10.4** | JS only |

Verified on 2026-11-08: unit tests on jvm, js (Node), Android host and iOS simulator; `:apps:restaurantStaff:androidApp:assembleDebug`; both desktop apps `compileKotlin`; three `jsBrowserDistribution`; staff `compileKotlinIosArm64`/`IosSimulatorArm64`; Xcode simulator build of `apps/restaurantStaff/iosApp`; customer web app loaded in a browser with no console errors.
Unlike §18-10, `iosSimulatorArm64Test` **links and runs** here (no Firebase frameworks to link).

### 15.1 TechStore's original chain (reference)


| Piece | Version | Notes |
|---|---|---|
| Kotlin | **2.4.10** | The Compose compiler plugin MUST use this exact version (`composeCompiler` ties to `kotlin`) |
| Compose Multiplatform | **1.11.1** | `org.jetbrains.compose` plugin + `compose.*` artifacts |
| Compose compiler plugin | = Kotlin (**2.4.10**) | `org.jetbrains.kotlin.plugin.compose` |
| Android Gradle Plugin | **9.0.1** | AGP 9 compiles Kotlin itself: `:androidApp` applies **no** Kotlin-Android plugin |
| Gradle | **9.1.0** | `gradle/wrapper/gradle-wrapper.properties` |
| JDK running Gradle | **21** (Android Studio's bundled JBR, Zulu 21) | |
| compileSdk / targetSdk / minSdk | **36 / 36 / 24** | |
| JVM target | **17** in `:shared` (Android library), **11** in `:androidApp` | the split is intentional (§18-2) |
| GitLive Firebase Kotlin SDK | **2.4.0** | `dev.gitlive:firebase-*` |
| Firebase Android BoM | **33.7.0** | pins the GitLive Android wrappers (§18-5) |
| Firebase Apple SDK (SwiftPM) | **12.18.0** minimum, resolved **12.19.3** | `firebase-ios-sdk`, "up to next major" |
| GoogleSignIn-iOS (SwiftPM) | **9.2.0** minimum | |
| firebase-java-sdk (desktop) | **0.6.3** | community port, see §18-7 |
| Firebase JS SDK | `firebase@10.12.2`, `@firebase/app-check@0.8.4` | arrives transitively through GitLive JS; versions are in `kotlin-js-store/yarn.lock`. **Do not declare them yourself** |
| Koin | **4.2.2** | `koin-core`, `koin-compose`, `koin-compose-viewmodel`, `koin-android` |
| kotlinx-coroutines | **1.11.0** | tests + `coroutines-swing` on desktop |
| kotlinx-datetime | **0.8.0** | |
| kotlinx-serialization | plugin = Kotlin version | runtime arrives transitively (1.9.0 here); no explicit entry |
| Navigation Compose (JetBrains) | **2.9.0-beta01** | pre-release |
| AndroidX Lifecycle (JetBrains builds) | **2.11.0-beta01** | pre-release |
| Material 3 (Compose MP) | **1.11.0-alpha07** | pre-release |
| Material icons extended | **1.7.3** | |
| KMPAuth (Google Sign-In) | **2.3.0-beta02** | pre-release |
| AndroidX Activity | **1.13.0** | |
| AndroidX Credentials | **1.3.0** | Android Google Sign-In |
| googleid | **1.1.1** | |
| google-services Gradle plugin | **4.4.2** | applied only in `:androidApp` |
| kotlin-wrappers (`kotlin-browser`) | **2026.8.4** | JS only |
| CameraX / ML Kit (only if the app scans) | **1.4.2** / barcode **17.3.0**, text **16.0.1** | Android only; optional |

**Targets built:** `android`, `iosArm64`, `iosSimulatorArm64`, `jvm` (desktop), `js` (browser, IR).
**`wasmJs` is NOT a target** — the GitLive/KMPAuth stack publishes no wasm artifacts (§18-3).

**Pre-release risk (honest note):** Navigation, Lifecycle, Material 3 and KMPAuth are beta/alpha builds. They work together
here, but a clean re-resolve months later may pick different transitive versions. Commit `kotlin-js-store/yarn.lock`, keep
Gradle's dependency verification/lock behaviour consistent for the team, and bump these four deliberately, not by accident.

---

## 16. `gradle/libs.versions.toml` — ready to paste

> **Restora:** don't paste this block. The catalog in use is `gradle/libs.versions.toml` (chain §15.0). This block is TechStore's, kept for reference.

Keep the alias names: the module build files in §17 refer to them. Remove the optional blocks you don't need.

```toml
[versions]
agp = "9.0.1"
android-compileSdk = "36"
android-minSdk = "24"
android-targetSdk = "36"
androidx-activity = "1.13.0"
androidx-lifecycle = "2.11.0-beta01"
composeMultiplatform = "1.11.1"
kotlin = "2.4.10"
kotlin-wrappers = "2026.8.4"
kotlinx-coroutines = "1.11.0"
kotlinxDatetime = "0.8.0"
material3 = "1.11.0-alpha07"
materialIconsExtended = "1.7.3"
koin = "4.2.2"
navigation = "2.9.0-beta01"
firebase = "2.4.0"
firebaseBom = "33.7.0"
firebaseJavaSdk = "0.6.3"
google = "4.4.2"
auth = "2.3.0-beta02"
androidxCredentials = "1.3.0"
googleId = "1.1.1"
# OPTIONAL — only for an app that scans barcodes on Android
camerax = "1.4.2"
mlkitBarcode = "17.3.0"
mlkitText = "16.0.1"

[libraries]
# Tests (commonTest)
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }

# Compose Multiplatform (commonMain)
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeMultiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "composeMultiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "composeMultiplatform" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "composeMultiplatform" }
compose-uiTooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "composeMultiplatform" }
compose-uiToolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "composeMultiplatform" }
material-icons-extended = { module = "org.jetbrains.compose.material:material-icons-extended", version.ref = "materialIconsExtended" }
compose-navigation = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }

# Platform glue
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
kotlinx-coroutinesSwing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "kotlinx-coroutines" }   # desktop: Dispatchers.Main
wrappers-browser = { module = "org.jetbrains.kotlin-wrappers:kotlin-browser", version.ref = "kotlin-wrappers" }                # web only

# Kotlin libs
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }

# DI
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }

# Firebase — GitLive multiplatform wrappers (commonMain). firebase-storage ONLY if the app uses Storage.
firebase-app = { module = "dev.gitlive:firebase-app", version.ref = "firebase" }
firebase-common = { module = "dev.gitlive:firebase-common", version.ref = "firebase" }
firebase-auth = { module = "dev.gitlive:firebase-auth", version.ref = "firebase" }
firebase-firestore = { module = "dev.gitlive:firebase-firestore", version.ref = "firebase" }
firebase-storage = { module = "dev.gitlive:firebase-storage", version.ref = "firebase" }

# Firebase — desktop (JVM) needs the JVM wrappers + the community Java SDK
firebase-common-jvm = { module = "dev.gitlive:firebase-common-jvm", version.ref = "firebase" }
firebase-auth-jvm = { module = "dev.gitlive:firebase-auth-jvm", version.ref = "firebase" }
firebase-firestore-jvm = { module = "dev.gitlive:firebase-firestore-jvm", version.ref = "firebase" }
firebase-java-sdk = { module = "dev.gitlive:firebase-java-sdk", version.ref = "firebaseJavaSdk" }

# Firebase BoM — pins the GitLive Android wrappers' empty versions on Android classpaths
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }

# Google Sign-In
auth-kmp = { module = "io.github.mirzemehdi:kmpauth-google", version.ref = "auth" }
auth-firebase-kmp = { module = "io.github.mirzemehdi:kmpauth-firebase", version.ref = "auth" }
androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "androidxCredentials" }
androidx-credentials-playServices = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "androidxCredentials" }
google-identity-googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "googleId" }

# OPTIONAL — Android barcode scanner (androidMain only)
androidx-camera-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { module = "androidx.camera:camera-view", version.ref = "camerax" }
mlkit-barcode-scanning = { module = "com.google.mlkit:barcode-scanning", version.ref = "mlkitBarcode" }
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkitText" }

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidMultiplatformLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
google-services = { id = "com.google.gms.google-services", version.ref = "google" }
```

**In TechStore's catalog but UNUSED by any build file — do not copy:** `kotlin-testJunit`, `junit`, `androidx-core-ktx`,
`androidx-testExt-junit`, `androidx-espresso-core`, `androidx-appcompat` (wizard leftovers).

---

## 17. The Gradle files — ready to adapt

### 17.1 `settings.gradle.kts`
```kotlin
rootProject.name = "MyApp"          // ⚠ see §18-12: this name decides the generated `Res` package

pluginManagement {
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google { mavenContent { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }
        mavenCentral()
    }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

include(":androidApp")
include(":desktopApp")
include(":shared")
include(":webApp")
```
Only two repositories (Google with a content filter, Maven Central). **No JitPack, no snapshot repositories.**

### 17.2 Root `build.gradle.kts`
```kotlin
plugins {   // declared once, applied in the modules that need them
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.google.services) apply false
}
```

### 17.3 `gradle.properties`
```properties
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx3072M
org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.caching=true
android.nonTransitiveRClass=true
android.useAndroidX=true
```
`gradle/wrapper/gradle-wrapper.properties`: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip`

### 17.4 `:shared` (the whole app lives here) — `shared/build.gradle.kts`
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)   // the NEW KMP Android-library plugin (not com.android.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    // NO google-services here: it belongs to :androidApp only (§18-1)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { baseName = "Shared"; isStatic = true }     // static framework
    }
    jvm()                                   // desktop
    js { browser(); binaries.executable() } // web: Kotlin/JS IR. NO wasmJs (§18-3). `js(IR)` is deprecated in Kotlin 2.4.

    android {                               // the android block lives INSIDE kotlin { }
        namespace = "com.mycompany.myapp.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }     // 17 here, 11 in :androidApp (§18-2)
        androidResources { enable = true }
        withHostTest { isIncludeAndroidResources = true }
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.material.icons.extended)
            implementation(libs.kotlinx.datetime)
            implementation(libs.compose.navigation)

            implementation(libs.firebase.app)
            implementation(libs.firebase.common)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            // implementation(libs.firebase.storage)     // only if the app really uses Storage

            implementation(libs.auth.kmp)
            implementation(libs.auth.firebase.kmp)

            api(libs.koin.core)          // `api`, not `implementation`: initializeKoin(config: KoinApplication.() -> Unit) is public API (§18-6)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServices)
            implementation(libs.google.identity.googleid)
            // OPTIONAL scanner: camera.core / camera2 / lifecycle / view + mlkit.barcode.scanning (+ mlkit.text.recognition)
        }
        jvmMain.dependencies {                       // desktop Firebase
            implementation(libs.firebase.common.jvm)
            implementation(libs.firebase.auth.jvm)
            implementation(libs.firebase.firestore.jvm)
            implementation(libs.firebase.java.sdk)
        }
        jsMain.dependencies { implementation(libs.wrappers.browser) }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    // Firebase BoM on EVERY Android classpath of the library, tests included (§18-5)
    "androidCompileClasspath"(platform(libs.firebase.bom))
    "androidRuntimeClasspath"(platform(libs.firebase.bom))
    "androidHostTestCompileClasspath"(platform(libs.firebase.bom))
    "androidHostTestRuntimeClasspath"(platform(libs.firebase.bom))
    "androidDeviceTestImplementation"(platform(libs.firebase.bom))
    "androidDeviceTestRuntimeClasspath"(platform(libs.firebase.bom))
}
```
`jvmMain` and `jsMain` also get the **generated Firebase web config** (a small Gradle task reading a **gitignored**
`firebase/web-config.json` into a generated Kotlin object) — see §19.

### 17.5 `:androidApp` — `androidApp/build.gradle.kts`
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)       // NO Kotlin-Android plugin: AGP 9 builds the Kotlin itself
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.google.services) apply false      // `apply false` is load-bearing (see below)
}

// Apply google-services ONLY when google-services.json exists, so a fresh clone still builds.
val googleServicesConfig = file("google-services.json")
if (googleServicesConfig.exists()) apply(plugin = libs.plugins.google.services.get().pluginId)
else logger.lifecycle("androidApp/google-services.json not found — Firebase is not configured for Android.")

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.koin.android)            // androidContext(...) in the Application class
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.mycompany.myapp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.mycompany.myapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1; versionName = "1.0"
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    buildTypes { release { isMinifyEnabled = false /* see §20 before turning on */ ; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    buildFeatures { compose = true }
}
```

### 17.6 `:desktopApp` — `desktopApp/build.gradle.kts`
```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)           // the Skiko runtime for THIS OS
    implementation(libs.kotlinx.coroutinesSwing)        // provides Dispatchers.Main on desktop, which viewModelScope needs
    implementation(libs.compose.uiToolingPreview)
}
compose.desktop {
    application {
        mainClass = "com.mycompany.myapp.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.mycompany.myapp"; packageVersion = "1.0.0"
            // ⚠ Packaged installers may need `modules("java.net.http", "jdk.httpserver", …)` if the app uses them.
            //   Run `./gradlew :desktopApp:suggestRuntimeModules` and test the PACKAGED app, not only `:desktopApp:run`. (§18-9)
        }
    }
}
```

### 17.7 `:webApp` — `webApp/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}
kotlin {
    js { browser(); binaries.executable() }          // JS IR only
    sourceSets { commonMain.dependencies { implementation(project(":shared")); implementation(libs.compose.ui) } }
}
```
Production bundle: `./gradlew :webApp:jsBrowserDistribution` → `webApp/build/dist/js/productionExecutable/`.
Dev server: `./gradlew :webApp:jsBrowserDevelopmentRun`.

### 17.8 iOS (Xcode project, Swift Package Manager — no CocoaPods)
- Swift packages: `https://github.com/firebase/firebase-ios-sdk` (≥ 12.18.0) and `https://github.com/google/GoogleSignIn-iOS` (≥ 9.2.0).
- **Link only what the Kotlin code needs:** `FirebaseAuth`, `FirebaseFirestore`, `GoogleSignIn`. (TechStore also links Analytics,
  Crashlytics, Messaging, In-App Messaging and Storage that nothing uses — don't repeat that; each one adds data collection and App
  Store privacy obligations.)
- Xcode build phase **"Compile Kotlin Framework"**: `cd "$SRCROOT/.."; ./gradlew :shared:embedAndSignAppleFrameworkForXcode`
  (skip when `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`). The Kotlin framework is **static**, `baseName = "Shared"`.
- `IPHONEOS_DEPLOYMENT_TARGET = 18.2`; `GoogleService-Info.plist` placed in the `iosApp/` source folder (synchronized folder = added
  automatically); `Info.plist` URL scheme and `GIDClientID` from build settings (§19).

---

## 18. Traps that cost real time here (read before you start)

1. **`google-services` plugin: only in `:androidApp`, and conditionally.** Applied in the shared Android *library* it only
   prints a warning and generates nothing. Declared with `alias(...)` **without** `apply false` it is applied immediately and the
   build then fails with "File google-services.json is missing" for anyone without the file. `apply false` + the `if (file.exists())`
   block is the fix.
2. **JVM target split: 17 in `:shared`, 11 in `:androidApp`.** GitLive's Firestore Android artifacts are compiled at JVM 17; their
   `inline` functions (the `where { }` DSL, `DocumentSnapshot.data<T>()`) cannot be inlined into JVM 11 bytecode. Setting `:shared` to 11
   breaks the build with an inlining error.
3. **No `wasmJs` target.** GitLive and KMPAuth publish no wasm artifacts; enabling it breaks `commonMain` dependency resolution. The web
   target is Kotlin/JS (IR). (Compose's own `skiko.wasm` in the web bundle is just the renderer — not a Kotlin/Wasm target.)
4. **`js(IR)` is deprecated in Kotlin 2.4** (removed in 2.6). Write `js { … }`; IR is the only backend.
5. **Firebase BoM on every Android classpath.** GitLive's Android wrappers have empty versions that the BoM fills. Put
   `platform(libs.firebase.bom)` on compile, runtime, **host-test and device-test** classpaths, or tests fail to resolve.
6. **`koin-core` is `api`**, because `initializeKoin(config: (KoinApplication.() -> Unit)?)` is public and called from the Android,
   desktop and web entry points. With `implementation`, those modules don't see `KoinApplication`.
7. **Desktop Firebase = `firebase-java-sdk` 0.6.3, a community port.** `signInWithCredential` is **not implemented** (throws
   `NotImplementedError`), so Google sign-in on desktop is a hand-written flow; KMPAuth's JVM flow binds a fixed port 8080 and takes
   the window down if it is busy — don't use it on desktop. The SDK's session is whatever `FirebasePlatform` storage you give it (use memory, not
   a plaintext file). Firestore on desktop keeps an SQLite cache in the OS temp folder.
8. **Android Google Sign-In: drive Credential Manager yourself.** KMPAuth hardcodes `setAutoSelectEnabled(true)`, which silently reuses
   the previous account on a shared phone. That is why `androidx-credentials*` and `googleid` are declared.
9. **Desktop installers.** `:desktopApp:run` uses the full JDK; `createDistributable`/`packageDmg` build a **trimmed runtime** that can omit
   modules you use (`java.net.http`, `jdk.httpserver`, networking/gRPC pieces). Not verified in TechStore — test the packaged app early.
   Also keep `kotlinx-coroutines-swing` on the desktop module: it is what provides `Dispatchers.Main`, which `viewModelScope` uses.
10. **iOS.**
    - GitLive 2.4.0 binds to Apple's *real* Firebase frameworks, which Kotlin does not vendor: you **must** add the Firebase SwiftPM
      packages to the Xcode project.
    - `:shared:iosSimulatorArm64Test` **does not link** ("framework 'FirebaseCore' not found") because the Kotlin test executable links
      outside Xcode. Accept it: the iOS framework and app build fine; shared logic is covered by the JVM/Android/JS tests.
    - `FirebaseApp.configure()` may run in Swift or Kotlin but **never twice** — a second call raises an `NSException` that kills the
      process. Check `Firebase.apps(null).isNotEmpty()` first.
    - The URL scheme keys must be the raw names `CFBundleURLTypes` / `CFBundleURLSchemes`; Xcode's editor *displays* them as
      "URL types"/"URL Schemes".
    - Firebase Auth stores the user in the **keychain**, so a simulator build must be code-signed (even ad-hoc, `CODE_SIGN_IDENTITY=-`).
      `CODE_SIGNING_ALLOWED=NO` gives error 17995 and sign-in fails.
11. **Kotlin/JS `Long`.** The Firebase web SDK rejects Kotlin's `Long` object ("Unsupported field value: a custom Long object"). Every `Long`
    DTO field uses a serializer that writes a JS number on JS only (`FirestoreLong`), and raw `update(...)` values go through
    `firestoreNumber(...)`. Without this, every web write with a timestamp or an amount fails. Safe while values stay below 2^53.
12. **Generated `Res` package name.** Compose Resources generates `Res` in a package derived from the project/module names — here
    `techstore.shared.generated.resources` (root project `TechStore`, module `shared`). Every screen imports it. Decide the root project
    name **before** writing UI, or set the package explicitly in the `:shared` module's `compose.resources { packageOfResClass = … }` block
    (recommended; not used in TechStore, so verify it on a throwaway build first). Renaming later means editing every import.
13. **AGP 9 specifics.** `:shared` uses `com.android.kotlin.multiplatform.library` with an `android { }` block *inside* `kotlin { }`
    (not the old `com.android.library`); `:androidApp` has no `org.jetbrains.kotlin.android` plugin. Don't "fix" these by adding the old plugins.
14. **Firebase config for web and desktop** is not a file the SDK reads: it comes from the console's *Web app* config, saved to a
    **gitignored** `firebase/web-config.json`, compiled into a generated Kotlin object by a small Gradle task. Without the file every
    value is blank and Firebase is skipped (the app shows an error state, never another project's backend).
15. **Configuration cache and build cache are ON** (`gradle.properties`). A Gradle task that reads a file must declare it as an input
    (`providers.fileContents(...)`), or the cache serves stale values.

---

## 19. Local, never-committed configuration

| File | Used by | Why it's local |
|---|---|---|
| `androidApp/google-services.json` | Android | Firebase project identifiers (public by design, but kept out of git so a clone can't point at another project) |
| `iosApp/iosApp/GoogleService-Info.plist` | iOS | same |
| `firebase/web-config.json` | web + desktop | same; template in `firebase/web-config.example.json` |
| `iosApp/Configuration/GoogleSignIn.xcconfig` | iOS | generated from the plist by a small script |
| `local.properties` | Android SDK path | machine-specific |
| `.firebaserc` | Firebase CLI | decide whether to commit; if it names a live project, a stray `firebase use` targets it |

Never commit: **service-account JSON, Admin SDK keys, OAuth client secrets, keystores/passwords.** Public Firebase config values are not secrets,
but they are only safe when the security rules (and ideally App Check) are correct.

---

## 20. Build-type notes

- **Android release:** TechStore ships with `isMinifyEnabled = false` and an empty `proguard-rules.pro` — it builds, but the APK keeps readable class
  names. Enabling R8 is a task of its own: test the scanner (CameraX/ML Kit), GitLive, `kotlinx.serialization` DTOs and Koin on a real
  device, and archive `mapping.txt` for every release.
- **Web release:** the production bundle includes a source map by default (`webApp.js.map`, ~19 MB). Don't publish `*.map` files.
- **Desktop release:** no ProGuard and no code signing configured. Signing/notarization credentials must come from a CI secret store.
- **iOS release:** Release uses `dwarf-with-dsym`; upload dSYMs if you add crash reporting.

---

## 21. Known leftovers in TechStore — do not copy

- `androidApp/build.gradle.kts` has a **version literal** (`implementation("com.google.firebase:firebase-common-ktx:21.0.0")`). Put every version in the
  catalog (or drop it if the BoM already provides it).
- Six unused catalog entries (§16) and `firebase-storage` declared without any code using it.
- Five unused Firebase products linked into the iOS target (§17.8).
- A tracked `.firebaserc` pointing at a live project while a comment in the rules says there is none.

---

## 22. Procedure: adding or changing a dependency

**Before adding** — write this five-line justification in the pull request or in a comment next to the catalog entry:

```
Reason         why the feature cannot be done with what is already there
Targets        which source sets see it (commonMain? androidMain only?)  — camera/OS APIs never go in commonMain
Compatibility  Kotlin / Compose / AGP / GitLive constraints, minSdk, JVM target, does it publish js/jvm/ios artifacts?
Privacy        any data leaving the device, any permission, any store-listing/privacy-label impact
Verification   the §23 commands that were run, and their result
```

**Rules**
1. Prefer a library that publishes **all four targets** (android, ios, jvm, js) for `commonMain`; otherwise it goes in one platform source set behind an
   `expect`/interface in `commonMain` (§9).
2. **One change at a time**, then run §23. Never batch an upgrade with feature work.
3. Upgrading the chain (Kotlin / Compose / AGP / Gradle / GitLive / Firebase BoM): read each project's release notes; change them together on a branch; run §23 on a
   clean checkout (`./gradlew clean` + a fresh `--refresh-dependencies` once); only then merge.
4. Pin exact versions (no `+`, no `latest`, no version ranges). Pre-release builds only when they are already on the list in §15.
5. No new Maven repositories without an explicit decision.
6. Update this playbook (§15, §16) in the same commit as the catalog.

---

## 23. Verification commands (all platforms, every time)

```bash
# 1. Unit tests (shared logic, on the JVM)
./gradlew :shared:jvmTest

# 2. Every platform must still build
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:compileKotlin
./gradlew :webApp:jsBrowserDistribution
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64

# 3. The iOS app itself (needs Xcode; a signed simulator build, ad-hoc signing is enough)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'name=iPhone 17 Pro' CODE_SIGN_IDENTITY=- CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM= build

# 4. Backend security rules (Firebase emulator)
cd firebase/rules-tests && npm install && npm test
```

A change is "done" only when **all of 1–3 pass** (and 4 if rules changed), and the feature has been run at least once on the platform it targets.
Run the desktop app with `./gradlew :desktopApp:run` and the web app with `./gradlew :webApp:jsBrowserDevelopmentRun`.

**Starting a new project from this playbook — order of work**
1. Create the four modules + `settings.gradle.kts`, root build file, `gradle.properties`, wrapper (§17).
2. Paste the catalog (§16). Build **before writing any code**: `:shared:compileKotlinJvm`, `:androidApp:assembleDebug`, `:webApp:jsBrowserDistribution`,
   iOS compile. A clean dependency baseline first means any later failure is yours, not the setup's.
3. Add Firebase config files (§19); initialise Firebase per platform; start Koin once per entry point.
4. Build `core/` (RequestState, SafeCall, DisplayResult, Money, AppClock), then `SessionManager` + `SessionGate`, then one small reference
   feature through the section-12 checklist. Only then build the rest.
