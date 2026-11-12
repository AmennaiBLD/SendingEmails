package com.example.checkinoutapp.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.checkinoutapp.feature.admin.EventPicker
import com.example.checkinoutapp.feature.admin.MutedText
import com.example.checkinoutapp.feature.admin.Panel
import com.example.checkinoutapp.feature.admin.WithSelectedEvent
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val EVENT_REPORTS: List<Triple<ReportKind, StringResource, StringResource>> = listOf(
    Triple(ReportKind.ATTENDANCE_CSV, Res.string.rp_attendance_csv, Res.string.rp_attendance_csv_sub),
    Triple(ReportKind.EVENT_PDF, Res.string.rp_event_pdf, Res.string.rp_event_pdf_sub),
    Triple(ReportKind.PARTICIPANTS_CSV, Res.string.rp_participants_csv, Res.string.rp_participants_csv_sub),
    Triple(ReportKind.SESSIONS_CSV, Res.string.rp_sessions_csv, Res.string.rp_sessions_csv_sub),
    Triple(ReportKind.ANALYTICS_CSV, Res.string.rp_analytics_csv, Res.string.rp_analytics_csv_sub),
)

@Composable
fun ReportsScreen() {
    val viewModel = koinViewModel<ReportsViewModel>()
    val busy by viewModel.busy.collectAsState()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is ReportEvent.Saved -> getString(Res.string.rp_saved, event.fileName)
                is ReportEvent.LoadFailed -> getString(Res.string.common_load_failed) + " " + event.message
                ReportEvent.SaveFailed -> getString(Res.string.common_action_failed)
            }
            snackbar.showSnackbar(message)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        EventPicker(viewModel.selection)
        WithSelectedEvent(viewModel.selection) { event ->
            Panel(stringResource(Res.string.rp_event_reports), subtitle = event.title) {
                EVENT_REPORTS.forEach { (kind, title, body) -> ReportRow(stringResource(title), stringResource(body), busy, kind, viewModel::export) }
            }
        }
        Panel(stringResource(Res.string.rp_club_reports)) {
            ReportRow(stringResource(Res.string.rp_comparison_csv), stringResource(Res.string.rp_comparison_csv_sub), busy, ReportKind.COMPARISON_CSV, viewModel::export)
        }
        MutedText(stringResource(Res.string.rp_csv_note))
    }
}

@Composable
private fun ReportRow(title: String, body: String, busy: ReportKind?, kind: ReportKind, onExport: (ReportKind) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ClubButton(stringResource(Res.string.rp_export), { onExport(kind) }, kind = ButtonKind.Secondary, icon = Icons.Outlined.FileDownload, enabled = busy == null, loading = busy == kind)
    }
}
