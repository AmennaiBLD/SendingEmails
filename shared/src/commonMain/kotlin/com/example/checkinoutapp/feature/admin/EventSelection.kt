package com.example.checkinoutapp.feature.admin

import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

const val ADMIN_EVENT_LIMIT = 200

/**
 * "Which event is this screen about?" — shared by the admin screens that work on one event.
 * Until the administrator picks one, it is the event happening now or most recently started.
 */
class EventSelection(eventRepository: EventRepository, scope: CoroutineScope, private val clock: AppClock) {

    val events: StateFlow<RequestState<List<ClubEvent>>> = eventRepository.readAllEventsFlow(ADMIN_EVENT_LIMIT)
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    private val chosenId = MutableStateFlow<String?>(null)

    val selected: StateFlow<ClubEvent?> = combine(events, chosenId) { state, id ->
        val list = state.getSuccessDataOrElse(emptyList())
        list.firstOrNull { it.id == id } ?: list.firstOrNull { it.startAt <= clock.nowMillis() + SOON_MILLIS } ?: list.firstOrNull()
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun select(eventId: String) {
        chosenId.value = eventId
    }

    private companion object {
        /** An event starting within a day counts as "the current one". */
        const val SOON_MILLIS = 24 * 60 * 60 * 1000L
    }
}

/** Success only when both are; the first error wins; otherwise still loading. */
fun <A, B, R> merge(a: RequestState<A>, b: RequestState<B>, transform: (A, B) -> R): RequestState<R> = when {
    a is RequestState.Error -> a
    b is RequestState.Error -> b
    a is RequestState.Success && b is RequestState.Success -> RequestState.Success(transform(a.data, b.data))
    else -> RequestState.Loading
}
