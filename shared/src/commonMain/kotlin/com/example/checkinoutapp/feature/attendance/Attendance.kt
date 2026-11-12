package com.example.checkinoutapp.feature.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.domain.model.AttendanceStatus
import com.example.checkinoutapp.domain.model.RegistrationStatus
import com.example.checkinoutapp.domain.model.ScanMode
import com.example.checkinoutapp.domain.model.ScanResult
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CellText
import com.example.checkinoutapp.ui.components.CellTwoLines
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.DialogFrame
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.RowActions
import com.example.checkinoutapp.ui.components.SearchField
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.StatCard
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.components.TableColumn
import com.example.checkinoutapp.ui.components.TableHeader
import com.example.checkinoutapp.ui.components.TableRow
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.format.formatDateTime
import com.example.checkinoutapp.ui.format.formatDuration
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.format.tone
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Toasts for manual check-in/out and corrections. Events, not state: each shows once. */
@Composable
fun AttendanceEvents(viewModel: AttendanceViewModel) {
    val snackbar = LocalSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is AttendanceEvent.ManualDone -> getString(if (event.mode == ScanMode.CHECK_IN) Res.string.att_manual_done_in else Res.string.att_manual_done_out, event.name)
                is AttendanceEvent.ManualRefused -> getString(
                    when (event.result) {
                        ScanResult.ALREADY_CHECKED_IN -> Res.string.scan_r_already_in
                        ScanResult.ALREADY_CHECKED_OUT -> Res.string.scan_r_already_out
                        ScanResult.NOT_CHECKED_IN -> Res.string.scan_r_not_in
                        ScanResult.EVENT_NOT_OPEN -> Res.string.scan_r_event_not_open
                        ScanResult.REGISTRATION_CANCELLED -> Res.string.scan_r_cancelled
                        ScanResult.ORGANIZER_NOT_AUTHORIZED -> Res.string.scan_r_not_authorized
                        else -> Res.string.scan_r_unknown
                    },
                )
                AttendanceEvent.ManualFailed -> getString(Res.string.scan_r_network)
                AttendanceEvent.Corrected -> getString(Res.string.corr_done)
            }
            snackbar.showSnackbar(message)
        }
    }
}

/** The session list for one event, with a correction action on every row. */
@Composable
fun AttendanceRecordsPanel(viewModel: AttendanceViewModel, modifier: Modifier = Modifier) {
    val records by viewModel.records.collectAsState()
    val zone by viewModel.timezone.collectAsState()
    val columns = listOf(
        TableColumn(stringResource(Res.string.att_col_participant), 1.6f), TableColumn(stringResource(Res.string.att_col_in), 1.2f),
        TableColumn(stringResource(Res.string.att_col_out), 1.2f), TableColumn(stringResource(Res.string.att_col_duration), 0.7f),
        TableColumn(stringResource(Res.string.att_col_status), 0.9f), TableColumn(stringResource(Res.string.att_col_actions), 0.8f),
    )
    CorrectionDialog(viewModel, zone)
    records.DisplayResult(
        modifier = modifier,
        onLoading = { SkeletonList() },
        onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
        onSuccess = { data ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatCard(stringResource(Res.string.att_summary_inside), data.inside.toString(), Modifier.weight(1f), tone = Tone.Success)
                    StatCard(stringResource(Res.string.att_summary_completed), data.completed.toString(), Modifier.weight(1f))
                    StatCard(stringResource(Res.string.att_summary_total), data.sessions.size.toString(), Modifier.weight(1f), tone = Tone.Neutral)
                }
                if (data.sessions.isEmpty()) {
                    EmptyState(Icons.Outlined.HowToReg, stringResource(Res.string.att_empty_title), body = stringResource(Res.string.att_empty_body))
                } else {
                    TableHeader(columns)
                    Column(verticalArrangement = Arrangement.spacedBy(if (com.example.checkinoutapp.ui.theme.isCompact) Spacing.sm else Spacing.xs)) {
                        data.sessions.forEach { session ->
                            TableRow(
                                columns,
                                cells = listOf(
                                    { CellTwoLines(session.participantName, if (session.manual) stringResource(Res.string.att_manual) else session.department) },
                                    { CellText(formatDateTime(session.checkInAt, zone)) },
                                    { CellText(session.checkOutAt?.let { formatDateTime(it, zone) } ?: stringResource(Res.string.att_still_inside), muted = session.checkOutAt == null) },
                                    { CellText(session.durationSeconds?.let { formatDuration(it) } ?: stringResource(Res.string.common_none), strong = true) },
                                    { StatusChip(session.status.label(), session.status.tone()) },
                                    { ClubButton(stringResource(Res.string.att_correct), { viewModel.openCorrection(session) }, kind = ButtonKind.Ghost, icon = Icons.Outlined.Edit) },
                                ),
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Manual fallback when a ticket cannot be scanned: find the person, check them in or out. */
@Composable
fun ParticipantLookupPanel(viewModel: AttendanceViewModel, modifier: Modifier = Modifier) {
    val search by viewModel.search.collectAsState()
    val query by viewModel.query.collectAsState()
    val busy by viewModel.busyRegistration.collectAsState()
    val columns = listOf(
        TableColumn(stringResource(Res.string.att_col_participant), 1.7f), TableColumn(stringResource(Res.string.att_col_registration), 0.9f),
        TableColumn(stringResource(Res.string.att_col_attendance), 0.9f), TableColumn(stringResource(Res.string.att_col_actions), 1.3f),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SearchField(query, viewModel::setQuery, stringResource(Res.string.att_search_placeholder), Modifier.fillMaxWidth())
        search.DisplayResult(
            onLoading = { SkeletonList() },
            onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
            onSuccess = { data ->
                when {
                    data.total == 0 -> EmptyState(Icons.Outlined.Groups, stringResource(Res.string.att_search_none_title))
                    data.matches.isEmpty() -> EmptyState(Icons.Outlined.PersonSearch, stringResource(Res.string.att_search_empty_title))
                    else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        TableHeader(columns)
                        data.matches.take(MAX_VISIBLE_MATCHES).forEach { registration ->
                            val valid = registration.status == RegistrationStatus.REGISTERED
                            TableRow(
                                columns,
                                cells = listOf(
                                    { CellTwoLines(registration.participantName, registration.email) },
                                    { StatusChip(registration.status.label(), registration.status.tone()) },
                                    { StatusChip(registration.attendanceStatus.label(), registration.attendanceStatus.tone()) },
                                    {
                                        RowActions {
                                            val working = busy == registration.id
                                            if (registration.attendanceStatus == AttendanceStatus.ACTIVE) {
                                                ClubButton(stringResource(Res.string.att_check_out), { viewModel.manual(registration, ScanMode.CHECK_OUT) }, kind = ButtonKind.Secondary, enabled = valid && busy == null, loading = working)
                                            } else {
                                                ClubButton(stringResource(Res.string.att_check_in), { viewModel.manual(registration, ScanMode.CHECK_IN) }, enabled = valid && busy == null, loading = working)
                                            }
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
            },
        )
    }
}

private const val MAX_VISIBLE_MATCHES = 60

@Composable
private fun CorrectionDialog(viewModel: AttendanceViewModel, zone: String) {
    val draft by viewModel.correction.collectAsState()
    val current = draft ?: return
    DialogFrame(
        stringResource(Res.string.corr_title), viewModel::closeCorrection, subtitle = current.session.participantName,
        actions = {
            ClubButton(stringResource(Res.string.common_cancel), viewModel::closeCorrection, kind = ButtonKind.Ghost, enabled = !current.saving)
            ClubButton(stringResource(Res.string.corr_save), viewModel::saveCorrection, loading = current.saving)
        },
    ) {
        Text(stringResource(Res.string.corr_subtitle) + " (" + zone + ")", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val editable = !current.saving
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(current.inDate, viewModel::setInDate, stringResource(Res.string.corr_in_date), Modifier.weight(1f), placeholder = stringResource(Res.string.corr_date_hint), enabled = editable)
            FormField(current.inTime, viewModel::setInTime, stringResource(Res.string.corr_in_time), Modifier.weight(1f), placeholder = stringResource(Res.string.corr_time_hint), enabled = editable)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(current.outDate, viewModel::setOutDate, stringResource(Res.string.corr_out_date), Modifier.weight(1f), placeholder = stringResource(Res.string.corr_date_hint), enabled = editable)
            FormField(current.outTime, viewModel::setOutTime, stringResource(Res.string.corr_out_time), Modifier.weight(1f), placeholder = stringResource(Res.string.corr_time_hint), enabled = editable)
        }
        Text(stringResource(Res.string.corr_out_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FormField(
            current.reason, viewModel::setReason, stringResource(Res.string.corr_reason), singleLine = false, minLines = 2, enabled = editable,
            error = stringResource(Res.string.corr_err_reason).takeIf { current.error == CorrectionError.REASON_REQUIRED },
        )
        when (current.error) {
            CorrectionError.INVALID_TIMES -> Notice(stringResource(Res.string.corr_err_times), Tone.Warning)
            CorrectionError.NOTHING_CHANGED -> Notice(stringResource(Res.string.corr_err_unchanged), Tone.Info)
            CorrectionError.NOT_AUTHORIZED -> Notice(stringResource(Res.string.corr_err_not_authorized), Tone.Danger)
            CorrectionError.FAILED -> Notice(stringResource(Res.string.common_action_failed), Tone.Danger)
            else -> Unit
        }
    }
}
