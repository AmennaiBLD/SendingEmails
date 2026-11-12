package com.example.checkinoutapp.feature.notifications

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.domain.model.NotificationChannel
import com.example.checkinoutapp.domain.model.NotificationJob
import com.example.checkinoutapp.domain.model.NotificationKind
import com.example.checkinoutapp.domain.model.NotificationStatus
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CellText
import com.example.checkinoutapp.ui.components.CellTwoLines
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.DialogFrame
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.LabeledValue
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.RowActions
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.components.TableColumn
import com.example.checkinoutapp.ui.components.TableHeader
import com.example.checkinoutapp.ui.components.TableRow
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.format.formatDateTime
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private fun NotificationStatus.resource() = when (this) {
    NotificationStatus.PENDING -> Res.string.nt_status_pending
    NotificationStatus.PROCESSING -> Res.string.nt_status_processing
    NotificationStatus.SENT -> Res.string.nt_status_sent
    NotificationStatus.DELIVERED -> Res.string.nt_status_delivered
    NotificationStatus.FAILED -> Res.string.nt_status_failed
    NotificationStatus.RETRYING -> Res.string.nt_status_retrying
}

private fun NotificationStatus.tone(): Tone = when (this) {
    NotificationStatus.SENT, NotificationStatus.DELIVERED -> Tone.Success
    NotificationStatus.FAILED -> Tone.Danger
    NotificationStatus.RETRYING -> Tone.Warning
    else -> Tone.Neutral
}

@Composable
private fun NotificationChannel.label(): String = stringResource(if (this == NotificationChannel.EMAIL) Res.string.nt_channel_email else Res.string.nt_channel_whatsapp)

/** UTC: delivery times are operational facts, not tied to any one event's zone. */
private fun stamp(millis: Long): String = formatDateTime(millis, "UTC") + " UTC"

@Composable
fun NotificationsScreen() {
    val viewModel = koinViewModel<NotificationsViewModel>()
    val jobs by viewModel.jobs.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val retrying by viewModel.retrying.collectAsState()
    val snackbar = LocalSnackbar.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is NotificationsEvent.Retried -> getString(Res.string.nt_retried, getString(event.status.resource()))
                NotificationsEvent.Failed -> getString(Res.string.common_action_failed)
            }
            snackbar.showSnackbar(message)
        }
    }
    DetailDialog(viewModel)

    val columns = listOf(
        TableColumn(stringResource(Res.string.nt_col_recipient), 1.7f), TableColumn(stringResource(Res.string.nt_col_channel), 0.8f),
        TableColumn(stringResource(Res.string.nt_col_event), 1.3f), TableColumn(stringResource(Res.string.nt_col_status), 1.1f),
        TableColumn(stringResource(Res.string.nt_col_created), 1.1f), TableColumn(stringResource(Res.string.nt_col_actions), 1.2f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(filter == null, { viewModel.setFilter(null) }, { Text(stringResource(Res.string.nt_filter_all)) })
            listOf(NotificationStatus.FAILED, NotificationStatus.RETRYING, NotificationStatus.PENDING, NotificationStatus.SENT, NotificationStatus.DELIVERED).forEach { status ->
                FilterChip(filter == status, { viewModel.setFilter(status) }, { Text(stringResource(status.resource())) })
            }
        }
        jobs.DisplayResult(
            onLoading = { SkeletonList() },
            onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
            onSuccess = { list ->
                if (list.isEmpty()) EmptyState(Icons.Outlined.NotificationsNone, stringResource(Res.string.nt_empty))
                else Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    // Be plain about mock deliveries: "sent" here did not reach anyone.
                    if (list.any { it.isSimulated }) Notice(stringResource(Res.string.nt_simulated_note), Tone.Info)
                    TableHeader(columns)
                    list.forEach { job ->
                        TableRow(
                            columns,
                            cells = listOf(
                                { CellTwoLines(job.participantName, job.recipient) },
                                { CellText(job.channel.label()) },
                                { CellText(job.eventTitle) },
                                {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        StatusChip(stringResource(job.status.resource()), job.status.tone())
                                        if (job.isSimulated) StatusChip(stringResource(Res.string.nt_simulated), Tone.Warning)
                                    }
                                },
                                { CellText(stamp(job.createdAt), muted = true) },
                                {
                                    RowActions {
                                        ClubButton(stringResource(Res.string.nt_details), { viewModel.openDetail(job) }, kind = ButtonKind.Ghost)
                                        if (job.status.canRetry) {
                                            ClubButton(stringResource(Res.string.nt_retry), { viewModel.retry(job) }, kind = ButtonKind.Secondary, enabled = retrying == null, loading = retrying == job.id)
                                        }
                                    }
                                },
                            ),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun DetailDialog(viewModel: NotificationsViewModel) {
    val state by viewModel.detail.collectAsState()
    val retrying by viewModel.retrying.collectAsState()
    val job: NotificationJob = state ?: return
    val none = stringResource(Res.string.common_none)
    DialogFrame(
        stringResource(Res.string.nt_detail_title), viewModel::closeDetail, subtitle = job.participantName + "  ·  " + job.recipient,
        actions = {
            if (job.status.canRetry) ClubButton(stringResource(Res.string.nt_retry), { viewModel.retry(job) }, loading = retrying == job.id)
            ClubButton(stringResource(Res.string.common_close), viewModel::closeDetail, kind = ButtonKind.Secondary)
        },
    ) {
        StatusChip(stringResource(job.status.resource()), job.status.tone())
        if (job.isSimulated) Notice(stringResource(Res.string.nt_simulated_note), Tone.Info)
        LabeledValue(stringResource(Res.string.nt_kind), stringResource(if (job.kind == NotificationKind.TICKET_REPLACEMENT) Res.string.nt_kind_replacement else Res.string.nt_kind_confirmation) + "  ·  " + job.channel.label())
        LabeledValue(stringResource(Res.string.nt_col_event), job.eventTitle)
        LabeledValue(stringResource(Res.string.nt_provider), job.provider ?: none)
        LabeledValue(stringResource(Res.string.nt_provider_id), job.providerMessageId ?: none)
        LabeledValue(stringResource(Res.string.nt_attempts), job.attemptCount.toString())
        LabeledValue(stringResource(Res.string.nt_col_created), stamp(job.createdAt))
        LabeledValue(stringResource(Res.string.nt_sent_at), job.sentAt?.let(::stamp) ?: none)
        LabeledValue(stringResource(Res.string.nt_delivered_at), job.deliveredAt?.let(::stamp) ?: none)
        if (job.lastError != null) Notice(stringResource(Res.string.nt_last_error) + ": " + job.lastError, Tone.Danger)
    }
}
