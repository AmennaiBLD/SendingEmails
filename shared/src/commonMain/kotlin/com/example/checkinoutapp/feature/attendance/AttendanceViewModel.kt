package com.example.checkinoutapp.feature.attendance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.AttendanceSession
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.model.ScanMode
import com.example.checkinoutapp.domain.model.ScanResult
import com.example.checkinoutapp.domain.repository.AttendanceRepository
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.domain.time.isoDate
import com.example.checkinoutapp.domain.time.isoTime
import com.example.checkinoutapp.domain.time.parseLocalDateTime
import com.example.checkinoutapp.domain.time.toLocal
import com.example.checkinoutapp.domain.repository.CorrectionOutcome
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttendanceRecords(val sessions: List<AttendanceSession>, val inside: Int, val completed: Int)

data class ParticipantSearch(val matches: List<Registration>, val total: Int)

enum class CorrectionError { REASON_REQUIRED, INVALID_TIMES, NOTHING_CHANGED, NOT_AUTHORIZED, FAILED }

/** The correction dialog. Dates and times are typed in the event's own time zone. */
data class CorrectionDraft(
    val session: AttendanceSession,
    val inDate: String, val inTime: String, val outDate: String, val outTime: String,
    val reason: String = "", val saving: Boolean = false, val error: CorrectionError? = null,
)

sealed interface AttendanceEvent {
    data class ManualDone(val name: String, val mode: ScanMode) : AttendanceEvent
    data class ManualRefused(val result: ScanResult) : AttendanceEvent
    data object ManualFailed : AttendanceEvent
    data object Corrected : AttendanceEvent
}

/** One event's attendance: the session list, the participant lookup, manual check-in/out and corrections. */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(
    savedStateHandle: SavedStateHandle,
    private val attendanceRepository: AttendanceRepository,
    private val registrationRepository: RegistrationRepository,
    private val eventRepository: EventRepository,
) : ViewModel() {

    /** From the route on the organizer's workspace; chosen with [setEvent] on the admin page. */
    private val eventId = MutableStateFlow(savedStateHandle.get<String>("id") ?: "")

    fun setEvent(id: String) {
        eventId.value = id
    }

    val timezone: StateFlow<String> = eventId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(RequestState.Loading) else eventRepository.readEventByIdFlow(id) }
        .map { it.getSuccessDataOrNull()?.timezone ?: "UTC" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "UTC")

    val records: StateFlow<RequestState<AttendanceRecords>> = eventId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(RequestState.Loading) else attendanceRepository.readSessionsByEventFlow(id, SESSION_LIMIT) }
        .map { state -> state.map { sessions -> AttendanceRecords(sessions, sessions.count { !it.status.isClosed }, sessions.count { it.status.isClosed }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val search: StateFlow<RequestState<ParticipantSearch>> = eventId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(RequestState.Loading) else registrationRepository.readRegistrationsByEventFlow(id, REGISTRATION_LIMIT) }
        .combine(_query) { state, query ->
            state.map { registrations ->
                val needle = query.trim().lowercase()
                val matches = if (needle.isEmpty()) registrations else registrations.filter {
                    it.participantName.lowercase().contains(needle) || it.normalizedEmail.contains(needle) || it.phone.contains(needle)
                }
                ParticipantSearch(matches.sortedBy { it.participantName.lowercase() }, registrations.size)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    /** The registration a manual check-in/out is running for, to show progress on its row. */
    private val _busyRegistration = MutableStateFlow<String?>(null)
    val busyRegistration: StateFlow<String?> = _busyRegistration.asStateFlow()

    private val _correction = MutableStateFlow<CorrectionDraft?>(null)
    val correction: StateFlow<CorrectionDraft?> = _correction.asStateFlow()

    private val _events = Channel<AttendanceEvent>(Channel.BUFFERED)
    val events: Flow<AttendanceEvent> = _events.receiveAsFlow()

    fun setQuery(text: String) { _query.value = text.take(80) }

    fun manual(registration: Registration, mode: ScanMode) {
        if (_busyRegistration.value != null) return
        _busyRegistration.value = registration.id
        viewModelScope.launch {
            val result = attendanceRepository.scan(
                mode = mode,
                eventId = eventId.value,
                qr = null,
                registrationId = registration.id
            )
            val event = if (result.isSuccess()) {
                val outcome = result.getSuccessData()
                if (outcome.result.isSuccess) AttendanceEvent.ManualDone(registration.participantName, mode) else AttendanceEvent.ManualRefused(outcome.result)
            } else {
                AttendanceEvent.ManualFailed
            }
            _busyRegistration.value = null
            _events.send(event)
        }
    }

    fun openCorrection(session: AttendanceSession) {
        val zone = timezone.value
        val checkIn = session.checkInAt.toLocal(zone)
        val checkOut = session.checkOutAt?.toLocal(zone)
        _correction.value = CorrectionDraft(session, checkIn.isoDate(), checkIn.isoTime(), checkOut?.isoDate().orEmpty(), checkOut?.isoTime().orEmpty())
    }

    fun closeCorrection() { if (_correction.value?.saving != true) _correction.value = null }

    private fun editCorrection(change: (CorrectionDraft) -> CorrectionDraft) =
        _correction.update { draft -> draft?.takeIf { !it.saving }?.let { change(it).copy(error = null) } ?: draft }

    fun setInDate(value: String) = editCorrection { it.copy(inDate = value.take(10)) }
    fun setInTime(value: String) = editCorrection { it.copy(inTime = value.take(5)) }
    fun setOutDate(value: String) = editCorrection { it.copy(outDate = value.take(10)) }
    fun setOutTime(value: String) = editCorrection { it.copy(outTime = value.take(5)) }
    fun setReason(value: String) = editCorrection { it.copy(reason = value.take(500)) }

    fun saveCorrection() {
        val draft = _correction.value ?: return
        if (draft.saving) return
        val zone = timezone.value
        val newIn = parseLocalDateTime(draft.inDate, draft.inTime, zone)
        val outBlank = draft.outDate.isBlank() && draft.outTime.isBlank()
        val newOut = if (outBlank) null else parseLocalDateTime(draft.outDate, draft.outTime, zone)
        if (newIn == null || (!outBlank && newOut == null)) {
            _correction.value = draft.copy(error = CorrectionError.INVALID_TIMES)
            return
        }
        // Only what actually changed is sent (to the minute, which is what the form shows).
        val changedIn = newIn.takeIf { it / MINUTE != draft.session.checkInAt / MINUTE }
        val changedOut = newOut?.takeIf { it / MINUTE != (draft.session.checkOutAt ?: -1) / MINUTE }
        if (draft.reason.trim().length < MIN_REASON_LENGTH) {
            _correction.value = draft.copy(error = CorrectionError.REASON_REQUIRED)
            return
        }
        if (changedIn == null && changedOut == null) {
            _correction.value = draft.copy(error = CorrectionError.NOTHING_CHANGED)
            return
        }
        if (changedOut != null && changedOut <= (changedIn ?: draft.session.checkInAt)) {
            _correction.value = draft.copy(error = CorrectionError.INVALID_TIMES)
            return
        }
        _correction.value = draft.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = attendanceRepository.correctAttendance(
                sessionId = draft.session.id,
                checkInAt = changedIn,
                checkOutAt = changedOut,
                reason = draft.reason.trim()
            )
            if (result.isSuccess()) {
                when (result.getSuccessData()) {
                    CorrectionOutcome.CORRECTED -> {
                        _correction.value = null
                        _events.send(AttendanceEvent.Corrected)
                    }
                    CorrectionOutcome.REASON_REQUIRED -> failCorrection(draft, CorrectionError.REASON_REQUIRED)
                    CorrectionOutcome.INVALID_TIMES -> failCorrection(draft, CorrectionError.INVALID_TIMES)
                    CorrectionOutcome.NOT_AUTHORIZED -> failCorrection(draft, CorrectionError.NOT_AUTHORIZED)
                    CorrectionOutcome.SESSION_NOT_FOUND, CorrectionOutcome.UNKNOWN -> failCorrection(draft, CorrectionError.FAILED)
                }
            } else {
                failCorrection(draft, CorrectionError.FAILED)
            }
        }
    }

    /** The dialog stays open with what was typed. */
    private fun failCorrection(draft: CorrectionDraft, error: CorrectionError) { _correction.value = draft.copy(saving = false, error = error) }

    private companion object {
        const val SESSION_LIMIT = 500
        const val REGISTRATION_LIMIT = 500
        const val MINUTE = 60_000L
        const val MIN_REASON_LENGTH = 3
    }
}
