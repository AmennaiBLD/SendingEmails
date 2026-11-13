package com.example.checkinoutapp.feature.eventManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EVENT_CATEGORIES
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.time.COMMON_TIME_ZONES
import com.example.checkinoutapp.domain.event.EventField
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CellText
import com.example.checkinoutapp.ui.components.CellTwoLines
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.DialogFrame
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.LoadingState
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.RowActions
import com.example.checkinoutapp.ui.components.SelectField
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.components.SwitchRow
import com.example.checkinoutapp.ui.components.TableColumn
import com.example.checkinoutapp.ui.components.TableHeader
import com.example.checkinoutapp.ui.components.TableRow
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatTimeRange
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.format.tone
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
private fun EventStatus.actionLabel(): String = stringResource(
    when (this) {
        EventStatus.PUBLISHED -> Res.string.ev_to_published
        EventStatus.DRAFT -> Res.string.ev_to_draft
        EventStatus.CANCELLED -> Res.string.ev_to_cancelled
        EventStatus.COMPLETED -> Res.string.ev_to_completed
        EventStatus.ARCHIVED -> Res.string.ev_to_archived
    },
)

@Composable
fun EventManagementScreen() {
    val viewModel = koinViewModel<EventManagementViewModel>()
    val events by viewModel.events.collectAsState()
    val busyId by viewModel.busyEventId.collectAsState()
    val snackbar = LocalSnackbar.current

    LaunchedEffect(viewModel) {
        viewModel.notices.collect { notice ->
            val message = when (notice) {
                EventManagementEvent.Saved -> getString(Res.string.ev_saved)
                EventManagementEvent.StatusChanged -> getString(Res.string.ev_status_changed)
                EventManagementEvent.OrganizersUpdated -> getString(Res.string.common_saved)
                is EventManagementEvent.Refused -> getString(Res.string.ev_refused, notice.reason)
                EventManagementEvent.Failed -> getString(Res.string.common_action_failed)
            }
            snackbar.showSnackbar(message)
        }
    }
    EventFormDialog(viewModel)

    OrganizersDialog(viewModel)

    val columns = listOf(
        TableColumn(stringResource(Res.string.ev_col_event), 1.8f), TableColumn(stringResource(Res.string.ev_col_when), 1.3f),

        TableColumn(stringResource(Res.string.ev_col_registered), 0.8f), TableColumn(stringResource(Res.string.ev_col_status), 0.8f),

        TableColumn(stringResource(Res.string.ev_col_actions), 2.1f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ClubButton(stringResource(Res.string.ev_new), viewModel::openNew, icon = Icons.Outlined.Add)
        }

        events.DisplayResult(
            onLoading = { SkeletonList() },
            onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
            onSuccess = { list ->
                if (list.isEmpty()) {
                    EmptyState(Icons.Outlined.Event, stringResource(Res.string.common_no_events_title), body = stringResource(Res.string.ev_empty_body))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        TableHeader(columns)
                        list.forEach { event ->
                            TableRow(
                                columns,
                                cells = listOf(
                                    { CellTwoLines(event.title, event.category + "  ·  " + event.location) },
                                    { CellTwoLines(formatDate(event.startAt, event.timezone), formatTimeRange(event.startAt, event.endAt, event.timezone)) },
                                    { CellText(if (event.capacity > 0) "${event.registrationCount} / ${event.capacity}" else event.registrationCount.toString(), strong = true) },
                                    { StatusChip(event.status.label(), event.status.tone()) },
                                    { EventActions(event, busy = busyId == event.id, enabled = busyId == null, viewModel) },
                                ),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun EventActions(event: ClubEvent, busy: Boolean, enabled: Boolean, viewModel: EventManagementViewModel) {
    var menu by remember { mutableStateOf(false) }
    RowActions {
        ClubButton(stringResource(Res.string.ev_edit), { viewModel.openEdit(event) }, kind = ButtonKind.Ghost, enabled = enabled)
        ClubButton(stringResource(Res.string.ev_organizers), { viewModel.openOrganizers(event) }, kind = ButtonKind.Ghost, enabled = enabled)
        if (event.status.nextStatuses.isNotEmpty()) {
            Box {
                ClubButton(stringResource(Res.string.ev_change_status), { menu = true }, kind = ButtonKind.Secondary, enabled = enabled, loading = busy)
                DropdownMenu(menu, { menu = false }) {
                    event.status.nextStatuses.forEach { next ->
                        DropdownMenuItem(text = { Text(next.actionLabel()) }, onClick = { menu = false; viewModel.setStatus(event, next) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EventFormDialog(viewModel: EventManagementViewModel) {
    val state by viewModel.form.collectAsState()
    val form = state ?: return
    val editable = !form.saving
    val dateHint = stringResource(Res.string.corr_date_hint)
    val timeHint = stringResource(Res.string.corr_time_hint)
    @Composable fun err(field: EventField, text: String = stringResource(Res.string.ev_err_field)): String? = if (field in form.invalid) text else null

    DialogFrame(
        stringResource(if (form.eventId == null) Res.string.ev_form_new else Res.string.ev_form_edit), viewModel::closeForm, wide = true,
        actions = {
            ClubButton(stringResource(Res.string.common_cancel), viewModel::closeForm, kind = ButtonKind.Ghost, enabled = editable)
            ClubButton(stringResource(Res.string.common_save), viewModel::save, loading = form.saving)
        },
    ) {
        FormField(form.title, { v -> viewModel.edit { it.copy(title = v.take(140)) } }, stringResource(Res.string.ev_f_title), error = err(EventField.TITLE), enabled = editable)
        FormField(form.description, { v -> viewModel.edit { it.copy(description = v.take(6000)) } }, stringResource(Res.string.ev_f_description), error = err(EventField.DESCRIPTION), enabled = editable, singleLine = false, minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SelectField(stringResource(Res.string.ev_f_category), form.category, EVENT_CATEGORIES, { it }, { v -> viewModel.edit { it.copy(category = v) } }, Modifier.weight(1f), enabled = editable)
            SelectField(stringResource(Res.string.ev_f_timezone), form.timezone, COMMON_TIME_ZONES, { it }, { v -> viewModel.edit { it.copy(timezone = v) } }, Modifier.weight(1f), enabled = editable)
        }
        FormField(form.location, { v -> viewModel.edit { it.copy(location = v.take(200)) } }, stringResource(Res.string.ev_f_location), error = err(EventField.LOCATION), enabled = editable)
        FormField(form.organizerName, { v -> viewModel.edit { it.copy(organizerName = v.take(120)) } }, stringResource(Res.string.ev_f_organizer), enabled = editable)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(form.startDate, { v -> viewModel.edit { it.copy(startDate = v.take(10)) } }, stringResource(Res.string.ev_f_start_date), Modifier.weight(1f), placeholder = dateHint, error = err(EventField.START), enabled = editable)
            FormField(form.startTime, { v -> viewModel.edit { it.copy(startTime = v.take(5)) } }, stringResource(Res.string.ev_f_start_time), Modifier.weight(1f), placeholder = timeHint, enabled = editable)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(form.endDate, { v -> viewModel.edit { it.copy(endDate = v.take(10)) } }, stringResource(Res.string.ev_f_end_date), Modifier.weight(1f), placeholder = dateHint, error = err(EventField.END, stringResource(Res.string.ev_err_end)), enabled = editable)
            FormField(form.endTime, { v -> viewModel.edit { it.copy(endTime = v.take(5)) } }, stringResource(Res.string.ev_f_end_time), Modifier.weight(1f), placeholder = timeHint, enabled = editable)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(form.openDate, { v -> viewModel.edit { it.copy(openDate = v.take(10)) } }, stringResource(Res.string.ev_f_open_date), Modifier.weight(1f), placeholder = dateHint, error = err(EventField.REGISTRATION_OPEN), enabled = editable)
            FormField(form.openTime, { v -> viewModel.edit { it.copy(openTime = v.take(5)) } }, stringResource(Res.string.ev_f_open_time), Modifier.weight(1f), placeholder = timeHint, enabled = editable)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(form.closeDate, { v -> viewModel.edit { it.copy(closeDate = v.take(10)) } }, stringResource(Res.string.ev_f_close_date), Modifier.weight(1f), placeholder = dateHint, error = err(EventField.REGISTRATION_CLOSE, stringResource(Res.string.ev_err_close)), enabled = editable)
            FormField(form.closeTime, { v -> viewModel.edit { it.copy(closeTime = v.take(5)) } }, stringResource(Res.string.ev_f_close_time), Modifier.weight(1f), placeholder = timeHint, enabled = editable)
        }
        FormField(
            form.capacity, { v -> viewModel.edit { it.copy(capacity = v.filter(Char::isDigit).take(6)) } }, stringResource(Res.string.ev_f_capacity),
            supporting = stringResource(Res.string.ev_f_capacity_hint), error = err(EventField.CAPACITY), enabled = editable, keyboardType = KeyboardType.Number,
        )
        SwitchRow(form.allowReentry, { v -> viewModel.edit { it.copy(allowReentry = v) } }, stringResource(Res.string.ev_f_reentry), subtitle = stringResource(Res.string.ev_f_reentry_sub), enabled = editable)
        SwitchRow(form.featured, { v -> viewModel.edit { it.copy(featured = v) } }, stringResource(Res.string.ev_f_featured), enabled = editable)
        when (form.failure) {
            EventFormFailure.INVALID -> Notice(stringResource(Res.string.ev_err_invalid), Tone.Warning)
            EventFormFailure.CAPACITY_BELOW_REGISTRATIONS -> Notice(stringResource(Res.string.ev_err_capacity_below), Tone.Warning)
            EventFormFailure.FAILED -> Notice(stringResource(Res.string.common_action_failed), Tone.Danger)
            null -> Unit
        }
    }
}

@Composable
private fun OrganizersDialog(viewModel: EventManagementViewModel) {
    val state by viewModel.organizers.collectAsState()
    val panel = state ?: return
    DialogFrame(
        stringResource(Res.string.ev_org_title), viewModel::closeOrganizers, subtitle = panel.event.title,
        actions = { ClubButton(stringResource(Res.string.common_close), viewModel::closeOrganizers, kind = ButtonKind.Secondary) },
    ) {
        Text(stringResource(Res.string.ev_org_sub), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        when (val choices = panel.choices) {
            RequestState.Idle, RequestState.Loading -> LoadingState()
            is RequestState.Error -> ErrorState(choices.message)
            is RequestState.Success -> {
                if (choices.data.isEmpty()) Notice(stringResource(Res.string.ev_org_none), Tone.Info)
                choices.data.forEach { choice ->
                    SwitchRow(
                        choice.assigned, { viewModel.setAssigned(choice.user, it) }, choice.user.fullName,
                        subtitle = choice.user.email + "  ·  " + choice.user.role.label(), enabled = panel.busyUid == null,
                    )
                }
            }
        }
    }
}
