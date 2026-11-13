package com.example.checkinoutapp.feature.adminDashboard

import com.example.checkinoutapp.navigation.visibleAdminSections
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.feature.admin.MutedText
import com.example.checkinoutapp.feature.admin.Panel
import com.example.checkinoutapp.feature.admin.AdminSection
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CardGrid
import com.example.checkinoutapp.ui.components.CellTwoLines
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.StatCard
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatDateTime
import com.example.checkinoutapp.ui.format.formatDuration
import com.example.checkinoutapp.ui.format.formatPercent
import com.example.checkinoutapp.ui.theme.LocalWidthClass
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.WidthClass
import com.example.checkinoutapp.ui.theme.isCompact
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun statColumns(): Int = when (LocalWidthClass.current) {
    WidthClass.Compact -> 2
    WidthClass.Medium -> 2
    else -> 4
}

@Composable
fun DashboardScreen(navigateToSection: (AdminSection) -> Unit) {
    val viewModel = koinViewModel<DashboardViewModel>()
    val dashboard by viewModel.dashboard.collectAsState()
    dashboard.DisplayResult(
        onLoading = { SkeletonList(rows = 3, rowHeight = androidx.compose.ui.unit.Dp(110f)) },
        onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
        onSuccess = { content ->
            val club = content.club
            val none = stringResource(Res.string.dash_no_data)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                // Every figure is derived from stored events and sessions; a missing one says so instead of showing 0.
                val stats = listOf<@Composable (Modifier) -> Unit>(
                    { StatCard(stringResource(Res.string.dash_total_events), club.totalEvents.toString(), it, Icons.Outlined.Event) },
                    { StatCard(stringResource(Res.string.dash_total_registrations), club.totalRegistrations.toString(), it, Icons.Outlined.Groups) },
                    { StatCard(stringResource(Res.string.dash_total_attendees), club.totalAttendees.toString(), it, Icons.Outlined.HowToReg, tone = Tone.Success) },
                    { StatCard(stringResource(Res.string.dash_attendance_rate), formatPercent(club.attendanceRate) ?: none, it, Icons.Outlined.Percent, tone = Tone.Success) },
                    { StatCard(stringResource(Res.string.dash_completed_checkouts), club.completedCheckouts.toString(), it, Icons.AutoMirrored.Outlined.Logout) },
                    { StatCard(stringResource(Res.string.dash_average_time), formatDuration(club.averageSessionSeconds) ?: none, it, Icons.Outlined.Schedule) },
                    { StatCard(stringResource(Res.string.dash_median_time), formatDuration(club.medianSessionSeconds) ?: none, it, Icons.Outlined.Timeline, hint = stringResource(Res.string.dash_median_hint)) },
                    { StatCard(stringResource(Res.string.dash_incomplete), club.incompleteSessions.toString(), it, Icons.Outlined.HourglassEmpty, hint = stringResource(Res.string.dash_incomplete_hint), tone = Tone.Warning) },
                )
                CardGrid(stats, statColumns()) { stat, modifier -> stat(modifier) }

                CardGrid(listOf(0, 1), if (isCompact) 1 else 2) { index, modifier ->
                    if (index == 0) {
                        Panel(stringResource(Res.string.dash_live), modifier) {
                            if (content.live.isEmpty()) MutedText(stringResource(Res.string.dash_live_none))
                            content.live.forEach { event ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(event.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    StatusChip(stringResource(Res.string.dash_live_inside, event.activeSessionCount), Tone.Success)
                                }
                            }
                        }
                    } else {
                        Panel(stringResource(Res.string.dash_upcoming), modifier, action = { if (AdminSection.EVENTS in visibleAdminSections) ClubButton(stringResource(Res.string.dash_view_all), { navigateToSection(AdminSection.EVENTS) }, kind = ButtonKind.Ghost) }) {
                            if (content.upcoming.isEmpty()) MutedText(stringResource(Res.string.dash_upcoming_none))
                            content.upcoming.forEach { event ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { CellTwoLines(event.title, formatDate(event.startAt, event.timezone) + "  ·  " + event.location) }
                                    StatusChip(
                                        if (event.capacity > 0) stringResource(Res.string.events_capacity, event.registrationCount, event.capacity)
                                        else stringResource(Res.string.events_registered_count, event.registrationCount),
                                        Tone.Info,
                                    )
                                }
                            }
                        }
                    }
                }

                CardGrid(listOf(0, 1), if (isCompact) 1 else 2) { index, modifier ->
                    if (index == 0) {
                        Panel(stringResource(Res.string.dash_recent), modifier, action = { if (AdminSection.PARTICIPANTS in visibleAdminSections) ClubButton(stringResource(Res.string.dash_view_all), { navigateToSection(AdminSection.PARTICIPANTS) }, kind = ButtonKind.Ghost) }) {
                            if (content.recent.isEmpty()) MutedText(stringResource(Res.string.dash_recent_none))
                            content.recent.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    Column(Modifier.weight(1f)) { CellTwoLines(item.registration.participantName, item.eventTitle) }
                                    Text(formatDateTime(item.registration.registeredAt, item.timezone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        Panel(stringResource(Res.string.dash_failed), modifier, action = { if (AdminSection.NOTIFICATIONS in visibleAdminSections) ClubButton(stringResource(Res.string.dash_view_all), { navigateToSection(AdminSection.NOTIFICATIONS) }, kind = ButtonKind.Ghost) }) {
                            if (content.failedNotifications.isEmpty()) MutedText(stringResource(Res.string.dash_failed_none))
                            content.failedNotifications.forEach { job ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    Column(Modifier.weight(1f)) { CellTwoLines(job.participantName, job.lastError ?: job.recipient) }
                                    StatusChip(job.channel.name, Tone.Danger)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
