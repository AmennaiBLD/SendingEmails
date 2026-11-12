package com.example.checkinoutapp.feature.ticket

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.domain.model.Ticket
import com.example.checkinoutapp.domain.registration.isValidEmail
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.domain.repository.TicketAccessOutcome
import com.example.checkinoutapp.ui.platform.FileSaver
import io.github.alexzhirkevich.qrose.ImageFormat
import io.github.alexzhirkevich.qrose.toByteArray
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TicketEvent { DOWNLOADED, DOWNLOAD_FAILED }

/** Saves the ticket's QR as a PNG for its owner. Used wherever a ticket is shown. */
class TicketActionsViewModel(private val fileSaver: FileSaver) : ViewModel() {
    private val _events = Channel<TicketEvent>(Channel.BUFFERED)
    val events: Flow<TicketEvent> = _events.receiveAsFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun download(ticket: Ticket) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val saved = runCatching {
                val png = ticketQrPainter(ticket.ticketCode).toByteArray(QR_EXPORT_SIZE, QR_EXPORT_SIZE, ImageFormat.PNG)
                fileSaver.save("ticket-${ticket.registrationId.take(8)}.png", "image/png", png)
            }
            _saving.value = false
            when {
                saved.isFailure -> _events.send(TicketEvent.DOWNLOAD_FAILED)
                saved.getOrDefault(false) -> _events.send(TicketEvent.DOWNLOADED)
            }
        }
    }
}

private const val QR_EXPORT_SIZE = 1024

enum class FindTicketStep { EMAIL, CODE }

enum class FindTicketError { INVALID_EMAIL, INVALID_CODE, EXPIRED, TOO_MANY_ATTEMPTS, NETWORK }

data class FindTicketState(
    val step: FindTicketStep = FindTicketStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val replace: Boolean = false,
    val requestId: String = "",
    val busy: Boolean = false,
    val error: FindTicketError? = null,
)

/** Ticket retrieval for a participant without an account: email, one-time code, ticket. */
class FindTicketViewModel(
    savedStateHandle: SavedStateHandle,
    private val registrationRepository: RegistrationRepository,
) : ViewModel() {

    private val eventId = savedStateHandle.get<String>("id") ?: ""

    private val _state = MutableStateFlow(FindTicketState())
    val state: StateFlow<FindTicketState> = _state.asStateFlow()

    private val _ticket = MutableStateFlow<Ticket?>(null)
    val ticket: StateFlow<Ticket?> = _ticket.asStateFlow()

    private fun edit(change: (FindTicketState) -> FindTicketState) = _state.update { if (it.busy) it else change(it).copy(error = null) }

    fun setEmail(value: String) = edit { it.copy(email = value.take(254)) }
    fun setCode(value: String) = edit { it.copy(code = value.filter(Char::isDigit).take(CODE_LENGTH)) }
    fun setReplace(value: Boolean) = edit { it.copy(replace = value) }
    fun changeEmail() = edit { it.copy(step = FindTicketStep.EMAIL, code = "", requestId = "") }

    fun sendCode() {
        val current = _state.value
        if (current.busy) return
        if (!isValidEmail(current.email)) {
            _state.value = current.copy(error = FindTicketError.INVALID_EMAIL)
            return
        }
        _state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = registrationRepository.requestTicketAccess(eventId = eventId, email = current.email.trim())
            if (result.isSuccess()) {
                _state.value = current.copy(busy = false, step = FindTicketStep.CODE, requestId = result.getSuccessData(), code = "")
            } else {
                _state.value = current.copy(busy = false, error = FindTicketError.NETWORK)
            }
        }
    }

    fun confirm() {
        val current = _state.value
        if (current.busy) return
        if (current.code.length != CODE_LENGTH) {
            _state.value = current.copy(error = FindTicketError.INVALID_CODE)
            return
        }
        _state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = registrationRepository.confirmTicketAccess(
                requestId = current.requestId,
                code = current.code,
                replace = current.replace
            )
            if (result.isSuccess()) {
                when (val outcome = result.getSuccessData()) {
                    is TicketAccessOutcome.Granted -> {
                        _ticket.value = outcome.ticket
                        _state.value = FindTicketState()
                    }
                    TicketAccessOutcome.InvalidCode -> _state.value = current.copy(busy = false, error = FindTicketError.INVALID_CODE)
                    TicketAccessOutcome.Expired -> _state.value = current.copy(busy = false, error = FindTicketError.EXPIRED)
                    TicketAccessOutcome.TooManyAttempts -> _state.value = current.copy(busy = false, error = FindTicketError.TOO_MANY_ATTEMPTS)
                }
            } else {
                _state.value = current.copy(busy = false, error = FindTicketError.NETWORK)
            }
        }
    }

    private companion object {
        const val CODE_LENGTH = 6
    }
}
