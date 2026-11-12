package com.example.checkinoutapp.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.event.EventQuery
import com.example.checkinoutapp.domain.event.EventTimeFilter
import com.example.checkinoutapp.domain.event.categoriesOf
import com.example.checkinoutapp.domain.event.filterEvents
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class EventsContent(
    val events: List<ClubEvent>,
    val categories: List<String>,
    /** The server may hold more events than are loaded: offer "Show more". */
    val canLoadMore: Boolean,
    val now: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModel(private val eventRepository: EventRepository, private val clock: AppClock) : ViewModel() {

    private val _query = MutableStateFlow(EventQuery())
    val query: StateFlow<EventQuery> = _query.asStateFlow()

    private val limit = MutableStateFlow(PAGE_SIZE)

    val events: StateFlow<RequestState<EventsContent>> = limit
        .flatMapLatest { size ->
            combine(eventRepository.readPublicEventsFlow(size), _query) { state, query ->
                state.map { loaded ->
                    val now = clock.nowMillis()
                    EventsContent(filterEvents(loaded, query, now), categoriesOf(loaded), canLoadMore = loaded.size >= size, now = now)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    fun setSearch(text: String) = _query.update { it.copy(search = text.take(SEARCH_LIMIT)) }
    fun setCategory(category: String?) = _query.update { it.copy(category = category) }
    fun setTime(filter: EventTimeFilter) = _query.update { it.copy(time = filter) }
    fun loadMore() = limit.update { it + PAGE_SIZE }

    private companion object {
        const val PAGE_SIZE = 24
        const val SEARCH_LIMIT = 80
    }
}
