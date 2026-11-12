package com.example.checkinoutapp.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.analytics.computeClubAnalytics
import com.example.checkinoutapp.domain.analytics.computeEventAnalytics
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.ClubSettings
import com.example.checkinoutapp.domain.report.attendanceCsv
import com.example.checkinoutapp.domain.report.eventAnalyticsCsv
import com.example.checkinoutapp.domain.report.eventComparisonCsv
import com.example.checkinoutapp.domain.report.eventReportPdf
import com.example.checkinoutapp.domain.report.participantsCsv
import com.example.checkinoutapp.domain.report.sessionsCsv
import com.example.checkinoutapp.domain.repository.AttendanceRepository
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.domain.repository.SettingsRepository
import com.example.checkinoutapp.feature.admin.EventSelection
import com.example.checkinoutapp.feature.adminDashboard.ANALYTICS_SESSION_LIMIT
import com.example.checkinoutapp.feature.adminDashboard.ANALYTICS_WINDOW_MILLIS
import com.example.checkinoutapp.feature.analytics.EVENT_DATA_LIMIT
import com.example.checkinoutapp.ui.platform.FileSaver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class ReportKind(val needsEvent: Boolean) {
    ATTENDANCE_CSV(true), PARTICIPANTS_CSV(true), SESSIONS_CSV(true), ANALYTICS_CSV(true), EVENT_PDF(true), COMPARISON_CSV(false),
}

sealed interface ReportEvent {
    data class Saved(val fileName: String) : ReportEvent
    data class LoadFailed(val message: String) : ReportEvent
    data object SaveFailed : ReportEvent
}

private const val CSV = "text/csv"
private const val PDF = "application/pdf"

/**
 * Builds a report from the stored data at the moment it is asked for, then hands the bytes to
 * the platform to save. Nothing is sampled or invented: an event with no attendance exports
 * a report that says so.
 */
class ReportsViewModel(
    eventRepository: EventRepository,
    private val registrationRepository: RegistrationRepository,
    private val attendanceRepository: AttendanceRepository,
    private val settingsRepository: SettingsRepository,
    private val fileSaver: FileSaver,
    private val clock: AppClock,
) : ViewModel() {

    val selection = EventSelection(eventRepository, viewModelScope, clock)

    private val _busy = MutableStateFlow<ReportKind?>(null)
    val busy: StateFlow<ReportKind?> = _busy.asStateFlow()

    private val _events = Channel<ReportEvent>(Channel.BUFFERED)
    val events: Flow<ReportEvent> = _events.receiveAsFlow()

    /** The first settled value of a live read: the data as it is right now. */
    private suspend fun <T> Flow<RequestState<T>>.now(): T = when (val state = first { it is RequestState.Success || it is RequestState.Error }) {
        is RequestState.Success -> state.data
        is RequestState.Error -> error(state.message)
        else -> error("")
    }

    fun export(kind: ReportKind) {
        if (_busy.value != null) return
        val event = selection.selected.value
        if (kind.needsEvent && event == null) return
        _busy.value = kind
        viewModelScope.launch {
            val built = runCatching { build(kind, event) }
            val outcome = built.fold(
                onSuccess = { (name, mime, bytes) ->
                    runCatching { fileSaver.save(name, mime, bytes) }.fold({ saved -> if (saved) ReportEvent.Saved(name) else null }, { ReportEvent.SaveFailed })
                },
                onFailure = { ReportEvent.LoadFailed(it.message.orEmpty()) },
            )
            _busy.value = null
            if (outcome != null) _events.send(outcome)
        }
    }

    private suspend fun build(kind: ReportKind, event: ClubEvent?): Triple<String, String, ByteArray> {
        if (kind == ReportKind.COMPARISON_CSV) {
            val events = selection.events.now()
            val sessions = attendanceRepository.readSessionsSinceFlow(clock.nowMillis() - ANALYTICS_WINDOW_MILLIS, ANALYTICS_SESSION_LIMIT).now()
            return Triple("events-comparison.csv", CSV, eventComparisonCsv(computeClubAnalytics(events, sessions)).encodeToByteArray())
        }
        requireNotNull(event)
        val slug = event.title.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').take(40).ifBlank { "event" }
        val registrations = registrationRepository.readRegistrationsByEventFlow(event.id, EVENT_DATA_LIMIT).now()
        if (kind == ReportKind.PARTICIPANTS_CSV) return Triple("$slug-participants.csv", CSV, participantsCsv(event, registrations).encodeToByteArray())
        val sessions = attendanceRepository.readSessionsByEventFlow(event.id, EVENT_DATA_LIMIT).now()
        if (kind == ReportKind.SESSIONS_CSV) return Triple("$slug-sessions.csv", CSV, sessionsCsv(event, sessions).encodeToByteArray())
        val settings = runCatching { settingsRepository.readClubSettingsFlow().now() }.getOrDefault(ClubSettings())
        val analytics = computeEventAnalytics(event, registrations, sessions, settings.attendanceGraceMinutes)
        return when (kind) {
            ReportKind.ATTENDANCE_CSV -> Triple("$slug-attendance.csv", CSV, attendanceCsv(analytics).encodeToByteArray())
            ReportKind.ANALYTICS_CSV -> Triple("$slug-analytics.csv", CSV, eventAnalyticsCsv(analytics).encodeToByteArray())
            else -> Triple("$slug-report.pdf", PDF, eventReportPdf(analytics, settings.clubName, clock.nowMillis()))
        }
    }
}
