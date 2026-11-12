package com.example.checkinoutapp.feature.adminDashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.analytics.ClubAnalytics
import com.example.checkinoutapp.domain.analytics.computeClubAnalytics
import com.example.checkinoutapp.domain.event.isPast
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.NotificationJob
import com.example.checkinoutapp.domain.model.NotificationStatus
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.repository.AttendanceRepository
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.NotificationRepository
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.feature.admin.ADMIN_EVENT_LIMIT
import com.example.checkinoutapp.feature.admin.merge
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RecentRegistration(val registration: Registration, val eventTitle: String, val timezone: String)

data class DashboardContent(
    val club: ClubAnalytics,
    val upcoming: List<ClubEvent>,
    /** Events with people checked in right now. */
    val live: List<ClubEvent>,
    val recent: List<RecentRegistration>,
    val failedNotifications: List<NotificationJob>,
)

/** How far back the dashboard's session window reaches (for the club-wide median). */
const val ANALYTICS_WINDOW_MILLIS = 180L * 24 * 60 * 60 * 1000
const val ANALYTICS_SESSION_LIMIT = 3000

class DashboardViewModel(
    eventRepository: EventRepository,
    registrationRepository: RegistrationRepository,
    notificationRepository: NotificationRepository,
    attendanceRepository: AttendanceRepository,
    private val clock: AppClock,
) : ViewModel() {

    val dashboard: StateFlow<RequestState<DashboardContent>> = combine(
        eventRepository.readAllEventsFlow(ADMIN_EVENT_LIMIT),
        attendanceRepository.readSessionsSinceFlow(clock.nowMillis() - ANALYTICS_WINDOW_MILLIS, ANALYTICS_SESSION_LIMIT),
        registrationRepository.readRecentRegistrationsFlow(RECENT_COUNT),
        notificationRepository.readNotificationsFlow(NotificationStatus.FAILED, FAILED_COUNT),
    ) { events, sessions, recent, failed ->
        merge(merge(events, sessions) { e, s -> e to s }, merge(recent, failed) { r, f -> r to f }) { (eventList, sessionList), (recentList, failedList) ->
            val now = clock.nowMillis()
            val byId = eventList.associateBy { it.id }
            DashboardContent(
                club = computeClubAnalytics(eventList, sessionList),
                upcoming = eventList.filter { it.status == EventStatus.PUBLISHED && !it.isPast(now) }.sortedBy { it.startAt }.take(UPCOMING_COUNT),
                live = eventList.filter { it.activeSessionCount > 0 },
                recent = recentList.map { RecentRegistration(it, byId[it.eventId]?.title.orEmpty(), byId[it.eventId]?.timezone ?: "UTC") },
                failedNotifications = failedList,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    private companion object {
        const val RECENT_COUNT = 8
        const val FAILED_COUNT = 5
        const val UPCOMING_COUNT = 4
    }
}
