package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.data.dto.TicketAccessConfirm
import com.example.checkinoutapp.data.dto.TicketAccessRequest
import com.example.checkinoutapp.data.dto.TicketAccessResponse
import com.example.checkinoutapp.data.dto.TicketAccessStarted
import com.example.checkinoutapp.data.firebase.auditLog
import com.example.checkinoutapp.data.firebase.currentActor
import com.example.checkinoutapp.data.firebase.millisTimestamp
import com.example.checkinoutapp.data.firebase.nowMillis
import com.example.checkinoutapp.data.firebase.toMillis
import com.example.checkinoutapp.domain.model.AttendanceStatus
import com.example.checkinoutapp.domain.model.DeliveryPreference
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.model.RegistrationForm
import com.example.checkinoutapp.domain.model.RegistrationStatus
import com.example.checkinoutapp.domain.model.Ticket
import com.example.checkinoutapp.domain.registration.cleanRegistrationForm
import com.example.checkinoutapp.domain.registration.normalizeEmail
import com.example.checkinoutapp.domain.registration.registrationKeyId
import com.example.checkinoutapp.domain.registration.validateRegistration
import com.example.checkinoutapp.domain.repository.CancelOutcome
import com.example.checkinoutapp.domain.repository.RegistrationOutcome
import com.example.checkinoutapp.domain.repository.RegistrationRejection
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.domain.repository.ResendOutcome
import com.example.checkinoutapp.domain.repository.ReviewOutcome
import com.example.checkinoutapp.domain.repository.TicketAccessOutcome
import com.example.checkinoutapp.domain.ticket.newTicketSecret
import com.example.checkinoutapp.domain.ticket.ticketCode
import com.example.checkinoutapp.firebase.FirebaseAppConfig
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.Transaction
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class RegistrationRepositoryImpl : RegistrationRepository {

    /**
     * A visitor applies: PENDING, no QR and no seat yet, and a "we received it, please wait for the
     * approval" email is queued. The QR is issued and emailed when an administrator approves
     * ([reviewRegistration]). An administrator (`asAdmin`, may also register outside the window)
     * registers someone approved at once: QR, seat and ticket email.
     * One transaction re-reads the event (status, window, capacity) and claims the (event, email) key;
     * firestore.rules re-checks all of it.
     */
    override suspend fun register(eventId: String, form: RegistrationForm, asAdmin: Boolean): RequestState<RegistrationOutcome> {
        return try {
            val invalid = validateRegistration(form)
            if (invalid.isNotEmpty()) return RequestState.Success(data = RegistrationOutcome.Invalid(invalid))
            val clean = cleanRegistrationForm(form)
            val normalizedEmail = normalizeEmail(clean.email)
            val actor = if (asAdmin) currentActor() else null
            val secret = newTicketSecret()
            val database = Firebase.firestore
            val eventRef = database.collection(collectionPath = "events").document(eventId)
            val keyRef = database.collection(collectionPath = "registrationKeys").document(registrationKeyId(eventId, normalizedEmail))
            val registrationRef = database.collection(collectionPath = "registrations").document
            val registrationId = registrationRef.id

            val outcome = database.runTransaction {
                val event = get(eventRef)
                if (!event.exists) return@runTransaction RegistrationOutcome.Rejected(RegistrationRejection.EVENT_NOT_FOUND)
                val key = get(keyRef)
                val now = nowMillis()
                val startAt = event.get<Timestamp>(field = "startAt").toMillis()
                val endAt = event.get<Timestamp>(field = "endAt").toMillis()
                val capacity = event.get<Int?>(field = "capacity") ?: 0
                val count = event.get<Int?>(field = "registrationCount") ?: 0
                val rejection = when {
                    event.get<String?>(field = "status") != EventStatus.PUBLISHED.name -> RegistrationRejection.EVENT_NOT_OPEN
                    now >= endAt -> RegistrationRejection.REGISTRATION_CLOSED
                    actor == null && now < event.get<Timestamp>(field = "registrationOpenAt").toMillis() -> RegistrationRejection.REGISTRATION_NOT_OPEN
                    actor == null && now > event.get<Timestamp>(field = "registrationCloseAt").toMillis() -> RegistrationRejection.REGISTRATION_CLOSED
                    key.exists -> RegistrationRejection.ALREADY_REGISTERED
                    capacity > 0 && count >= capacity -> RegistrationRejection.EVENT_FULL
                    else -> null
                }
                if (rejection != null) return@runTransaction RegistrationOutcome.Rejected(rejection)

                val at = millisTimestamp(now)
                val title = event.get<String>(field = "title")
                val approved = actor != null
                // An application has no ticket yet (version 0, no secret): approval issues version 1.
                val ticketVersion = if (approved) 1 else 0
                set(
                    registrationRef,
                    mapOf(
                        "id" to registrationId,
                        "eventId" to eventId,
                        "participantId" to null,
                        "participantName" to clean.fullName,
                        "nameLower" to clean.fullName.lowercase(),
                        "email" to clean.email,
                        "normalizedEmail" to normalizedEmail,
                        "phone" to clean.phone,
                        "university" to clean.university,
                        "faculty" to clean.faculty,
                        "department" to clean.department,
                        "studentId" to clean.studentId.ifBlank { null },
                        "status" to if (approved) RegistrationStatus.REGISTERED.name else RegistrationStatus.PENDING.name,
                        "ticketVersion" to ticketVersion,
                        "ticketSecret" to if (approved) secret else null,
                        "registeredAt" to at,
                        "updatedAt" to at,
                        "deliveryPreference" to clean.deliveryPreference.name,
                        "whatsappConsent" to clean.whatsappConsent,
                        "source" to if (actor != null) "ADMIN" else "PUBLIC",
                        "registeredBy" to actor?.uid,
                        "attendanceStatus" to AttendanceStatus.NONE.name,
                        "activeSessionId" to null,
                        "sessionCount" to 0,
                        "totalDurationSeconds" to 0,
                        "firstCheckInAt" to null,
                        "lastCheckOutAt" to null,
                    ),
                )
                set(keyRef, mapOf("registrationId" to registrationId, "eventId" to eventId, "createdAt" to at))
                // Approved: the QR ticket email. Applied: only the "please wait for the approval" email,
                // always by email (it carries no QR). The sender runs every few minutes.
                val queuedNow = if (approved) {
                    queueTicketJobs(
                        registrationId = registrationId, eventId = eventId, eventTitle = title, participantName = clean.fullName,
                        email = clean.email, phone = clean.phone, preference = clean.deliveryPreference, whatsappConsent = clean.whatsappConsent,
                        kind = "REGISTRATION_CONFIRMATION", ticketVersion = ticketVersion, suffix = "", at = at,
                    )
                } else {
                    queueTicketJobs(
                        registrationId = registrationId, eventId = eventId, eventTitle = title, participantName = clean.fullName,
                        email = clean.email, phone = clean.phone, preference = DeliveryPreference.EMAIL_ONLY, whatsappConsent = false,
                        kind = "REGISTRATION_RECEIVED", ticketVersion = ticketVersion, suffix = "", at = at,
                    )
                }
                val ticket = Ticket(
                    registrationId = registrationId,
                    eventId = eventId,
                    eventTitle = title,
                    participantName = clean.fullName,
                    location = event.get<String?>(field = "location") ?: "",
                    timezone = event.get<String?>(field = "timezone") ?: "UTC",
                    startAt = startAt,
                    endAt = endAt,
                    ticketVersion = ticketVersion,
                    // No QR before approval: an application's ticket has no code to show.
                    ticketCode = if (approved) ticketCode(registrationId, 1, secret) else "",
                    // A message is only "on its way" when the sender is deployed for this project
                    // (emailDelivery / whatsappDelivery in firebase/app-config.json).
                    emailQueued = "EMAIL" in queuedNow && FirebaseAppConfig.EMAIL_DELIVERY == "true",
                    whatsappQueued = "WHATSAPP" in queuedNow && FirebaseAppConfig.WHATSAPP_DELIVERY == "true",
                    approved = approved,
                )
                if (actor != null) {
                    // Only an approved registration takes a seat.
                    update(eventRef, "registrationCount" to count + 1, "lastRegistrationId" to registrationId, "updatedAt" to at)
                    set(
                        database.collection(collectionPath = "auditLogs").document,
                        auditLog(actor, "REGISTRATION_CREATED_BY_ADMIN", "registration", registrationId, eventId, at),
                    )
                }
                RegistrationOutcome.Registered(ticket)
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while registering: ${e.toUserMessage()}")
        }
    }

    /**
     * One outbox job per channel the participant chose, with a deterministic id, for the email /
     * WhatsApp sender (Cloud Functions, when enabled). Written in the caller's transaction.
     * Returns the channels queued ("EMAIL", "WHATSAPP").
     */
    private fun Transaction.queueTicketJobs(
        registrationId: String,
        eventId: String,
        eventTitle: String,
        participantName: String,
        email: String,
        phone: String,
        preference: DeliveryPreference,
        whatsappConsent: Boolean,
        kind: String,
        ticketVersion: Int,
        suffix: String,
        at: Timestamp,
    ): Set<String> {
        val channels = buildList {
            if (preference == DeliveryPreference.EMAIL_ONLY || preference == DeliveryPreference.BOTH) add("EMAIL" to email)
            if (whatsappConsent && (preference == DeliveryPreference.WHATSAPP_ONLY || preference == DeliveryPreference.BOTH)) add("WHATSAPP" to phone)
        }
        channels.forEach { (channel, recipient) ->
            val id = "${registrationId}_v${ticketVersion}_${channel}_$kind$suffix"
            set(
                Firebase.firestore.collection(collectionPath = "notificationJobs").document(id),
                mapOf(
                    "id" to id,
                    "registrationId" to registrationId,
                    "eventId" to eventId,
                    "eventTitle" to eventTitle,
                    "participantName" to participantName,
                    "channel" to channel,
                    "kind" to kind,
                    "recipient" to recipient,
                    "ticketVersion" to ticketVersion,
                    "status" to "PENDING",
                    "provider" to null,
                    "providerMessageId" to null,
                    "attemptCount" to 0,
                    "lastError" to null,
                    "nextAttemptAt" to at,
                    "leaseUntil" to null,
                    "createdAt" to at,
                    "updatedAt" to at,
                    "sentAt" to null,
                    "deliveredAt" to null,
                ),
            )
        }
        return channels.map { it.first }.toSet()
    }

    override fun readRegistrationsByEventFlow(eventId: String, limit: Int): Flow<RequestState<List<Registration>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "registrations")
                .where { "eventId" equalTo eventId }
                .orderBy("registeredAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val registrations = query.documents.mapNotNull { document ->
                        try {
                            registrationFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable registration '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = registrations))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the registrations: ${e.toUserMessage()}"))
        }
    }

    override fun readRecentRegistrationsFlow(limit: Int): Flow<RequestState<List<Registration>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "registrations")
                .orderBy("registeredAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val registrations = query.documents.mapNotNull { document ->
                        try {
                            registrationFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable registration '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = registrations))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the recent registrations: ${e.toUserMessage()}"))
        }
    }

    override fun readRegistrationsByEmailFlow(normalizedEmail: String, limit: Int): Flow<RequestState<List<Registration>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "registrations")
                .where { "normalizedEmail" equalTo normalizedEmail }
                .orderBy("registeredAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val registrations = query.documents.mapNotNull { document ->
                        try {
                            registrationFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable registration '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = registrations))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the participant history: ${e.toUserMessage()}"))
        }
    }

    /**
     * Approve: the status becomes REGISTERED and the QR is issued now (version 1, new secret); a seat
     * is taken (capacity enforced) and the "you're approved" email with the QR ticket is queued.
     * Reject: REJECTED, no message (the specification emails approved people only). The
     * (event, email) key stays, so the same email cannot apply again.
     */
    override suspend fun reviewRegistration(registrationId: String, approve: Boolean): RequestState<ReviewOutcome> {
        return try {
            val actor = currentActor()
            val database = Firebase.firestore
            val registrationRef = database.collection(collectionPath = "registrations").document(registrationId)
            val outcome = database.runTransaction {
                val registration = get(registrationRef)
                if (!registration.exists) return@runTransaction ReviewOutcome.REGISTRATION_NOT_FOUND
                if (registration.get<String?>(field = "status") != RegistrationStatus.PENDING.name) return@runTransaction ReviewOutcome.NOT_PENDING
                val eventId = registration.get<String>(field = "eventId")
                val eventRef = database.collection(collectionPath = "events").document(eventId)
                val event = get(eventRef)
                val at = millisTimestamp(nowMillis())
                val name = registration.get<String>(field = "participantName")
                if (!approve) {
                    update(registrationRef, "status" to RegistrationStatus.REJECTED.name, "reviewedAt" to at, "reviewedBy" to actor.uid, "updatedAt" to at)
                    set(
                        database.collection(collectionPath = "auditLogs").document,
                        auditLog(actor, "REGISTRATION_REJECTED", "registration", registrationId, eventId, at, newValue = mapOf("participantName" to name)),
                    )
                    return@runTransaction ReviewOutcome.REJECTED
                }
                if (!event.exists) return@runTransaction ReviewOutcome.REGISTRATION_NOT_FOUND
                val capacity = event.get<Int?>(field = "capacity") ?: 0
                val count = event.get<Int?>(field = "registrationCount") ?: 0
                if (capacity > 0 && count >= capacity) return@runTransaction ReviewOutcome.EVENT_FULL
                // The QR is issued on approval (an older application that already has one keeps it).
                val existingSecret = registration.get<String?>(field = "ticketSecret")
                val ticketVersion = maxOf(1, registration.get<Int?>(field = "ticketVersion") ?: 1)
                update(
                    registrationRef,
                    "status" to RegistrationStatus.REGISTERED.name,
                    "ticketVersion" to ticketVersion,
                    "ticketSecret" to (existingSecret ?: newTicketSecret()),
                    "reviewedAt" to at,
                    "reviewedBy" to actor.uid,
                    "updatedAt" to at,
                )
                update(eventRef, "registrationCount" to count + 1, "updatedAt" to at)
                queueTicketJobs(
                    registrationId = registrationId, eventId = eventId, eventTitle = event.get(field = "title"), participantName = name,
                    email = registration.get(field = "email"), phone = registration.get(field = "phone"),
                    preference = DeliveryPreference.fromRaw(registration.get<String?>(field = "deliveryPreference")),
                    whatsappConsent = registration.get<Boolean?>(field = "whatsappConsent") ?: false,
                    kind = "REGISTRATION_APPROVED", ticketVersion = ticketVersion, suffix = "", at = at,
                )
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(actor, "REGISTRATION_APPROVED", "registration", registrationId, eventId, at, newValue = mapOf("participantName" to name)),
                )
                ReviewOutcome.APPROVED
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while reviewing the registration: ${e.toUserMessage()}")
        }
    }

    /** Frees the seat and the (event, email) key, so the person can register again later. */
    override suspend fun cancelRegistration(registrationId: String, reason: String): RequestState<CancelOutcome> {
        return try {
            val actor = currentActor()
            val database = Firebase.firestore
            val registrationRef = database.collection(collectionPath = "registrations").document(registrationId)
            val outcome = database.runTransaction {
                val registration = get(registrationRef)
                if (!registration.exists) return@runTransaction CancelOutcome.REGISTRATION_NOT_FOUND
                val status = registration.get<String?>(field = "status")
                if (status == RegistrationStatus.CANCELLED.name) return@runTransaction CancelOutcome.ALREADY_CANCELLED
                val eventId = registration.get<String>(field = "eventId")
                val eventRef = database.collection(collectionPath = "events").document(eventId)
                val event = get(eventRef)
                val at = millisTimestamp(nowMillis())
                update(registrationRef, "status" to RegistrationStatus.CANCELLED.name, "cancelledAt" to at, "updatedAt" to at)
                delete(database.collection(collectionPath = "registrationKeys").document(registrationKeyId(eventId, registration.get(field = "normalizedEmail"))))
                // Only an approved registration holds a seat.
                if (event.exists && status == RegistrationStatus.REGISTERED.name) {
                    update(eventRef, "registrationCount" to maxOf(0, (event.get<Int?>(field = "registrationCount") ?: 0) - 1), "updatedAt" to at)
                }
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(
                        actor, "REGISTRATION_CANCELLED", "registration", registrationId, eventId, at, reason = reason,
                        previousValue = mapOf("status" to status), newValue = mapOf("status" to RegistrationStatus.CANCELLED.name),
                    ),
                )
                CancelOutcome.CANCELLED
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while cancelling the registration: ${e.toUserMessage()}")
        }
    }

    /** Queues the ticket again; `replace` writes a new secret and version, so the old QR stops working. */
    override suspend fun resendTicket(registrationId: String, replace: Boolean): RequestState<ResendOutcome> {
        return try {
            val actor = currentActor()
            val database = Firebase.firestore
            val registrationRef = database.collection(collectionPath = "registrations").document(registrationId)
            val outcome = database.runTransaction {
                val registration = get(registrationRef)
                if (!registration.exists) return@runTransaction ResendOutcome.REGISTRATION_NOT_FOUND
                if (registration.get<String?>(field = "status") == RegistrationStatus.CANCELLED.name) return@runTransaction ResendOutcome.REGISTRATION_CANCELLED
                val eventId = registration.get<String>(field = "eventId")
                val event = get(database.collection(collectionPath = "events").document(eventId))
                if (!event.exists) return@runTransaction ResendOutcome.REGISTRATION_NOT_FOUND
                val now = nowMillis()
                val at = millisTimestamp(now)
                val current = registration.get<Int?>(field = "ticketVersion") ?: 1
                val ticketVersion = if (replace) current + 1 else current
                val queued = queueTicketJobs(
                    registrationId = registrationId, eventId = eventId, eventTitle = event.get(field = "title"),
                    participantName = registration.get(field = "participantName"), email = registration.get(field = "email"),
                    phone = registration.get(field = "phone"),
                    preference = DeliveryPreference.fromRaw(registration.get<String?>(field = "deliveryPreference")),
                    whatsappConsent = registration.get<Boolean?>(field = "whatsappConsent") ?: false,
                    kind = if (replace) "TICKET_REPLACEMENT" else "REGISTRATION_CONFIRMATION", ticketVersion = ticketVersion,
                    suffix = "_r$now", at = at,
                )
                if (queued.isEmpty()) return@runTransaction ResendOutcome.NO_CHANNEL
                if (replace) {
                    update(registrationRef, "ticketVersion" to ticketVersion, "ticketSecret" to newTicketSecret(), "updatedAt" to at)
                }
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(actor, if (replace) "TICKET_REPLACED" else "TICKET_RESENT", "registration", registrationId, eventId, at, newValue = mapOf("ticketVersion" to ticketVersion)),
                )
                ResendOutcome.QUEUED
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while resending the ticket: ${e.toUserMessage()}")
        }
    }

    override suspend fun requestTicketAccess(eventId: String, email: String): RequestState<String> {
        return try {
            val response = Firebase.functions(region = FirebaseAppConfig.FUNCTIONS_REGION)
                .httpsCallable("requestTicketAccess")
                .invoke(TicketAccessRequest(eventId, email))
                .data<TicketAccessStarted>()
            RequestState.Success(data = response.requestId)
        } catch (e: Exception) {
            RequestState.Error("Error while requesting the code: ${e.toUserMessage()}")
        }
    }

    override suspend fun confirmTicketAccess(requestId: String, code: String, replace: Boolean): RequestState<TicketAccessOutcome> {
        return try {
            val response = Firebase.functions(region = FirebaseAppConfig.FUNCTIONS_REGION)
                .httpsCallable("confirmTicketAccess")
                .invoke(TicketAccessConfirm(requestId, code, replace))
                .data<TicketAccessResponse>()
            val ticket = response.ticket
            val outcome = if (response.result == "OK" && ticket != null) {
                TicketAccessOutcome.Granted(ticket.toDomain())
            } else if (response.result == "EXPIRED") {
                TicketAccessOutcome.Expired
            } else if (response.result == "TOO_MANY_ATTEMPTS") {
                TicketAccessOutcome.TooManyAttempts
            } else {
                TicketAccessOutcome.InvalidCode
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while checking the code: ${e.toUserMessage()}")
        }
    }

    private fun registrationFrom(document: DocumentSnapshot): Registration {
        return Registration(
            id = document.id,
            eventId = document.get(field = "eventId"),
            participantName = document.get(field = "participantName"),
            email = document.get(field = "email"),
            normalizedEmail = document.get(field = "normalizedEmail"),
            phone = document.get(field = "phone"),
            university = document.get(field = "university"),
            faculty = document.get(field = "faculty"),
            department = document.get(field = "department"),
            studentId = document.get(field = "studentId"),
            status = RegistrationStatus.fromRaw(document.get<String?>(field = "status")),
            ticketVersion = document.get(field = "ticketVersion"),
            registeredAt = document.get<Timestamp>(field = "registeredAt").toMillis(),
            deliveryPreference = DeliveryPreference.fromRaw(document.get<String?>(field = "deliveryPreference")),
            whatsappConsent = document.get<Boolean?>(field = "whatsappConsent") ?: false,
            attendanceStatus = AttendanceStatus.fromRaw(document.get<String?>(field = "attendanceStatus")),
            sessionCount = document.get<Int?>(field = "sessionCount") ?: 0,
            totalDurationSeconds = (document.get<Int?>(field = "totalDurationSeconds") ?: 0).toLong(),
            firstCheckInAt = document.get<Timestamp?>(field = "firstCheckInAt")?.toMillis(),
            lastCheckOutAt = document.get<Timestamp?>(field = "lastCheckOutAt")?.toMillis(),
        )
    }
}
