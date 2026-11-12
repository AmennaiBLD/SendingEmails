package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.model.RegistrationField
import com.example.checkinoutapp.domain.model.RegistrationForm
import com.example.checkinoutapp.domain.model.Ticket
import kotlinx.coroutines.flow.Flow

interface RegistrationRepository {
    suspend fun register(
        eventId: String,
        form: RegistrationForm,
        asAdmin: Boolean
    ): RequestState<RegistrationOutcome>
    fun readRegistrationsByEventFlow(
        eventId: String,
        limit: Int
    ): Flow<RequestState<List<Registration>>>
    fun readRecentRegistrationsFlow(limit: Int): Flow<RequestState<List<Registration>>>
    fun readRegistrationsByEmailFlow(
        normalizedEmail: String,
        limit: Int
    ): Flow<RequestState<List<Registration>>>
    suspend fun cancelRegistration(
        registrationId: String,
        reason: String
    ): RequestState<CancelOutcome>
    suspend fun resendTicket(
        registrationId: String,
        replace: Boolean
    ): RequestState<ResendOutcome>
    suspend fun requestTicketAccess(
        eventId: String,
        email: String
    ): RequestState<String>
    suspend fun confirmTicketAccess(
        requestId: String,
        code: String,
        replace: Boolean
    ): RequestState<TicketAccessOutcome>
}

sealed interface RegistrationOutcome {
    class Registered(val ticket: Ticket) : RegistrationOutcome
    data class Invalid(val fields: Set<RegistrationField>) : RegistrationOutcome
    data class Rejected(val reason: RegistrationRejection) : RegistrationOutcome
}

enum class RegistrationRejection { EVENT_NOT_FOUND, EVENT_NOT_OPEN, REGISTRATION_NOT_OPEN, REGISTRATION_CLOSED, EVENT_FULL, ALREADY_REGISTERED, UNKNOWN }

enum class CancelOutcome { CANCELLED, ALREADY_CANCELLED, REGISTRATION_NOT_FOUND, UNKNOWN }

enum class ResendOutcome { QUEUED, REGISTRATION_NOT_FOUND, REGISTRATION_CANCELLED, NO_CHANNEL, UNKNOWN }

sealed interface TicketAccessOutcome {
    class Granted(val ticket: Ticket) : TicketAccessOutcome
    data object InvalidCode : TicketAccessOutcome
    data object Expired : TicketAccessOutcome
    data object TooManyAttempts : TicketAccessOutcome
}
