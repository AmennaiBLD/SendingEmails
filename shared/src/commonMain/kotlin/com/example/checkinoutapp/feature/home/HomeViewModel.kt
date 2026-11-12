package com.example.checkinoutapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.event.isPast
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.ClubSettings
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeContent(val featured: List<ClubEvent>, val upcoming: List<ClubEvent>, val past: List<ClubEvent>)

/** The club's public identity (name, contact, links). Shared by every public page's header and footer. */
class ClubInfoViewModel(settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<ClubSettings> = settingsRepository.readClubSettingsFlow()
        .map { it.getSuccessDataOrElse(ClubSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ClubSettings())
}

class HomeViewModel(eventRepository: EventRepository, private val clock: AppClock) : ViewModel() {
    val home: StateFlow<RequestState<HomeContent>> = eventRepository.readPublicEventsFlow(HOME_EVENT_LIMIT)
        .map { state ->
            state.map { events ->
                val now = clock.nowMillis()
                val upcoming = events.filter { !it.isPast(now) }.sortedBy { it.startAt }
                HomeContent(
                    featured = upcoming.filter { it.featured }.take(FEATURED_COUNT),
                    upcoming = upcoming.take(UPCOMING_COUNT),
                    past = events.filter { it.isPast(now) }.sortedByDescending { it.startAt }.take(PAST_COUNT),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    private companion object {
        const val HOME_EVENT_LIMIT = 40
        const val FEATURED_COUNT = 3
        const val UPCOMING_COUNT = 6
        const val PAST_COUNT = 3
    }
}

/** Listeners stop this long after the last collector leaves, so rotation does not re-subscribe. */
const val STOP_TIMEOUT_MILLIS = 5_000L
