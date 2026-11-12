package com.example.checkinoutapp.feature.analytics

import androidx.compose.ui.graphics.Path.Companion.combine
import androidx.compose.ui.text.style.TextDecoration.Companion.combine
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.analytics.ClubAnalytics
import com.example.checkinoutapp.domain.analytics.EventAnalytics
import com.example.checkinoutapp.domain.analytics.computeClubAnalytics
import com.example.checkinoutapp.domain.analytics.computeEventAnalytics
import com.example.checkinoutapp.domain.model.ClubSettings
import com.example.checkinoutapp.domain.repository.AttendanceRepository
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.domain.repository.SettingsRepository
import com.example.checkinoutapp.feature.admin.EventSelection
import com.example.checkinoutapp.feature.admin.merge
import com.example.checkinoutapp.feature.adminDashboard.ANALYTICS_SESSION_LIMIT
import com.example.checkinoutapp.feature.adminDashboard.ANALYTICS_WINDOW_MILLIS
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

enum class AnalyticsScope { EVENT, CLUB }

/** Reads for one event are capped; a larger event would need server-side aggregation. */
const val EVENT_DATA_LIMIT = 5000

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    eventRepository: EventRepository,
    private val registrationRepository: RegistrationRepository,
    private val attendanceRepository: AttendanceRepository,
    settingsRepository: SettingsRepository,
    clock: AppClock,
) : ViewModel() {

    val selection = EventSelection(eventRepository, viewModelScope, clock)

    private val _scope = MutableStateFlow(AnalyticsScope.EVENT)
    val scope: StateFlow<AnalyticsScope> = _scope.asStateFlow()
    fun setScope(scope: AnalyticsScope) { _scope.value = scope }

    val event: StateFlow<RequestState<EventAnalytics?>> = selection.selected.flatMapLatest { selected ->
        if (selected == null) flowOf(RequestState.Success(null))
        else combine(
            registrationRepository.readRegistrationsByEventFlow(selected.id, EVENT_DATA_LIMIT),
            attendanceRepository.readSessionsByEventFlow(selected.id, EVENT_DATA_LIMIT),
            settingsRepository.readClubSettingsFlow(),
        ) { registrations, sessions, settings ->
            val grace = settings.getSuccessDataOrElse(ClubSettings()).attendanceGraceMinutes
            merge(registrations, sessions) { r, s -> computeEventAnalytics(selected, r, s, grace) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    val club: StateFlow<RequestState<ClubAnalytics>> = combine(
        selection.events,
        attendanceRepository.readSessionsSinceFlow(clock.nowMillis() - ANALYTICS_WINDOW_MILLIS, ANALYTICS_SESSION_LIMIT),
    ) { events, sessions -> merge(events, sessions) { e, s -> computeClubAnalytics(e, s) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)
}
