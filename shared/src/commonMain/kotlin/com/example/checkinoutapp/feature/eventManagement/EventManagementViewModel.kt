package com.example.checkinoutapp.feature.eventManagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.event.EventField
import com.example.checkinoutapp.domain.event.eventFieldOf
import com.example.checkinoutapp.domain.event.validateEventDraft
import com.example.checkinoutapp.domain.model.AppUser
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EVENT_CATEGORIES
import com.example.checkinoutapp.domain.model.EventDraft
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.repository.AssignOutcome
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.EventSaveOutcome
import com.example.checkinoutapp.domain.repository.EventStatusOutcome
import com.example.checkinoutapp.domain.repository.OrganizerRepository
import com.example.checkinoutapp.domain.repository.UserRepository
import com.example.checkinoutapp.domain.time.COMMON_TIME_ZONES
import com.example.checkinoutapp.domain.time.isoDate
import com.example.checkinoutapp.domain.time.isoTime
import com.example.checkinoutapp.domain.time.parseLocalDateTime
import com.example.checkinoutapp.domain.time.toLocal
import com.example.checkinoutapp.feature.admin.ADMIN_EVENT_LIMIT
import com.example.checkinoutapp.feature.admin.merge
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EventFormFailure { INVALID, CAPACITY_BELOW_REGISTRATIONS, FAILED }

/** The event editor. Dates and times are text in the event's own time zone. */
data class EventForm(
    val eventId: String? = null,
    val title: String = "", val description: String = "", val category: String = EVENT_CATEGORIES.first(), val location: String = "",
    val organizerName: String = "", val timezone: String = COMMON_TIME_ZONES.first(),
    val startDate: String = "", val startTime: String = "", val endDate: String = "", val endTime: String = "",
    val openDate: String = "", val openTime: String = "", val closeDate: String = "", val closeTime: String = "",
    val capacity: String = "0", val allowReentry: Boolean = false, val featured: Boolean = false,
    val invalid: Set<EventField> = emptySet(), val saving: Boolean = false, val failure: EventFormFailure? = null,
)

data class OrganizerChoice(val user: AppUser, val assigned: Boolean)

data class OrganizerPanel(val event: ClubEvent, val choices: RequestState<List<OrganizerChoice>>, val busyUid: String? = null)

sealed interface EventManagementEvent {
    data object Saved : EventManagementEvent
    data object StatusChanged : EventManagementEvent
    data object OrganizersUpdated : EventManagementEvent
    data class Refused(val reason: String) : EventManagementEvent
    data object Failed : EventManagementEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class EventManagementViewModel(
    private val eventRepository: EventRepository,
    private val organizerRepository: OrganizerRepository,
    private val userRepository: UserRepository,
    private val clock: AppClock,
) : ViewModel() {

    val events: StateFlow<RequestState<List<ClubEvent>>> = eventRepository.readAllEventsFlow(ADMIN_EVENT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    private val _form = MutableStateFlow<EventForm?>(null)
    val form: StateFlow<EventForm?> = _form.asStateFlow()

    /** The event whose status is being changed, to show progress on its row. */
    private val _busyEventId = MutableStateFlow<String?>(null)
    val busyEventId: StateFlow<String?> = _busyEventId.asStateFlow()

    private val organizerEvent = MutableStateFlow<ClubEvent?>(null)
    private val organizerBusy = MutableStateFlow<String?>(null)

    val organizers: StateFlow<OrganizerPanel?> = organizerEvent.flatMapLatest { event ->
        if (event == null) flowOf(null)
        else combine(userRepository.readStaffFlow(STAFF_LIMIT), organizerRepository.readAssignmentsByEventFlow(event.id), organizerBusy) { staff, assignments, busy ->
            OrganizerPanel(
                event,
                merge(staff, assignments) { users, list ->
                    val active = list.filter { it.active }.map { it.organizerId }.toSet()
                    users.filter { it.isActive }.map { OrganizerChoice(it, it.uid in active) }
                },
                busy,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _events = Channel<EventManagementEvent>(Channel.BUFFERED)
    val notices: Flow<EventManagementEvent> = _events.receiveAsFlow()

    fun openNew() {
        // Sensible defaults: tomorrow 09:00–12:00, registration open now and closing at the start.
        val zone = COMMON_TIME_ZONES.first()
        val now = clock.nowMillis().toLocal(zone)
        val tomorrow = (clock.nowMillis() + DAY).toLocal(zone)
        _form.value = EventForm(
            timezone = zone, startDate = tomorrow.isoDate(), startTime = "09:00", endDate = tomorrow.isoDate(), endTime = "12:00",
            openDate = now.isoDate(), openTime = now.isoTime(), closeDate = tomorrow.isoDate(), closeTime = "09:00",
        )
    }

    fun openEdit(event: ClubEvent) {
        val zone = event.timezone
        fun date(millis: Long) = millis.toLocal(zone).isoDate()
        fun time(millis: Long) = millis.toLocal(zone).isoTime()
        _form.value = EventForm(
            eventId = event.id, title = event.title, description = event.description, category = event.category, location = event.location,
            organizerName = event.organizerName, timezone = zone,
            startDate = date(event.startAt), startTime = time(event.startAt), endDate = date(event.endAt), endTime = time(event.endAt),
            openDate = date(event.registrationOpenAt), openTime = time(event.registrationOpenAt),
            closeDate = date(event.registrationCloseAt), closeTime = time(event.registrationCloseAt),
            capacity = event.capacity.toString(), allowReentry = event.allowReentry, featured = event.featured,
        )
    }

    fun closeForm() { if (_form.value?.saving != true) _form.value = null }

    fun edit(change: (EventForm) -> EventForm) =
        _form.update { form -> form?.takeIf { !it.saving }?.let { change(it).copy(failure = null, invalid = emptySet()) } ?: form }

    fun save() {
        val form = _form.value ?: return
        if (form.saving) return
        val zone = form.timezone
        val draft = EventDraft(
            eventId = form.eventId, title = form.title, description = form.description, category = form.category, location = form.location,
            organizerName = form.organizerName, timezone = zone,
            // An unreadable date becomes 0, which the validator reports against the right field.
            startAt = parseLocalDateTime(form.startDate, form.startTime, zone) ?: 0, endAt = parseLocalDateTime(form.endDate, form.endTime, zone) ?: 0,
            registrationOpenAt = parseLocalDateTime(form.openDate, form.openTime, zone) ?: 0,
            registrationCloseAt = parseLocalDateTime(form.closeDate, form.closeTime, zone) ?: 0,
            capacity = form.capacity.trim().toIntOrNull() ?: -1, allowReentry = form.allowReentry, featured = form.featured,
        )
        val invalid = validateEventDraft(draft)
        if (invalid.isNotEmpty()) {
            _form.value = form.copy(invalid = invalid, failure = EventFormFailure.INVALID)
            return
        }
        _form.value = form.copy(saving = true, failure = null)
        viewModelScope.launch {
            val result = eventRepository.saveEvent(draft = draft)
            if (result.isSuccess()) {
                when (val outcome = result.getSuccessData()) {
                    is EventSaveOutcome.Saved -> {
                        _form.value = null
                        _events.send(EventManagementEvent.Saved)
                    }
                    is EventSaveOutcome.Invalid -> {
                        _form.value = form.copy(saving = false, invalid = outcome.fields.mapNotNull { eventFieldOf(it) }.toSet(), failure = EventFormFailure.INVALID)
                    }
                    EventSaveOutcome.CapacityBelowRegistrations -> {
                        _form.value = form.copy(saving = false, failure = EventFormFailure.CAPACITY_BELOW_REGISTRATIONS)
                    }
                    EventSaveOutcome.NotFound -> _form.value = form.copy(saving = false, failure = EventFormFailure.FAILED)
                }
            } else {
                _form.value = form.copy(saving = false, failure = EventFormFailure.FAILED)
            }
        }
    }

    fun setStatus(event: ClubEvent, status: EventStatus) {
        if (_busyEventId.value != null) return
        _busyEventId.value = event.id
        viewModelScope.launch {
            val result = eventRepository.updateEventStatus(eventId = event.id, status = status)
            _busyEventId.value = null
            if (result.isSuccess()) {
                val outcome = result.getSuccessData()
                if (outcome == EventStatusOutcome.UPDATED) {
                    _events.send(EventManagementEvent.StatusChanged)
                } else {
                    _events.send(EventManagementEvent.Refused(outcome.name))
                }
            } else {
                _events.send(EventManagementEvent.Failed)
            }
        }
    }

    fun openOrganizers(event: ClubEvent) { organizerEvent.value = event }
    fun closeOrganizers() { organizerEvent.value = null }

    fun setAssigned(user: AppUser, assigned: Boolean) {
        val event = organizerEvent.value ?: return
        if (organizerBusy.value != null) return
        organizerBusy.value = user.uid
        viewModelScope.launch {
            val result = organizerRepository.assignOrganizer(
                eventId = event.id,
                organizerId = user.uid,
                active = assigned
            )
            organizerBusy.value = null
            if (result.isSuccess()) {
                val outcome = result.getSuccessData()
                if (outcome != AssignOutcome.UPDATED) _events.send(EventManagementEvent.Refused(outcome.name))
            } else {
                _events.send(EventManagementEvent.Failed)
            }
        }
    }

    private companion object {
        const val STAFF_LIMIT = 200
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
