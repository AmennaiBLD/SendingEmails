package com.example.checkinoutapp.feature.organizerHome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.UserRole
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.OrganizerRepository
import com.example.checkinoutapp.domain.session.SessionManager
import com.example.checkinoutapp.domain.session.SessionState
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** An event the signed-in person may run attendance for. */
data class WorkspaceEvent(
    val eventId: String, val title: String, val location: String, val startAt: Long, val endAt: Long, val timezone: String, val status: EventStatus,
)

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizerHomeViewModel(
    sessionManager: SessionManager,
    private val organizerRepository: OrganizerRepository,
    private val eventRepository: EventRepository,
) : ViewModel() {

    private val active = sessionManager.state.map { it as? SessionState.Active }.distinctUntilChanged { a, b -> a?.uid == b?.uid && a?.role == b?.role }

    val isAdmin: StateFlow<Boolean> = active.map { it?.role == UserRole.ADMIN }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Organizers see their assignments; administrators see every published or completed event. */
    val events: StateFlow<RequestState<List<WorkspaceEvent>>> = active
        .flatMapLatest { session ->
            when {
                session == null -> flowOf(RequestState.Success(emptyList()))
                session.role == UserRole.ADMIN -> eventRepository.readPublicEventsFlow(EVENT_LIMIT).map { state ->
                    state.map { events -> events.map { WorkspaceEvent(it.id, it.title, it.location, it.startAt, it.endAt, it.timezone, it.status) } }
                }
                else -> organizerRepository.readAssignmentsByOrganizerFlow(session.uid, EVENT_LIMIT).map { state ->
                    state.map { list -> list.map { WorkspaceEvent(it.eventId, it.eventTitle, it.eventLocation, it.eventStartAt, it.eventEndAt, it.eventTimezone, it.eventStatus) } }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    private companion object {
        const val EVENT_LIMIT = 50
    }
}
