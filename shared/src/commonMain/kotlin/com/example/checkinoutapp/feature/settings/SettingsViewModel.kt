package com.example.checkinoutapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.ClubSettings
import com.example.checkinoutapp.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsEvent { SAVED, NAME_REQUIRED, FAILED }

data class SettingsForm(val settings: ClubSettings, val graceText: String, val saving: Boolean = false, val dirty: Boolean = false)

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** Loaded once into an editable form; null until the stored settings arrive. */
    private val _form = MutableStateFlow<RequestState<SettingsForm>>(RequestState.Loading)
    val form: StateFlow<RequestState<SettingsForm>> = _form.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val loaded = settingsRepository.readClubSettingsFlow().first { it is RequestState.Success || it is RequestState.Error }
            _form.value = loaded.map { SettingsForm(it, it.attendanceGraceMinutes.toString()) }
        }
    }

    fun edit(change: (ClubSettings) -> ClubSettings) = _form.update { state ->
        state.map { form -> if (form.saving) form else form.copy(settings = change(form.settings), dirty = true) }
    }

    fun setGrace(text: String) = _form.update { state ->
        state.map { form -> if (form.saving) form else form.copy(graceText = text.filter(Char::isDigit).take(3), dirty = true) }
    }

    fun save() {
        val form = _form.value.getSuccessDataOrNull() ?: return
        if (form.saving) return
        val grace = (form.graceText.toIntOrNull() ?: ClubSettings.DEFAULT_GRACE_MINUTES).coerceIn(0, MAX_GRACE_MINUTES)
        val settings = form.settings.copy(attendanceGraceMinutes = grace)
        if (settings.clubName.isBlank()) {
            viewModelScope.launch { _events.send(SettingsEvent.NAME_REQUIRED) }
            return
        }
        _form.value = RequestState.Success(data = form.copy(saving = true))
        viewModelScope.launch {
            val result = settingsRepository.updateClubSettings(settings = settings)
            if (result.isSuccess()) {
                _form.value = RequestState.Success(data = form.copy(settings = settings, graceText = grace.toString(), saving = false, dirty = false))
                _events.send(SettingsEvent.SAVED)
            } else {
                _form.value = RequestState.Success(data = form.copy(saving = false))
                _events.send(SettingsEvent.FAILED)
            }
        }
    }

    private companion object {
        const val MAX_GRACE_MINUTES = 240
    }
}
