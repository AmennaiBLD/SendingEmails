package com.example.checkinoutapp.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.domain.analytics.Bucket
import com.example.checkinoutapp.domain.analytics.ClubAnalytics
import com.example.checkinoutapp.domain.analytics.EventAnalytics
import com.example.checkinoutapp.domain.analytics.LabelCount
import com.example.checkinoutapp.feature.admin.EventPicker
import com.example.checkinoutapp.feature.admin.MutedText
import com.example.checkinoutapp.feature.admin.Panel
import com.example.checkinoutapp.feature.admin.WithSelectedEvent
import com.example.checkinoutapp.feature.adminDashboard.statColumns
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.BarList
import com.example.checkinoutapp.ui.components.CardGrid
import com.example.checkinoutapp.ui.components.CellText
import com.example.checkinoutapp.ui.components.CellTwoLines
import com.example.checkinoutapp.ui.components.ChartBar
import com.example.checkinoutapp.ui.components.ColumnChart
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.SegmentBar
import com.example.checkinoutapp.ui.components.SegmentedControl
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.StatCard
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.components.TableColumn
import com.example.checkinoutapp.ui.components.TableHeader
import com.example.checkinoutapp.ui.components.TableRow
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.components.toneColors
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatDateTime
import com.example.checkinoutapp.ui.format.formatDuration
import com.example.checkinoutapp.ui.format.formatPercent
import com.example.checkinoutapp.ui.format.formatTime
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.format.monthName
import com.example.checkinoutapp.ui.format.tone
import com.example.checkinoutapp.ui.theme.LocalStatusColors
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.isCompact
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AnalyticsScreen() {
    val viewModel = koinViewModel<AnalyticsViewModel>()
    val scope by viewModel.scope.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        SegmentedControl(
            AnalyticsScope.entries, scope,
            label = { stringResource(if (it == AnalyticsScope.EVENT) Res.string.an_scope_event else Res.string.an_scope_club) }, onSelect = viewModel::setScope,
        )
        if (scope == AnalyticsScope.EVENT) {
            EventPicker(viewModel.selection)
            WithSelectedEvent(viewModel.selection) {
                val analytics by viewModel.event.collectAsState()
                analytics.DisplayResult(
                    onLoading = { SkeletonList(rows = 3) },
                    onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
                    onSuccess = { data -> if (data != null) EventAnalyticsContent(data) },
                )
            }
        } else {
            val club by viewModel.club.collectAsState()
            club.DisplayResult(
                onLoading = { SkeletonList(rows = 3) },
                onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
                onSuccess = { ClubAnalyticsContent(it) },
            )
        }
    }
}

@Composable
private fun Chart(title: String, subtitle: String, bars: List<ChartBar>, modifier: Modifier) {
    Panel(title, modifier, subtitle = subtitle) {
        if (bars.isEmpty()) MutedText(stringResource(Res.string.dash_no_data))
        else ColumnChart(bars, stringResource(Res.string.an_chart_summary, title, bars.size, bars.maxBy { it.value }.label))
    }
}

@Composable
private fun EventAnalyticsContent(a: EventAnalytics) {
    val zone = a.event.timezone
    val none = stringResource(Res.string.dash_no_data)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        val stats = listOf<@Composable (Modifier) -> Unit>(
            { StatCard(stringResource(Res.string.an_registrations), a.validRegistrations.toString(), it) },
            { StatCard(stringResource(Res.string.an_attendees), a.uniqueAttendees.toString(), it, tone = Tone.Success) },
            { StatCard(stringResource(Res.string.an_rate), formatPercent(a.attendanceRate) ?: none, it) },
            { StatCard(stringResource(Res.string.an_completed), a.completedSessions.toString(), it) },
            { StatCard(stringResource(Res.string.an_avg_session), formatDuration(a.averageSessionSeconds) ?: none, it) },
            { StatCard(stringResource(Res.string.an_median_session), formatDuration(a.medianSessionSeconds) ?: none, it) },
            { StatCard(stringResource(Res.string.an_avg_participant), formatDuration(a.averageParticipantSeconds) ?: none, it) },
            { StatCard(stringResource(Res.string.an_incomplete), a.incompleteSessions.toString(), it) },
        )
        CardGrid(stats, statColumns()) { stat, modifier -> stat(modifier) }
        if (a.incompleteSessions > 0) Notice(stringResource(Res.string.an_incomplete_note), Tone.Warning)

        if (!a.hasAttendance) {
            // No attendance: say so. No chart is drawn from nothing.
            EmptyState(Icons.Outlined.Insights, stringResource(Res.string.an_no_attendance_title), body = stringResource(Res.string.an_no_attendance_body, a.validRegistrations))
            return@Column
        }

        val status = LocalStatusColors.current
        val columns = if (isCompact) 1 else 2
        fun times(buckets: List<Bucket>) = buckets.map { ChartBar(formatTime(it.start, zone), it.count.toFloat()) }
        val charts = listOf<@Composable (Modifier) -> Unit>(
            {
                Panel(stringResource(Res.string.an_outcomes), it, subtitle = stringResource(Res.string.an_outcomes_sub, formatDuration(a.event.scheduledDurationSeconds), a.graceMinutes)) {
                    SegmentBar(
                        listOf(
                            Triple(stringResource(Res.string.outcome_full), a.fullAttendance, status.success),
                            Triple(stringResource(Res.string.outcome_partial), a.partialAttendance, MaterialTheme.colorScheme.primary),
                            Triple(stringResource(Res.string.outcome_early), a.earlyDepartures, MaterialTheme.colorScheme.error),
                            Triple(stringResource(Res.string.outcome_missing_checkout), a.missingCheckout, status.warning),
                            Triple(stringResource(Res.string.outcome_absent), a.absent, MaterialTheme.colorScheme.outline),
                        ),
                    )
                }
            },
            { Chart(stringResource(Res.string.an_durations), stringResource(Res.string.an_durations_sub), a.durations.map { b -> ChartBar(formatDuration(b.start), b.count.toFloat()) }, it) },
            { Chart(stringResource(Res.string.an_arrivals), stringResource(Res.string.an_arrivals_sub), times(a.arrivals), it) },
            { Chart(stringResource(Res.string.an_departures), stringResource(Res.string.an_departures_sub), times(a.departures), it) },
            { Chart(stringResource(Res.string.an_registrations_over_time), stringResource(Res.string.an_registrations_over_time_sub), a.registrationsByDay.map { b -> ChartBar(formatDate(b.start, zone), b.count.toFloat()) }, it) },
            { Breakdown(stringResource(Res.string.an_by_faculty), a.byFaculty, it) },
            { Breakdown(stringResource(Res.string.an_by_department), a.byDepartment, it) },
        )
        CardGrid(charts, columns) { chart, modifier -> chart(modifier) }

        val table = listOf(
            TableColumn(stringResource(Res.string.pt_col_participant), 1.7f), TableColumn(stringResource(Res.string.an_col_first_in), 1.2f),
            TableColumn(stringResource(Res.string.an_col_last_out), 1.2f), TableColumn(stringResource(Res.string.an_col_sessions), 0.6f),
            TableColumn(stringResource(Res.string.an_col_time), 0.7f), TableColumn(stringResource(Res.string.an_col_outcome), 1f),
        )
        Panel(stringResource(Res.string.an_participants)) {
            TableHeader(table)
            a.participants.take(MAX_PARTICIPANT_ROWS).forEach { p ->
                TableRow(
                    table,
                    cells = listOf(
                        { CellTwoLines(p.registration.participantName, p.registration.department) },
                        { CellText(p.firstCheckInAt?.let { formatDateTime(it, zone) } ?: stringResource(Res.string.common_none)) },
                        { CellText(p.lastCheckOutAt?.let { formatDateTime(it, zone) } ?: stringResource(Res.string.common_none)) },
                        { CellText(p.sessionCount.toString()) },
                        { CellText(if (p.sessionCount == 0) stringResource(Res.string.common_none) else formatDuration(p.completedSeconds), strong = true) },
                        { StatusChip(p.outcome.label(), p.outcome.tone()) },
                    ),
                )
            }
        }
    }
}

private const val MAX_PARTICIPANT_ROWS = 300
private const val MAX_BREAKDOWN_ROWS = 8

@Composable
private fun Breakdown(title: String, counts: List<LabelCount>, modifier: Modifier) {
    Panel(title, modifier) {
        if (counts.isEmpty()) MutedText(stringResource(Res.string.dash_no_data))
        else BarList(counts.take(MAX_BREAKDOWN_ROWS).map { ChartBar(it.label, it.count.toFloat()) })
    }
}

@Composable
private fun ClubAnalyticsContent(club: ClubAnalytics) {
    val none = stringResource(Res.string.dash_no_data)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        val stats = listOf<@Composable (Modifier) -> Unit>(
            { StatCard(stringResource(Res.string.dash_total_events), club.totalEvents.toString(), it) },
            { StatCard(stringResource(Res.string.dash_total_registrations), club.totalRegistrations.toString(), it) },
            { StatCard(stringResource(Res.string.dash_total_attendees), club.totalAttendees.toString(), it, tone = Tone.Success) },
            { StatCard(stringResource(Res.string.dash_attendance_rate), formatPercent(club.attendanceRate) ?: none, it) },
            { StatCard(stringResource(Res.string.dash_completed_checkouts), club.completedCheckouts.toString(), it) },
            { StatCard(stringResource(Res.string.dash_average_time), formatDuration(club.averageSessionSeconds) ?: none, it) },
            { StatCard(stringResource(Res.string.dash_median_time), formatDuration(club.medianSessionSeconds) ?: none, it, hint = stringResource(Res.string.dash_median_hint)) },
            { StatCard(stringResource(Res.string.dash_incomplete), club.incompleteSessions.toString(), it) },
        )
        CardGrid(stats, statColumns()) { stat, modifier -> stat(modifier) }

        val charts = listOf<@Composable (Modifier) -> Unit>(
            {
                Panel(stringResource(Res.string.an_most_attended), it) {
                    if (club.mostAttended.isEmpty()) MutedText(stringResource(Res.string.an_none_yet))
                    else BarList(club.mostAttended.map { c -> ChartBar(c.event.title, c.event.attendeeCount.toFloat()) })
                }
            },
            {
                Panel(stringResource(Res.string.an_longest_stays), it) {
                    if (club.longestStays.isEmpty()) MutedText(stringResource(Res.string.an_none_yet))
                    else BarList(club.longestStays.map { c -> ChartBar(c.event.title, c.averageSessionSeconds!!.toFloat(), formatDuration(c.averageSessionSeconds).orEmpty()) }, color = toneColors(Tone.Info).second)
                }
            },
            { Chart(stringResource(Res.string.an_monthly), stringResource(Res.string.an_monthly_sub), club.months.map { m -> ChartBar("${monthName(m.month)} ${m.year}", m.attendees.toFloat()) }.takeIf { club.totalAttendees > 0 }.orEmpty(), it) },
            { Chart(stringResource(Res.string.an_daily), stringResource(Res.string.an_daily_sub), club.arrivalsByDay.map { b -> ChartBar(formatDate(b.start, "UTC"), b.count.toFloat()) }, it) },
        )
        CardGrid(charts, if (isCompact) 1 else 2) { chart, modifier -> chart(modifier) }

        val table = listOf(
            TableColumn(stringResource(Res.string.ev_col_event), 1.8f), TableColumn(stringResource(Res.string.ev_col_registered), 0.8f),
            TableColumn(stringResource(Res.string.an_attendees), 0.8f), TableColumn(stringResource(Res.string.an_rate), 0.8f),
            TableColumn(stringResource(Res.string.an_col_avg), 0.8f), TableColumn(stringResource(Res.string.an_incomplete), 0.8f),
        )
        Panel(stringResource(Res.string.an_compare)) {
            if (club.comparisons.isEmpty()) MutedText(stringResource(Res.string.an_none_yet))
            else {
                TableHeader(table)
                club.comparisons.forEach { c ->
                    TableRow(
                        table,
                        cells = listOf(
                            { CellTwoLines(c.event.title, formatDate(c.event.startAt, c.event.timezone)) },
                            { CellText(c.event.registrationCount.toString()) },
                            { CellText(c.event.attendeeCount.toString()) },
                            { CellText(formatPercent(c.attendanceRate) ?: stringResource(Res.string.common_none), strong = true) },
                            { CellText(formatDuration(c.averageSessionSeconds) ?: stringResource(Res.string.common_none)) },
                            { CellText(c.event.activeSessionCount.toString()) },
                        ),
                    )
                }
            }
        }
    }
}
