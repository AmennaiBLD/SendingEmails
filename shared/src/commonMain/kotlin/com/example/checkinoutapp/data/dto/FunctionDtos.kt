package com.example.checkinoutapp.data.dto

import com.example.checkinoutapp.domain.model.Ticket
import kotlinx.serialization.Serializable

// Requests and responses of the few callable Cloud Functions that remain: the ones that send an
// email (ticket retrieval by one-time code, notification retry). Everything else writes to
// Firestore directly. Instants travel as Double epoch-milliseconds: a JSON number on every
// platform, including JS.

@Serializable
internal data class TicketDto(
    val registrationId: String = "", val eventId: String = "", val eventTitle: String = "", val participantName: String = "",
    val location: String = "", val timezone: String = "UTC", val startAt: Double = 0.0, val endAt: Double = 0.0, val ticketVersion: Int = 1,
    val ticketCode: String = "", val emailQueued: Boolean = false, val whatsappQueued: Boolean = false,
) {
    fun toDomain() = Ticket(
        registrationId, eventId, eventTitle, participantName, location, timezone, startAt.toLong(), endAt.toLong(), ticketVersion, ticketCode,
        emailQueued = emailQueued, whatsappQueued = whatsappQueued,
    )
}

@Serializable
internal data class TicketAccessRequest(val eventId: String, val email: String)

@Serializable
internal data class TicketAccessStarted(val requestId: String = "")

@Serializable
internal data class TicketAccessConfirm(val requestId: String, val code: String, val replace: Boolean)

@Serializable
internal data class TicketAccessResponse(val result: String = "", val ticket: TicketDto? = null)

@Serializable
internal data class RetryRequest(val jobId: String)

/** `{ result: "…" }` — the shape of every simple command's answer. */
@Serializable
internal data class ResultResponse(val result: String = "", val status: String? = null)
