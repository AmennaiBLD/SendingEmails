package com.example.checkinoutapp.feature.participantManagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.analytics.ParticipantHistoryEntry
import com.example.checkinoutapp.domain.analytics.participantHistory
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.model.RegistrationField
import com.example.checkinoutapp.domain.model.RegistrationForm
import com.example.checkinoutapp.domain.model.RegistrationStatus
import com.example.checkinoutapp.domain.registration.cleanRegistrationForm
import com.example.checkinoutapp.domain.registration.validateRegistration
import com.example.checkinoutapp.domain.repository.CancelOutcome
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.RegistrationOutcome
import com.example.checkinoutapp.domain.repository.ResendOutcome
import com.example.checkinoutapp.domain.repository.ReviewOutcome
import com.example.checkinoutapp.domain.repository.RegistrationRejection
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.feature.admin.EventSelection
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParticipantFilter(val search: String = "", val status: RegistrationStatus? = null)

data class ParticipantList(val shown: List<Registration>, val total: Int)

enum class ParticipantAction { APPROVE, REJECT, CANCEL, RESEND, REPLACE }

/** One participant's panel: their registration, their history across events, and the actions on it. */
data class ParticipantDetail(
    val registration: Registration,
    val history: RequestState<List<ParticipantHistoryEntry>> = RequestState.Loading,
    val cancelReason: String = "",
    val busy: ParticipantAction? = null,
)

data class ManualRegistration(
    val form: RegistrationForm = RegistrationForm(),
    val invalid: Set<RegistrationField> = emptySet(),
    val saving: Boolean = false,
    val rejection: RegistrationRejection? = null,
    val failed: Boolean = false,
)

sealed interface ParticipantEvent {
    data class Done(val action: ParticipantAction) : ParticipantEvent
    data class Registered(val name: String) : ParticipantEvent
    data class Refused(val reason: String) : ParticipantEvent
    data object Failed : ParticipantEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class ParticipantsViewModel(
    eventRepository: EventRepository,
    private val registrationRepository: RegistrationRepository,
    clock: AppClock,
) : ViewModel() {

    val selection = EventSelection(eventRepository, viewModelScope, clock)

    private val _filter = MutableStateFlow(ParticipantFilter())
    val filter: StateFlow<ParticipantFilter> = _filter.asStateFlow()

    val participants: StateFlow<RequestState<ParticipantList>> = selection.selected.map { it?.id }.distinctUntilChanged()
        .flatMapLatest { eventId ->
            if (eventId == null) flowOf(RequestState.Success(ParticipantList(emptyList(), 0)))
            else combine(registrationRepository.readRegistrationsByEventFlow(eventId, REGISTRATION_LIMIT), _filter) { state, filter ->
                state.map { all ->
                    val needle = filter.search.trim().lowercase()
                    ParticipantList(
                        all.filter { r ->
                            (filter.status == null || r.status == filter.status) &&
                                (needle.isEmpty() || r.participantName.lowercase().contains(needle) || r.normalizedEmail.contains(needle) ||
                                    r.phone.contains(needle) || r.faculty.lowercase().contains(needle) || r.department.lowercase().contains(needle))
                        },
                        all.size,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    private val detailBase = MutableStateFlow<ParticipantDetail?>(null)

    val detail: StateFlow<ParticipantDetail?> = detailBase.flatMapLatest { base ->
        if (base == null) flowOf(null)
        else combine(registrationRepository.readRegistrationsByEmailFlow(base.registration.normalizedEmail, HISTORY_LIMIT), selection.events) { history, events ->
            // Keep the panel's registration in step with the live document (status, ticket version).
            val live = history.getSuccessDataOrNull()?.firstOrNull { it.id == base.registration.id } ?: base.registration
            base.copy(registration = live, history = history.map { participantHistory(it, events.getSuccessDataOrElse(emptyList())) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _manual = MutableStateFlow<ManualRegistration?>(null)
    val manual: StateFlow<ManualRegistration?> = _manual.asStateFlow()

    private val _events = Channel<ParticipantEvent>(Channel.BUFFERED)
    val events: Flow<ParticipantEvent> = _events.receiveAsFlow()

    fun setSearch(text: String) = _filter.update { it.copy(search = text.take(80)) }
    fun setStatus(status: RegistrationStatus?) = _filter.update { it.copy(status = status) }

    fun openDetail(registration: Registration) { detailBase.value = ParticipantDetail(registration) }
    fun closeDetail() { if (detailBase.value?.busy == null) detailBase.value = null }
    fun setCancelReason(text: String) = detailBase.update { it?.copy(cancelReason = text.take(300)) }

    fun act(action: ParticipantAction) {
        val current = detailBase.value ?: return
        if (current.busy != null) return
        detailBase.value = current.copy(busy = action)
        viewModelScope.launch {
            val id = current.registration.id
            // Each action answers with a named outcome; `accepted` says whether that outcome is the one asked for.
            val event = when (action) {
                ParticipantAction.APPROVE, ParticipantAction.REJECT -> {
                    val approve = action == ParticipantAction.APPROVE
                    val result = registrationRepository.reviewRegistration(registrationId = id, approve = approve)
                    if (result.isSuccess()) {
                        val outcome = result.getSuccessData()
                        val wanted = if (approve) ReviewOutcome.APPROVED else ReviewOutcome.REJECTED
                        if (outcome == wanted) ParticipantEvent.Done(action) else ParticipantEvent.Refused(outcome.name)
                    } else {
                        ParticipantEvent.Failed
                    }
                }
                ParticipantAction.CANCEL -> {
                    val result = registrationRepository.cancelRegistration(registrationId = id, reason = current.cancelReason.trim())
                    if (result.isSuccess()) {
                        val outcome = result.getSuccessData()
                        if (outcome == CancelOutcome.CANCELLED || outcome == CancelOutcome.ALREADY_CANCELLED) ParticipantEvent.Done(action) else ParticipantEvent.Refused(outcome.name)
                    } else {
                        ParticipantEvent.Failed
                    }
                }
                ParticipantAction.RESEND, ParticipantAction.REPLACE -> {
                    val result = registrationRepository.resendTicket(registrationId = id, replace = action == ParticipantAction.REPLACE)
                    if (result.isSuccess()) {
                        val outcome = result.getSuccessData()
                        if (outcome == ResendOutcome.QUEUED) ParticipantEvent.Done(action) else ParticipantEvent.Refused(outcome.name)
                    } else {
                        ParticipantEvent.Failed
                    }
                }
            }
            detailBase.update { it?.copy(busy = null, cancelReason = "") }
            _events.send(event)
        }
    }

    fun openManual() { _manual.value = ManualRegistration() }
    fun closeManual() { if (_manual.value?.saving != true) _manual.value = null }
    fun editManual(change: (RegistrationForm) -> RegistrationForm) =
        _manual.update { m -> m?.takeIf { !it.saving }?.let { it.copy(form = change(it.form), invalid = emptySet(), rejection = null, failed = false) } ?: m }

    fun saveManual() {
        val current = _manual.value ?: return
        val eventId = selection.selected.value?.id ?: return
        if (current.saving) return
        // An administrator registering someone has collected their consent offline.
        val form = current.form.copy(privacyAccepted = true)
        val invalid = validateRegistration(form)
        if (invalid.isNotEmpty()) {
            _manual.value = current.copy(invalid = invalid)
            return
        }
        _manual.value = current.copy(saving = true)
        viewModelScope.launch {
            val result = registrationRepository.register(
                eventId = eventId,
                form = cleanRegistrationForm(form),
                asAdmin = true
            )
            if (result.isSuccess()) {
                when (val outcome = result.getSuccessData()) {
                    is RegistrationOutcome.Registered -> {
                        _manual.value = null
                        _events.send(ParticipantEvent.Registered(outcome.ticket.participantName))
                    }
                    is RegistrationOutcome.Invalid -> _manual.value = current.copy(saving = false, invalid = outcome.fields)
                    is RegistrationOutcome.Rejected -> _manual.value = current.copy(saving = false, rejection = outcome.reason)
                }
            } else {
                _manual.value = current.copy(saving = false, failed = true)
            }
        }
    }

    private companion object {
        const val REGISTRATION_LIMIT = 1000
        const val HISTORY_LIMIT = 50
    }
}
