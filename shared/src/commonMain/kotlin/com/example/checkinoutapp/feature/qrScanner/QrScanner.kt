package com.example.checkinoutapp.feature.qrScanner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.domain.model.ScanMode
import com.example.checkinoutapp.domain.model.ScanOutcome
import com.example.checkinoutapp.domain.model.ScanResult
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.components.toneColors
import com.example.checkinoutapp.ui.format.formatDuration
import com.example.checkinoutapp.ui.format.formatTime
import com.example.checkinoutapp.ui.platform.CameraState
import com.example.checkinoutapp.ui.platform.QrCameraView
import com.example.checkinoutapp.ui.platform.cameraScanningSupported
import com.example.checkinoutapp.ui.platform.isMobilePlatform
import com.example.checkinoutapp.ui.theme.LocalStatusColors
import com.example.checkinoutapp.ui.theme.NumericStyle
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.isWide
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme
import com.example.checkinoutapp.ui.preview.previewCheckIn
import com.example.checkinoutapp.ui.preview.previewCheckOut
import com.example.checkinoutapp.ui.preview.previewRevoked

/** The scan tab: mode selector, camera, code entry, and the answer to the last scan. */
@Composable
fun QrScanner(viewModel: QrScannerViewModel, timezone: String, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
        Column(controlsModifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ModeSelector(state.mode, viewModel::setMode, enabled = !state.processing)
            CameraPanel(state, viewModel)
        }
    }
    val answer: @Composable (Modifier) -> Unit = { answerModifier ->
        Column(answerModifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ResultPanel(state, timezone, viewModel::dismissFeedback)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FormField(
                    state.manualCode, viewModel::setManualCode, stringResource(Res.string.scan_code_label),
                    placeholder = stringResource(Res.string.scan_code_placeholder), enabled = !state.processing, onDone = viewModel::submitManualCode,
                )
                ClubButton(
                    stringResource(Res.string.scan_code_submit), viewModel::submitManualCode, kind = ButtonKind.Secondary,
                    enabled = state.manualCode.isNotBlank(), loading = state.processing,
                )
            }
        }
    }
    if (isWide) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            controls(Modifier.weight(1f).widthIn(max = 520.dp))
            answer(Modifier.weight(1f))
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            controls(Modifier.fillMaxWidth())
            answer(Modifier.fillMaxWidth())
        }
    }
}

/** The two-mode selector. Large, always visible, and it only ever changes when tapped. */
@Composable
private fun ModeSelector(mode: ScanMode, onSelect: (ScanMode) -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ModeButton(Modifier.weight(1f), mode == ScanMode.CHECK_IN, Icons.AutoMirrored.Outlined.Login, stringResource(Res.string.scan_mode_check_in), stringResource(Res.string.scan_mode_check_in_sub), LocalStatusColors.current.success, LocalStatusColors.current.onSuccess, enabled) { onSelect(ScanMode.CHECK_IN) }
        ModeButton(Modifier.weight(1f), mode == ScanMode.CHECK_OUT, Icons.AutoMirrored.Outlined.Logout, stringResource(Res.string.scan_mode_check_out), stringResource(Res.string.scan_mode_check_out_sub), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, enabled) { onSelect(ScanMode.CHECK_OUT) }
    }
}

@Composable
private fun ModeButton(modifier: Modifier, active: Boolean, icon: ImageVector, title: String, subtitle: String, accent: Color, onAccent: Color, enabled: Boolean, onClick: () -> Unit) {
    val content = if (active) onAccent else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, role = Role.Tab, onClick = onClick).semantics { selected = active },
        shape = MaterialTheme.shapes.medium,
        color = if (active) accent else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (active) 0.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = Spacing.lg, horizontal = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(26.dp), tint = content)
            Spacer(Modifier.height(Spacing.xs))
            Text(title.uppercase(), style = MaterialTheme.typography.titleMedium, color = content)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun CameraPanel(state: ScannerUiState, viewModel: QrScannerViewModel) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (isWide) 1.25f else 1f).clip(MaterialTheme.shapes.large).background(Color(0xFF0B0F1E)),
        contentAlignment = Alignment.Center,
    ) {
        if (cameraScanningSupported) {
            QrCameraView(Modifier.fillMaxSize(), active = true, torchOn = state.torchOn, onScanned = viewModel::onScanned, onState = viewModel::onCameraState)
        }
        val message = when {
            !cameraScanningSupported || state.camera == CameraState.UNAVAILABLE -> Icons.Outlined.NoPhotography to stringResource(Res.string.scan_camera_unavailable)
            state.camera == CameraState.PERMISSION_DENIED -> Icons.Outlined.NoPhotography to stringResource(Res.string.scan_camera_denied)
            state.camera == CameraState.STARTING -> null to stringResource(Res.string.scan_camera_starting)
            else -> null
        }
        if (message != null) {
            Column(Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (message.first != null) Icon(message.first!!, null, Modifier.size(36.dp), tint = Color.White.copy(alpha = 0.7f))
                else CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
                Text(message.second, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            stringResource(if (state.mode == ScanMode.CHECK_IN) Res.string.scan_hint_in else Res.string.scan_hint_out),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The torch exists on phones; a laptop webcam has none.
        if (isMobilePlatform && state.camera == CameraState.READY) {
            ClubButton(
                stringResource(if (state.torchOn) Res.string.scan_torch_on else Res.string.scan_torch_off), viewModel::toggleTorch, kind = ButtonKind.Secondary,
                icon = if (state.torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
            )
        }
    }
}

@Composable
private fun ScanResult.message(): String = stringResource(
    when (this) {
        ScanResult.VALID_CHECK_IN -> Res.string.scan_ok_in
        ScanResult.VALID_CHECK_OUT -> Res.string.scan_ok_out
        ScanResult.INVALID_QR -> Res.string.scan_r_invalid
        ScanResult.REGISTRATION_NOT_FOUND -> Res.string.scan_r_not_found
        ScanResult.REGISTRATION_CANCELLED -> Res.string.scan_r_cancelled
        ScanResult.EVENT_NOT_OPEN -> Res.string.scan_r_event_not_open
        ScanResult.ALREADY_CHECKED_IN -> Res.string.scan_r_already_in
        ScanResult.NOT_CHECKED_IN -> Res.string.scan_r_not_in
        ScanResult.ALREADY_CHECKED_OUT -> Res.string.scan_r_already_out
        ScanResult.ORGANIZER_NOT_AUTHORIZED -> Res.string.scan_r_not_authorized
        ScanResult.EVENT_MISMATCH -> Res.string.scan_r_mismatch
        ScanResult.TICKET_REVOKED -> Res.string.scan_r_revoked
        ScanResult.UNKNOWN -> Res.string.scan_r_unknown
    },
)

/** The answer to the last scan. One glance: colour, a headline, the participant, the times. */
@Composable
private fun ResultPanel(state: ScannerUiState, timezone: String, onDismiss: () -> Unit) {
    val feedback = state.feedback
    if (state.processing) {
        ResultFrame(Tone.Neutral, null, stringResource(Res.string.scan_checking)) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp) }
        return
    }
    when (feedback) {
        null -> Unit
        is ScanFeedbackState.NoAnswer -> ResultFrame(Tone.Danger, Icons.Outlined.WifiOff, stringResource(Res.string.scan_r_network)) {
            ClubButton(stringResource(Res.string.scan_next), onDismiss, kind = ButtonKind.Secondary)
        }
        is ScanFeedbackState.Answered -> {
            val outcome = feedback.outcome
            val tone = when {
                outcome.result.isSuccess -> Tone.Success
                outcome.result.isRepeat -> Tone.Warning
                else -> Tone.Danger
            }
            val icon = when (tone) {
                Tone.Success -> Icons.Outlined.CheckCircle
                Tone.Warning -> Icons.Outlined.Info
                else -> Icons.Outlined.ErrorOutline
            }
            ResultFrame(tone, icon, outcome.result.message()) {
                OutcomeFacts(outcome, timezone)
                ClubButton(stringResource(Res.string.scan_next), onDismiss, kind = ButtonKind.Secondary)
            }
        }
    }
}

@Composable
private fun ResultFrame(tone: Tone, icon: ImageVector?, headline: String, content: @Composable () -> Unit) {
    val (container, onContainer) = toneColors(tone)
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(container).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (icon != null) Icon(icon, null, Modifier.size(32.dp), tint = onContainer)
            Text(headline, style = MaterialTheme.typography.headlineSmall, color = onContainer)
        }
        content()
    }
}

@Composable
private fun OutcomeFacts(outcome: ScanOutcome, timezone: String) {
    val color = MaterialTheme.colorScheme.onSurface
    @Composable fun fact(label: String, value: String) {
        Row {
            Text(label, Modifier.weight(0.42f), style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f))
            Text(value, Modifier.weight(0.58f), style = MaterialTheme.typography.titleMedium.merge(NumericStyle), color = color)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        outcome.participantName?.let { fact(stringResource(Res.string.scan_participant), it) }
        outcome.eventTitle?.let { fact(stringResource(Res.string.scan_event), it) }
        outcome.checkInAt?.let { fact(stringResource(Res.string.scan_arrival), formatTime(it, timezone)) }
        outcome.checkOutAt?.let { fact(stringResource(Res.string.scan_departure), formatTime(it, timezone)) }
        outcome.durationSeconds?.let { fact(stringResource(Res.string.scan_time_spent), formatDuration(it)) }
        val total = outcome.totalDurationSeconds
        if (total != null && total != outcome.durationSeconds) fact(stringResource(Res.string.scan_total_time), formatDuration(total))
        if (outcome.result == ScanResult.VALID_CHECK_IN) {
            Text(stringResource(Res.string.scan_registration_confirmed), style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f))
            Text(stringResource(Res.string.scan_attendance_active), style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f))
        }
    }
}

@Preview
@Composable
private fun ModeSelectorPreview() {
    ClubTheme {
        Column(Modifier.padding(Spacing.lg)) {
            ModeSelector(mode = ScanMode.CHECK_IN, onSelect = {}, enabled = true)
        }
    }
}

@Preview
@Composable
private fun CheckInResultPreview() {
    ClubTheme {
        ResultPanel(ScannerUiState(feedback = ScanFeedbackState.Answered(previewCheckIn)), timezone = "UTC", onDismiss = {})
    }
}

@Preview
@Composable
private fun CheckOutResultPreview() {
    ClubTheme {
        ResultPanel(ScannerUiState(mode = ScanMode.CHECK_OUT, feedback = ScanFeedbackState.Answered(previewCheckOut)), timezone = "UTC", onDismiss = {})
    }
}

@Preview
@Composable
private fun RefusedResultPreview() {
    ClubTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ResultPanel(ScannerUiState(feedback = ScanFeedbackState.Answered(previewRevoked)), timezone = "UTC", onDismiss = {})
            ResultPanel(ScannerUiState(feedback = ScanFeedbackState.NoAnswer(ScanMode.CHECK_IN)), timezone = "UTC", onDismiss = {})
        }
    }
}
