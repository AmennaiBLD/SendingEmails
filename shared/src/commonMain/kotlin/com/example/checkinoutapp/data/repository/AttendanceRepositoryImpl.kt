package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.data.firebase.asAuditValue
import com.example.checkinoutapp.data.firebase.auditLog
import com.example.checkinoutapp.data.firebase.currentActor
import com.example.checkinoutapp.data.firebase.millisTimestamp
import com.example.checkinoutapp.data.firebase.nowMillis
import com.example.checkinoutapp.data.firebase.toMillis
import com.example.checkinoutapp.domain.model.AttendanceSession
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.RegistrationStatus
import com.example.checkinoutapp.domain.model.ScanMode
import com.example.checkinoutapp.domain.model.ScanOutcome
import com.example.checkinoutapp.domain.model.ScanResult
import com.example.checkinoutapp.domain.model.SessionStatus
import com.example.checkinoutapp.domain.repository.AttendanceRepository
import com.example.checkinoutapp.domain.repository.CorrectionOutcome
import com.example.checkinoutapp.domain.ticket.parseTicketCode
import com.example.checkinoutapp.domain.ticket.sameSecret
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class AttendanceRepositoryImpl : AttendanceRepository {

    /**
     * The one place attendance changes state. The same QR serves both modes. Every check and
     * write happens in one transaction over the registration, which carries the attendance state
     * (`activeSessionId`, `sessionCount`): two organizers scanning the same ticket at once cannot
     * both check it in, because the second transaction re-reads it and answers ALREADY_CHECKED_IN.
     */
    override suspend fun scan(mode: ScanMode, eventId: String, qr: String?, registrationId: String?): RequestState<ScanOutcome> {
        return try {
            // A QR scan carries the ticket code; the manual lookup sends a registration id instead.
            val ticket = if (registrationId == null) parseTicketCode(qr) else null
            if (registrationId == null && ticket == null) return RequestState.Success(data = ScanOutcome(ScanResult.INVALID_QR, mode))
            val actor = currentActor()
            val database = Firebase.firestore
            val registrationRef = database.collection(collectionPath = "registrations").document(registrationId ?: ticket!!.registrationId)
            val eventRef = database.collection(collectionPath = "events").document(eventId)

            val outcome = database.runTransaction {
                val registration = get(registrationRef)
                if (!registration.exists) return@runTransaction ScanOutcome(ScanResult.REGISTRATION_NOT_FOUND, mode)
                val event = get(eventRef)
                val name = registration.get<String>(field = "participantName")
                val title = if (event.exists) event.get<String?>(field = "title") else null
                fun answer(result: ScanResult) = ScanOutcome(result, mode, name, title)

                if (registration.get<String>(field = "eventId") != eventId) return@runTransaction answer(ScanResult.EVENT_MISMATCH)
                if (ticket != null) {
                    if (ticket.version != registration.get<Int?>(field = "ticketVersion")) return@runTransaction answer(ScanResult.TICKET_REVOKED)
                    if (!sameSecret(ticket.secret, registration.get<String?>(field = "ticketSecret") ?: "")) return@runTransaction answer(ScanResult.INVALID_QR)
                }
                val status = registration.get<String?>(field = "status")
                if (status == RegistrationStatus.CANCELLED.name) return@runTransaction answer(ScanResult.REGISTRATION_CANCELLED)
                if (status == RegistrationStatus.PENDING.name) return@runTransaction answer(ScanResult.NOT_APPROVED)
                if (status != RegistrationStatus.REGISTERED.name) return@runTransaction answer(ScanResult.REGISTRATION_NOT_FOUND)
                if (!event.exists) return@runTransaction answer(ScanResult.EVENT_NOT_OPEN)

                val now = nowMillis()
                val at = millisTimestamp(now)
                val eventStatus = event.get<String?>(field = "status")
                val sessionCount = registration.get<Int?>(field = "sessionCount") ?: 0
                val activeSessionId = registration.get<String?>(field = "activeSessionId")
                val totalSeconds = registration.get<Int?>(field = "totalDurationSeconds") ?: 0
                val lastCheckOutAt = registration.get<Timestamp?>(field = "lastCheckOutAt")?.toMillis()

                if (mode == ScanMode.CHECK_IN) {
                    val opens = event.get<Timestamp>(field = "startAt").toMillis() - CHECK_IN_OPENS_BEFORE_START_MS
                    val closes = event.get<Timestamp>(field = "endAt").toMillis() + CHECK_IN_CLOSES_AFTER_END_MS
                    if (eventStatus != EventStatus.PUBLISHED.name || now < opens || now > closes) return@runTransaction answer(ScanResult.EVENT_NOT_OPEN)
                    if (activeSessionId != null) {
                        val active = get(database.collection(collectionPath = "attendanceSessions").document(activeSessionId))
                        return@runTransaction answer(ScanResult.ALREADY_CHECKED_IN).copy(checkInAt = active.get<Timestamp?>(field = "checkInAt")?.toMillis())
                    }
                    // Re-entry off: one session per registration, ever.
                    if (sessionCount > 0 && event.get<Boolean?>(field = "allowReentry") != true) {
                        return@runTransaction answer(ScanResult.ALREADY_CHECKED_OUT).copy(checkOutAt = lastCheckOutAt, totalDurationSeconds = totalSeconds.toLong())
                    }
                    val sessionRef = database.collection(collectionPath = "attendanceSessions").document
                    set(
                        sessionRef,
                        mapOf(
                            "id" to sessionRef.id,
                            "eventId" to eventId,
                            "registrationId" to registrationRef.id,
                            "participantId" to null,
                            "participantName" to name,
                            "faculty" to (registration.get<String?>(field = "faculty") ?: ""),
                            "department" to (registration.get<String?>(field = "department") ?: ""),
                            "checkInAt" to at,
                            "checkOutAt" to null,
                            "checkedInBy" to actor.uid,
                            "checkedInByName" to actor.name,
                            "checkedOutBy" to null,
                            "checkedOutByName" to null,
                            "durationSeconds" to null,
                            "status" to SessionStatus.ACTIVE.name,
                            "corrected" to false,
                            "method" to if (ticket != null) "QR" else "MANUAL",
                            "createdAt" to at,
                            "updatedAt" to at,
                        ),
                    )
                    update(
                        registrationRef,
                        "attendanceStatus" to "ACTIVE",
                        "activeSessionId" to sessionRef.id,
                        "sessionCount" to sessionCount + 1,
                        "firstCheckInAt" to (registration.get<Timestamp?>(field = "firstCheckInAt") ?: at),
                        "updatedAt" to at,
                    )
                    update(
                        eventRef,
                        "attendeeCount" to (event.get<Int?>(field = "attendeeCount") ?: 0) + if (sessionCount == 0) 1 else 0,
                        "activeSessionCount" to (event.get<Int?>(field = "activeSessionCount") ?: 0) + 1,
                        "updatedAt" to at,
                    )
                    set(
                        database.collection(collectionPath = "auditLogs").document,
                        auditLog(actor, "CHECK_IN", "attendanceSession", sessionRef.id, eventId, at, newValue = mapOf("registrationId" to registrationRef.id, "participantName" to name)),
                    )
                    return@runTransaction answer(ScanResult.VALID_CHECK_IN).copy(checkInAt = now)
                }

                // CHECK_OUT: allowed while a session is open, so nobody is locked in after the event.
                if (eventStatus != EventStatus.PUBLISHED.name && eventStatus != EventStatus.COMPLETED.name) return@runTransaction answer(ScanResult.EVENT_NOT_OPEN)
                if (activeSessionId == null) {
                    return@runTransaction if (sessionCount > 0) {
                        answer(ScanResult.ALREADY_CHECKED_OUT).copy(checkOutAt = lastCheckOutAt, totalDurationSeconds = totalSeconds.toLong())
                    } else {
                        answer(ScanResult.NOT_CHECKED_IN)
                    }
                }
                val sessionRef = database.collection(collectionPath = "attendanceSessions").document(activeSessionId)
                val session = get(sessionRef)
                if (!session.exists || session.get<String?>(field = "status") != SessionStatus.ACTIVE.name) return@runTransaction answer(ScanResult.NOT_CHECKED_IN)
                val checkInAt = session.get<Timestamp>(field = "checkInAt").toMillis()
                val duration = maxOf(0L, (now - checkInAt) / 1000).toInt()
                val total = totalSeconds + duration
                update(
                    sessionRef,
                    "checkOutAt" to at,
                    "checkedOutBy" to actor.uid,
                    "checkedOutByName" to actor.name,
                    "durationSeconds" to duration,
                    "status" to SessionStatus.COMPLETED.name,
                    "updatedAt" to at,
                )
                update(registrationRef, "attendanceStatus" to "COMPLETED", "activeSessionId" to null, "totalDurationSeconds" to total, "lastCheckOutAt" to at, "updatedAt" to at)
                update(
                    eventRef,
                    "activeSessionCount" to maxOf(0, (event.get<Int?>(field = "activeSessionCount") ?: 0) - 1),
                    "completedSessionCount" to (event.get<Int?>(field = "completedSessionCount") ?: 0) + 1,
                    "completedDurationSeconds" to (event.get<Int?>(field = "completedDurationSeconds") ?: 0) + duration,
                    "updatedAt" to at,
                )
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(
                        actor, "CHECK_OUT", "attendanceSession", activeSessionId, eventId, at,
                        newValue = mapOf("registrationId" to registrationRef.id, "participantName" to name, "durationSeconds" to duration),
                    ),
                )
                answer(ScanResult.VALID_CHECK_OUT).copy(checkInAt = checkInAt, checkOutAt = now, durationSeconds = duration.toLong(), totalDurationSeconds = total.toLong())
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while checking the ticket: ${e.toUserMessage()}")
        }
    }

    override fun readSessionsByEventFlow(eventId: String, limit: Int): Flow<RequestState<List<AttendanceSession>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "attendanceSessions")
                .where { "eventId" equalTo eventId }
                .orderBy("checkInAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val sessions = query.documents.mapNotNull { document ->
                        try {
                            sessionFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable attendance session '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = sessions))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the attendance: ${e.toUserMessage()}"))
        }
    }

    override fun readSessionsSinceFlow(since: Long, limit: Int): Flow<RequestState<List<AttendanceSession>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "attendanceSessions")
                .where { "checkInAt" greaterThanOrEqualTo millisTimestamp(since) }
                .orderBy("checkInAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val sessions = query.documents.mapNotNull { document ->
                        try {
                            sessionFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable attendance session '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = sessions))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the attendance: ${e.toUserMessage()}"))
        }
    }

    /**
     * Manual correction of a session's times. Nothing is lost: every change writes an audit entry
     * with who, when, why, and the values before and after. A `checkOutAt` on an open session closes it.
     */
    override suspend fun correctAttendance(sessionId: String, checkInAt: Long?, checkOutAt: Long?, reason: String): RequestState<CorrectionOutcome> {
        return try {
            if (reason.trim().length < 3) return RequestState.Success(data = CorrectionOutcome.REASON_REQUIRED)
            if (checkInAt == null && checkOutAt == null) return RequestState.Success(data = CorrectionOutcome.INVALID_TIMES)
            val actor = currentActor()
            val database = Firebase.firestore
            val sessionRef = database.collection(collectionPath = "attendanceSessions").document(sessionId)
            val outcome = database.runTransaction {
                val session = get(sessionRef)
                if (!session.exists) return@runTransaction CorrectionOutcome.SESSION_NOT_FOUND
                val eventId = session.get<String>(field = "eventId")
                val registrationRef = database.collection(collectionPath = "registrations").document(session.get(field = "registrationId"))
                val eventRef = database.collection(collectionPath = "events").document(eventId)
                val registration = get(registrationRef)
                val event = get(eventRef)

                val now = nowMillis()
                val oldIn = session.get<Timestamp>(field = "checkInAt").toMillis()
                val oldOut = session.get<Timestamp?>(field = "checkOutAt")?.toMillis()
                val newIn = checkInAt ?: oldIn
                val newOut = checkOutAt ?: oldOut
                if (newIn > now || (newOut != null && (newOut <= newIn || newOut > now))) return@runTransaction CorrectionOutcome.INVALID_TIMES

                val oldStatus = session.get<String?>(field = "status")
                val wasActive = oldStatus == SessionStatus.ACTIVE.name
                val oldDuration = session.get<Int?>(field = "durationSeconds") ?: 0
                val duration = newOut?.let { ((it - newIn) / 1000).toInt() }
                val closes = wasActive && newOut != null
                val newStatus = if (newOut != null) SessionStatus.CORRECTED.name else SessionStatus.ACTIVE.name
                val at = millisTimestamp(now)

                update(
                    sessionRef,
                    "checkInAt" to millisTimestamp(newIn),
                    "checkOutAt" to newOut?.let { millisTimestamp(it) },
                    "durationSeconds" to duration,
                    "status" to newStatus,
                    "corrected" to true,
                    "checkedOutBy" to if (closes) actor.uid else session.get<String?>(field = "checkedOutBy"),
                    "checkedOutByName" to if (closes) actor.name else session.get<String?>(field = "checkedOutByName"),
                    "updatedAt" to at,
                )
                if (registration.exists) {
                    val total = maxOf(0, (registration.get<Int?>(field = "totalDurationSeconds") ?: 0) - oldDuration + (duration ?: 0))
                    if (closes) {
                        update(registrationRef, "totalDurationSeconds" to total, "attendanceStatus" to "COMPLETED", "activeSessionId" to null, "lastCheckOutAt" to millisTimestamp(newOut!!), "updatedAt" to at)
                    } else {
                        update(registrationRef, "totalDurationSeconds" to total, "updatedAt" to at)
                    }
                }
                if (event.exists) {
                    val completedSeconds = maxOf(0, (event.get<Int?>(field = "completedDurationSeconds") ?: 0) - oldDuration + (duration ?: 0))
                    if (closes) {
                        update(
                            eventRef,
                            "completedDurationSeconds" to completedSeconds,
                            "activeSessionCount" to maxOf(0, (event.get<Int?>(field = "activeSessionCount") ?: 0) - 1),
                            "completedSessionCount" to (event.get<Int?>(field = "completedSessionCount") ?: 0) + 1,
                            "updatedAt" to at,
                        )
                    } else {
                        update(eventRef, "completedDurationSeconds" to completedSeconds, "updatedAt" to at)
                    }
                }
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(
                        actor, "ATTENDANCE_CORRECTED", "attendanceSession", sessionId, eventId, at, reason = reason.trim(),
                        previousValue = mapOf("checkInAt" to oldIn.asAuditValue(), "checkOutAt" to oldOut?.asAuditValue(), "durationSeconds" to session.get<Int?>(field = "durationSeconds"), "status" to oldStatus),
                        newValue = mapOf("checkInAt" to newIn.asAuditValue(), "checkOutAt" to newOut?.asAuditValue(), "durationSeconds" to duration, "status" to newStatus),
                    ),
                )
                CorrectionOutcome.CORRECTED
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while correcting the attendance: ${e.toUserMessage()}")
        }
    }

    private fun sessionFrom(document: DocumentSnapshot): AttendanceSession {
        return AttendanceSession(
            id = document.id,
            eventId = document.get(field = "eventId"),
            registrationId = document.get(field = "registrationId"),
            participantName = document.get(field = "participantName"),
            faculty = document.get<String?>(field = "faculty") ?: "",
            department = document.get<String?>(field = "department") ?: "",
            checkInAt = document.get<Timestamp>(field = "checkInAt").toMillis(),
            checkOutAt = document.get<Timestamp?>(field = "checkOutAt")?.toMillis(),
            durationSeconds = document.get<Int?>(field = "durationSeconds")?.toLong(),
            status = SessionStatus.fromRaw(document.get<String?>(field = "status")),
            corrected = document.get<Boolean?>(field = "corrected") ?: false,
            checkedInByName = document.get<String?>(field = "checkedInByName") ?: "",
            checkedOutByName = document.get(field = "checkedOutByName"),
            manual = document.get<String?>(field = "method") == "MANUAL",
        )
    }

    private companion object {
        /** Check-in opens this long before the start and closes this long after the end. */
        const val CHECK_IN_OPENS_BEFORE_START_MS = 3 * 60 * 60 * 1000L
        const val CHECK_IN_CLOSES_AFTER_END_MS = 6 * 60 * 60 * 1000L
    }
}
