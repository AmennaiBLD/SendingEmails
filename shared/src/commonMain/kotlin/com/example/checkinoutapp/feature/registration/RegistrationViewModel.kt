package com.example.checkinoutapp.feature.registration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.domain.model.DeliveryPreference
import com.example.checkinoutapp.domain.model.RegistrationField
import com.example.checkinoutapp.domain.model.RegistrationForm
import com.example.checkinoutapp.domain.model.Ticket
import com.example.checkinoutapp.domain.registration.cleanRegistrationForm
import com.example.checkinoutapp.domain.registration.validateRegistration
import com.example.checkinoutapp.domain.repository.RegistrationOutcome
import com.example.checkinoutapp.domain.repository.RegistrationRejection
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a submit did not produce a ticket. The screen turns each into a sentence. */
sealed interface RegistrationFailure {
    data object InvalidFields : RegistrationFailure
    data class Rejected(val reason: RegistrationRejection) : RegistrationFailure
    data object Network : RegistrationFailure
}

data class RegistrationFormState(
    val form: RegistrationForm = RegistrationForm(),
    val invalid: Set<RegistrationField> = emptySet(),
    val saving: Boolean = false,
    val failure: RegistrationFailure? = null,
)

class RegistrationViewModel(
    savedStateHandle: SavedStateHandle,
    private val registrationRepository: RegistrationRepository,
) : ViewModel() {

    private val eventId = savedStateHandle.get<String>("id") ?: ""

    private val _state = MutableStateFlow(RegistrationFormState())
    val state: StateFlow<RegistrationFormState> = _state.asStateFlow()

    /** Non-null once registered: the screen swaps the form for the ticket. */
    private val _ticket = MutableStateFlow<Ticket?>(null)
    val ticket: StateFlow<Ticket?> = _ticket.asStateFlow()

    /** Edits are ignored while saving, and clear the error of the field being fixed. */
    private fun edit(field: RegistrationField?, change: (RegistrationForm) -> RegistrationForm) {
        _state.update { state ->
            if (state.saving) state
            else state.copy(form = change(state.form), invalid = if (field == null) state.invalid else state.invalid - field, failure = null)
        }
    }

    fun setFullName(value: String) = edit(RegistrationField.FULL_NAME) { it.copy(fullName = value.take(120)) }
    fun setEmail(value: String) = edit(RegistrationField.EMAIL) { it.copy(email = value.take(254)) }
    fun setPhone(value: String) = edit(RegistrationField.PHONE) { it.copy(phone = value.take(24)) }
    fun setUniversity(value: String) = edit(RegistrationField.UNIVERSITY) { it.copy(university = value.take(120)) }
    fun setFaculty(value: String) = edit(RegistrationField.FACULTY) { it.copy(faculty = value.take(120)) }
    fun setDepartment(value: String) = edit(RegistrationField.DEPARTMENT) { it.copy(department = value.take(120)) }
    fun setStudentId(value: String) = edit(null) { it.copy(studentId = value.take(40)) }
    fun setPrivacy(accepted: Boolean) = edit(RegistrationField.PRIVACY) { it.copy(privacyAccepted = accepted) }
    fun setWhatsAppConsent(consent: Boolean) = edit(RegistrationField.WHATSAPP_CONSENT) { it.copy(whatsappConsent = consent) }
    fun setDelivery(preference: DeliveryPreference) = edit(RegistrationField.WHATSAPP_CONSENT) { it.copy(deliveryPreference = preference) }

    fun submit() {
        val current = _state.value
        if (current.saving) return
        val invalid = validateRegistration(current.form)
        if (invalid.isNotEmpty()) {
            _state.value = current.copy(invalid = invalid, failure = RegistrationFailure.InvalidFields)
            return
        }
        _state.value = current.copy(saving = true, failure = null)
        viewModelScope.launch {
            val result = registrationRepository.register(
                eventId = eventId,
                form = cleanRegistrationForm(current.form),
                asAdmin = false
            )
            // On any failure the form stays exactly as typed.
            if (result.isSuccess()) {
                when (val outcome = result.getSuccessData()) {
                    is RegistrationOutcome.Registered -> {
                        _ticket.value = outcome.ticket
                        _state.value = RegistrationFormState()
                    }
                    is RegistrationOutcome.Invalid -> {
                        _state.value = current.copy(saving = false, invalid = outcome.fields, failure = RegistrationFailure.InvalidFields)
                    }
                    is RegistrationOutcome.Rejected -> {
                        _state.value = current.copy(saving = false, failure = RegistrationFailure.Rejected(outcome.reason))
                    }
                }
            } else {
                _state.value = current.copy(saving = false, failure = RegistrationFailure.Network)
            }
        }
    }
}
