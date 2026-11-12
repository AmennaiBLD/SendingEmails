package com.example.checkinoutapp.feature.qrScanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.domain.model.ScanMode
import com.example.checkinoutapp.domain.model.ScanOutcome
import com.example.checkinoutapp.domain.model.ScanResult
import com.example.checkinoutapp.domain.repository.AttendanceRepository
import com.example.checkinoutapp.ui.platform.CameraState
import com.example.checkinoutapp.ui.platform.ScanFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ScanFeedbackState {
    data class Answered(val outcome: ScanOutcome) : ScanFeedbackState
    data class NoAnswer(val mode: ScanMode) : ScanFeedbackState
}

data class ScannerUiState(
    val mode: ScanMode = ScanMode.CHECK_IN,
    val processing: Boolean = false,
    val torchOn: Boolean = false,
    val camera: CameraState = CameraState.STARTING,
    val manualCode: String = "",
    val feedback: ScanFeedbackState? = null,
)


class QrScannerViewModel(
    savedStateHandle: SavedStateHandle,
    private val attendanceRepository: AttendanceRepository,
    private val feedback: ScanFeedback,
    private val clock: AppClock,
) : ViewModel() {

    private val eventId = savedStateHandle.get<String>("id") ?: ""

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private var lastPayload: String? = null
    private var lastPayloadAt = 0L

    fun setMode(mode: ScanMode) {
        // A new mode makes the same ticket scannable again at once (check in, then check out).
        lastPayload = null
        _state.update { if (it.processing) it else it.copy(mode = mode, feedback = null) }
    }

    fun toggleTorch() = _state.update { it.copy(torchOn = !it.torchOn) }
    fun onCameraState(camera: CameraState) = _state.update { it.copy(camera = camera) }
    fun setManualCode(value: String) = _state.update { it.copy(manualCode = value.take(MANUAL_CODE_LIMIT)) }
    fun dismissFeedback() = _state.update { it.copy(feedback = null) }

    fun onScanned(payload: String) {
        val now = clock.nowMillis()
        if (_state.value.processing) return
        if (payload == lastPayload && now - lastPayloadAt < SAME_CODE_COOLDOWN_MILLIS) return
        lastPayload = payload
        lastPayloadAt = now
        submit(payload)
    }

    fun submitManualCode() {
        val code = _state.value.manualCode.trim()
        if (code.isEmpty() || _state.value.processing) return
        submit(code)
    }

    private fun submit(payload: String) {
        val mode = _state.value.mode
        // Anything this long is not one of our tickets; do not spend a network call on it.
        if (payload.length > MAX_PAYLOAD_LENGTH) {
            feedback.failure()
            _state.update { it.copy(feedback = ScanFeedbackState.Answered(ScanOutcome(ScanResult.INVALID_QR, mode))) }
            return
        }
        _state.update { it.copy(processing = true, feedback = null) }
        viewModelScope.launch {
            // The backend decides. A payload that merely looks right proves nothing.
            val result = attendanceRepository.scan(
                mode = mode,
                eventId = eventId,
                qr = payload.trim(),
                registrationId = null
            )
            val shown = if (result.isSuccess()) {
                ScanFeedbackState.Answered(result.getSuccessData())
            } else {
                ScanFeedbackState.NoAnswer(mode)
            }
            val success = shown is ScanFeedbackState.Answered && shown.outcome.result.isSuccess
            if (success) feedback.success() else feedback.failure()
            // A failed call recorded nothing: let the same ticket be scanned again immediately.
            if (shown is ScanFeedbackState.NoAnswer) lastPayload = null
            _state.update { it.copy(processing = false, feedback = shown, manualCode = if (success) "" else it.manualCode) }
        }
    }

    private companion object {
        const val SAME_CODE_COOLDOWN_MILLIS = 4_000L
        const val MANUAL_CODE_LIMIT = 200
        const val MAX_PAYLOAD_LENGTH = 200
    }
}
