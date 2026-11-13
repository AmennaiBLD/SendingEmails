package com.example.checkinoutapp.feature.participantManagement

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.DeliveryPreference
import com.example.checkinoutapp.domain.model.RegistrationField
import com.example.checkinoutapp.domain.model.RegistrationStatus
import com.example.checkinoutapp.domain.repository.RegistrationRejection
import com.example.checkinoutapp.feature.admin.EventPicker
import com.example.checkinoutapp.feature.admin.MutedText
import com.example.checkinoutapp.feature.admin.WithSelectedEvent
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CellText
import com.example.checkinoutapp.ui.components.CellTwoLines
import com.example.checkinoutapp.ui.components.CheckRow
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.DialogFrame
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.LabeledValue
import com.example.checkinoutapp.ui.components.LoadingState
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.SearchField
import com.example.checkinoutapp.ui.components.SegmentedControl
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.components.TableColumn
import com.example.checkinoutapp.ui.components.TableHeader
import com.example.checkinoutapp.ui.components.TableRow
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatDateTime
import com.example.checkinoutapp.ui.format.formatDuration
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.format.tone
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ParticipantsScreen() {
    val viewModel = koinViewModel<ParticipantsViewModel>()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is ParticipantEvent.Done -> getString(
                    when (event.action) {
                        ParticipantAction.APPROVE -> Res.string.pt_done_approve
                        ParticipantAction.REJECT -> Res.string.pt_done_reject
                        ParticipantAction.CANCEL -> Res.string.pt_done_cancel
                        ParticipantAction.RESEND -> Res.string.pt_done_resend
                        ParticipantAction.REPLACE -> Res.string.pt_done_replace
                    },
                )
                is ParticipantEvent.Registered -> getString(Res.string.pt_registered, event.name)
                is ParticipantEvent.Refused -> getString(Res.string.ev_refused, event.reason)
                ParticipantEvent.Failed -> getString(Res.string.common_action_failed)
            }
            snackbar.showSnackbar(message)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        EventPicker(viewModel.selection)
        WithSelectedEvent(viewModel.selection) { event ->
            ParticipantDetailDialog(viewModel, event)
            ManualRegistrationDialog(viewModel, event)
            ParticipantTable(viewModel, event)
        }
    }
}

@Composable
private fun ParticipantTable(viewModel: ParticipantsViewModel, event: ClubEvent) {
    val participants by viewModel.participants.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val columns = listOf(
        TableColumn(stringResource(Res.string.pt_col_participant), 1.7f), TableColumn(stringResource(Res.string.pt_col_affiliation), 1.4f),
        TableColumn(stringResource(Res.string.pt_col_registered), 1.1f), TableColumn(stringResource(Res.string.pt_col_status), 0.8f),
        TableColumn(stringResource(Res.string.pt_col_attendance), 0.9f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SearchField(filter.search, viewModel::setSearch, stringResource(Res.string.pt_search), Modifier.widthIn(min = 240.dp, max = 360.dp))
            FilterChip(filter.status == null, { viewModel.setStatus(null) }, { Text(stringResource(Res.string.common_all)) })
            listOf(RegistrationStatus.PENDING, RegistrationStatus.REGISTERED, RegistrationStatus.REJECTED, RegistrationStatus.CANCELLED).forEach { status ->
                FilterChip(filter.status == status, { viewModel.setStatus(status) }, { Text(status.label()) })
            }
            ClubButton(stringResource(Res.string.pt_register), viewModel::openManual, icon = Icons.Outlined.PersonAdd)
        }
        participants.DisplayResult(
            onLoading = { SkeletonList() },
            onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
            onSuccess = { list ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    MutedText(stringResource(Res.string.pt_count, list.shown.size, list.total))
                    when {
                        list.total == 0 -> EmptyState(Icons.Outlined.Groups, stringResource(Res.string.pt_empty_title))
                        list.shown.isEmpty() -> EmptyState(Icons.Outlined.Groups, stringResource(Res.string.pt_empty_filtered))
                        else -> {
                            TableHeader(columns)
                            list.shown.take(MAX_ROWS).forEach { r ->
                                TableRow(
                                    columns, onClick = { viewModel.openDetail(r) },
                                    cells = listOf(
                                        { CellTwoLines(r.participantName, r.email) },
                                        { CellTwoLines(r.faculty, r.department + "  ·  " + r.university) },
                                        { CellText(formatDateTime(r.registeredAt, event.timezone)) },
                                        { StatusChip(r.status.label(), r.status.tone()) },
                                        { StatusChip(r.attendanceStatus.label(), r.attendanceStatus.tone()) },
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

/** Rendering more rows than this at once is not useful; the search narrows the list. */
private const val MAX_ROWS = 300

@Composable
private fun ParticipantDetailDialog(viewModel: ParticipantsViewModel, event: ClubEvent) {
    val state by viewModel.detail.collectAsState()
    val detail = state ?: return
    val r = detail.registration
    val active = r.status == RegistrationStatus.REGISTERED
    val pending = r.status == RegistrationStatus.PENDING
    DialogFrame(
        r.participantName, viewModel::closeDetail, subtitle = r.email, wide = true,
        actions = { ClubButton(stringResource(Res.string.common_close), viewModel::closeDetail, kind = ButtonKind.Secondary, enabled = detail.busy == null) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusChip(r.status.label(), r.status.tone())
            StatusChip(r.attendanceStatus.label(), r.attendanceStatus.tone())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LabeledValue(stringResource(Res.string.pt_phone), r.phone)
                LabeledValue(stringResource(Res.string.pt_university), r.university)
                LabeledValue(stringResource(Res.string.pt_col_affiliation), r.faculty + ", " + r.department)
                if (r.studentId != null) LabeledValue(stringResource(Res.string.pt_student_id), r.studentId)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                LabeledValue(stringResource(Res.string.pt_delivery), r.deliveryPreference.label())
                LabeledValue(
                    stringResource(Res.string.pt_ticket),
                    when {
                        active -> stringResource(Res.string.pt_ticket_version, r.ticketVersion)
                        pending -> stringResource(Res.string.pt_ticket_pending)
                        else -> stringResource(Res.string.pt_ticket_revoked)
                    },
                )
                LabeledValue(stringResource(Res.string.pt_time_attended), if (r.sessionCount == 0) stringResource(Res.string.common_none) else formatDuration(r.totalDurationSeconds))
                LabeledValue(stringResource(Res.string.pt_col_registered), formatDateTime(r.registeredAt, event.timezone))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(Res.string.pt_history), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        when (val history = detail.history) {
            RequestState.Idle, RequestState.Loading -> LoadingState()
            is RequestState.Error -> ErrorState(history.message)
            is RequestState.Success -> {
                val others = history.data.filter { it.registration.id != r.id }
                if (others.isEmpty()) MutedText(stringResource(Res.string.pt_history_none))
                others.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Column(Modifier.weight(1f)) {
                            CellTwoLines(entry.event?.title ?: entry.registration.eventId, entry.event?.let { formatDate(it.startAt, it.timezone) }.orEmpty())
                        }
                        StatusChip(entry.registration.status.label(), entry.registration.status.tone())
                        StatusChip(
                            if (entry.registration.sessionCount == 0) entry.registration.attendanceStatus.label() else formatDuration(entry.registration.totalDurationSeconds),
                            entry.registration.attendanceStatus.tone(),
                        )
                    }
                }
            }
        }
        if (pending) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MutedText(stringResource(Res.string.pt_review_note))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ClubButton(stringResource(Res.string.pt_approve), { viewModel.act(ParticipantAction.APPROVE) }, enabled = detail.busy == null, loading = detail.busy == ParticipantAction.APPROVE)
                ClubButton(stringResource(Res.string.pt_reject), { viewModel.act(ParticipantAction.REJECT) }, kind = ButtonKind.Danger, enabled = detail.busy == null, loading = detail.busy == ParticipantAction.REJECT)
            }
            FormField(detail.cancelReason, viewModel::setCancelReason, stringResource(Res.string.pt_cancel_reason), enabled = detail.busy == null)
            ClubButton(stringResource(Res.string.pt_cancel), { viewModel.act(ParticipantAction.CANCEL) }, kind = ButtonKind.Ghost, enabled = detail.busy == null, loading = detail.busy == ParticipantAction.CANCEL)
        }
        if (active) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ClubButton(stringResource(Res.string.pt_resend), { viewModel.act(ParticipantAction.RESEND) }, kind = ButtonKind.Secondary, enabled = detail.busy == null, loading = detail.busy == ParticipantAction.RESEND)
                ClubButton(stringResource(Res.string.pt_replace), { viewModel.act(ParticipantAction.REPLACE) }, kind = ButtonKind.Secondary, enabled = detail.busy == null, loading = detail.busy == ParticipantAction.REPLACE)
            }
            MutedText(stringResource(Res.string.pt_replace_note))
            FormField(detail.cancelReason, viewModel::setCancelReason, stringResource(Res.string.pt_cancel_reason), enabled = detail.busy == null)
            ClubButton(stringResource(Res.string.pt_cancel), { viewModel.act(ParticipantAction.CANCEL) }, kind = ButtonKind.Danger, enabled = detail.busy == null, loading = detail.busy == ParticipantAction.CANCEL)
        }
    }
}

@Composable
private fun ManualRegistrationDialog(viewModel: ParticipantsViewModel, event: ClubEvent) {
    val state by viewModel.manual.collectAsState()
    val manual = state ?: return
    val form = manual.form
    val editable = !manual.saving
    val required = stringResource(Res.string.rg_err_required)
    @Composable fun err(field: RegistrationField, text: String = required): String? = if (field in manual.invalid) text else null
    DialogFrame(
        stringResource(Res.string.pt_manual_title), viewModel::closeManual, subtitle = event.title,
        actions = {
            ClubButton(stringResource(Res.string.common_cancel), viewModel::closeManual, kind = ButtonKind.Ghost, enabled = editable)
            ClubButton(stringResource(Res.string.pt_register), viewModel::saveManual, loading = manual.saving)
        },
    ) {
        MutedText(stringResource(Res.string.pt_manual_sub))
        FormField(form.fullName, { v -> viewModel.editManual { it.copy(fullName = v.take(120)) } }, stringResource(Res.string.rg_full_name), error = err(RegistrationField.FULL_NAME, stringResource(Res.string.rg_err_full_name)), enabled = editable)
        FormField(form.email, { v -> viewModel.editManual { it.copy(email = v.take(254)) } }, stringResource(Res.string.rg_email), error = err(RegistrationField.EMAIL, stringResource(Res.string.rg_err_email)), enabled = editable, keyboardType = KeyboardType.Email)
        FormField(form.phone, { v -> viewModel.editManual { it.copy(phone = v.take(24)) } }, stringResource(Res.string.rg_phone), error = err(RegistrationField.PHONE, stringResource(Res.string.rg_err_phone)), supporting = stringResource(Res.string.rg_phone_hint), enabled = editable, keyboardType = KeyboardType.Phone)
        FormField(form.university, { v -> viewModel.editManual { it.copy(university = v.take(120)) } }, stringResource(Res.string.rg_university), error = err(RegistrationField.UNIVERSITY), enabled = editable)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(form.faculty, { v -> viewModel.editManual { it.copy(faculty = v.take(120)) } }, stringResource(Res.string.rg_faculty), Modifier.weight(1f), error = err(RegistrationField.FACULTY), enabled = editable)
            FormField(form.department, { v -> viewModel.editManual { it.copy(department = v.take(120)) } }, stringResource(Res.string.rg_department), Modifier.weight(1f), error = err(RegistrationField.DEPARTMENT), enabled = editable)
        }
        SegmentedControl(DeliveryPreference.entries, form.deliveryPreference, label = { it.label() }, onSelect = { v -> viewModel.editManual { it.copy(deliveryPreference = v) } })
        if (form.deliveryPreference.usesWhatsApp) {
            CheckRow(form.whatsappConsent, { v -> viewModel.editManual { it.copy(whatsappConsent = v) } }, stringResource(Res.string.rg_whatsapp_consent), error = RegistrationField.WHATSAPP_CONSENT in manual.invalid, enabled = editable)
        }
        manual.rejection?.let { reason ->
            Notice(
                stringResource(
                    when (reason) {
                        RegistrationRejection.ALREADY_REGISTERED -> Res.string.rg_rejected_already
                        RegistrationRejection.EVENT_FULL -> Res.string.rg_rejected_full
                        RegistrationRejection.REGISTRATION_CLOSED -> Res.string.rg_rejected_closed
                        else -> Res.string.rg_rejected_unavailable
                    },
                ),
                Tone.Warning,
            )
        }
        if (manual.failed) Notice(stringResource(Res.string.common_action_failed), Tone.Danger)
    }
}
