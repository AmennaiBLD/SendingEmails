package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.data.firebase.auditLog
import com.example.checkinoutapp.data.firebase.currentActor
import com.example.checkinoutapp.data.firebase.millisTimestamp
import com.example.checkinoutapp.data.firebase.nowMillis
import com.example.checkinoutapp.data.firebase.toMillis
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.OrganizerAssignment
import com.example.checkinoutapp.domain.repository.AssignOutcome
import com.example.checkinoutapp.domain.repository.OrganizerRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class OrganizerRepositoryImpl : OrganizerRepository {
    override fun readAssignmentsByOrganizerFlow(organizerId: String, limit: Int): Flow<RequestState<List<OrganizerAssignment>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "eventOrganizers")
                .where { ("organizerId" equalTo organizerId) and ("active" equalTo true) }
                .orderBy("eventStartAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val assignments = query.documents.mapNotNull { document ->
                        try {
                            assignmentFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable assignment '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = assignments))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading your events: ${e.toUserMessage()}"))
        }
    }

    override fun readAssignmentsByEventFlow(eventId: String): Flow<RequestState<List<OrganizerAssignment>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "eventOrganizers")
                .where { "eventId" equalTo eventId }
                .limit(100)
                .snapshots
                .collectLatest { query ->
                    val assignments = query.documents.mapNotNull { document ->
                        try {
                            assignmentFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable assignment '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = assignments))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the organizers: ${e.toUserMessage()}"))
        }
    }

    /** Gives (or takes back) an organizer's access to one event. The id is `<eventId>_<organizerId>`. */
    override suspend fun assignOrganizer(eventId: String, organizerId: String, active: Boolean): RequestState<AssignOutcome> {
        return try {
            val actor = currentActor()
            val database = Firebase.firestore
            val eventRef = database.collection(collectionPath = "events").document(eventId)
            val userRef = database.collection(collectionPath = "users").document(organizerId)
            val assignmentId = "${eventId}_$organizerId"
            val outcome = database.runTransaction {
                val event = get(eventRef)
                val user = get(userRef)
                if (!event.exists) return@runTransaction AssignOutcome.EVENT_NOT_FOUND
                val role = user.takeIf { it.exists }?.get<String?>(field = "role")
                if (role != "ORGANIZER" && role != "ADMIN") return@runTransaction AssignOutcome.ORGANIZER_NOT_FOUND
                val at = millisTimestamp(nowMillis())
                val organizerName = user.get<String?>(field = "fullName") ?: ""
                set(
                    database.collection(collectionPath = "eventOrganizers").document(assignmentId),
                    mapOf(
                        "eventId" to eventId,
                        "organizerId" to organizerId,
                        "organizerName" to organizerName,
                        "organizerEmail" to (user.get<String?>(field = "email") ?: ""),
                        "assignedBy" to actor.uid,
                        "assignedAt" to at,
                        "active" to active,
                        "eventTitle" to event.get<String>(field = "title"),
                        "eventLocation" to event.get<String>(field = "location"),
                        "eventStartAt" to event.get<Timestamp>(field = "startAt"),
                        "eventEndAt" to event.get<Timestamp>(field = "endAt"),
                        "eventTimezone" to event.get<String>(field = "timezone"),
                        "eventStatus" to event.get<String>(field = "status"),
                    ),
                )
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(
                        actor, if (active) "ORGANIZER_ASSIGNED" else "ORGANIZER_ACCESS_REVOKED", "eventOrganizer", assignmentId, eventId, at,
                        newValue = mapOf("organizerId" to organizerId, "organizerName" to organizerName, "active" to active),
                    ),
                )
                AssignOutcome.UPDATED
            }
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while assigning the organizer: ${e.toUserMessage()}")
        }
    }

    private fun assignmentFrom(document: DocumentSnapshot): OrganizerAssignment {
        return OrganizerAssignment(
            eventId = document.get(field = "eventId"),
            organizerId = document.get(field = "organizerId"),
            organizerName = document.get<String?>(field = "organizerName") ?: "",
            organizerEmail = document.get<String?>(field = "organizerEmail") ?: "",
            active = document.get(field = "active"),
            eventTitle = document.get<String?>(field = "eventTitle") ?: "",
            eventLocation = document.get<String?>(field = "eventLocation") ?: "",
            eventStartAt = document.get<Timestamp?>(field = "eventStartAt")?.toMillis() ?: 0,
            eventEndAt = document.get<Timestamp?>(field = "eventEndAt")?.toMillis() ?: 0,
            eventTimezone = document.get<String?>(field = "eventTimezone") ?: "UTC",
            eventStatus = EventStatus.fromRaw(document.get<String?>(field = "eventStatus")),
        )
    }
}
