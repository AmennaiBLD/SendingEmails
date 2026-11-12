package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.data.firebase.auditLog
import com.example.checkinoutapp.data.firebase.currentActor
import com.example.checkinoutapp.data.firebase.millisTimestamp
import com.example.checkinoutapp.data.firebase.nowMillis
import com.example.checkinoutapp.data.firebase.toMillis
import com.example.checkinoutapp.domain.event.slugify
import com.example.checkinoutapp.domain.event.validateEventDraft
import com.example.checkinoutapp.domain.event.wireName
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EventDraft
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.EventSaveOutcome
import com.example.checkinoutapp.domain.repository.EventStatusOutcome
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class EventRepositoryImpl : EventRepository {
    override fun readPublicEventsFlow(limit: Int): Flow<RequestState<List<ClubEvent>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "events")
                .where { "status" inArray listOf(EventStatus.PUBLISHED.name, EventStatus.COMPLETED.name) }
                .orderBy("startAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val events = query.documents.mapNotNull { document ->
                        try {
                            eventFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable event '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = events))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the events: ${e.toUserMessage()}"))
        }
    }

    override fun readAllEventsFlow(limit: Int): Flow<RequestState<List<ClubEvent>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "events")
                .orderBy("startAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { query ->
                    val events = query.documents.mapNotNull { document ->
                        try {
                            eventFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable event '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = events))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the events: ${e.toUserMessage()}"))
        }
    }

    override fun readEventByIdFlow(id: String): Flow<RequestState<ClubEvent?>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "events")
                .document(id)
                .snapshots
                .collectLatest { document ->
                    if (document.exists) {
                        send(RequestState.Success(data = eventFrom(document)))
                    } else {
                        send(RequestState.Success(data = null))
                    }
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the event: ${e.toUserMessage()}"))
        }
    }

    /** Creates (no `eventId`) or updates an event. firestore.rules re-checks the dates and limits. */
    override suspend fun saveEvent(draft: EventDraft): RequestState<EventSaveOutcome> {
        return try {
            val invalid = validateEventDraft(draft)
            if (invalid.isNotEmpty()) return RequestState.Success(data = EventSaveOutcome.Invalid(invalid.map { it.wireName() }))
            val actor = currentActor()
            val database = Firebase.firestore
            val at = millisTimestamp(nowMillis())
            val fields = mapOf(
                "title" to draft.title.trim(),
                "description" to draft.description.trim(),
                "coverImageUrl" to "",
                "category" to draft.category.trim(),
                "location" to draft.location.trim(),
                "organizerName" to draft.organizerName.trim(),
                "timezone" to draft.timezone,
                "startAt" to millisTimestamp(draft.startAt),
                "endAt" to millisTimestamp(draft.endAt),
                "registrationOpenAt" to millisTimestamp(draft.registrationOpenAt),
                "registrationCloseAt" to millisTimestamp(draft.registrationCloseAt),
                "capacity" to draft.capacity,
                "allowReentry" to draft.allowReentry,
                "featured" to draft.featured,
                "updatedAt" to at,
            )
            val eventId = draft.eventId
            if (eventId == null) {
                val eventRef = database.collection(collectionPath = "events").document
                database.batch().apply {
                    set(
                        eventRef,
                        fields + mapOf(
                            "id" to eventRef.id,
                            "slug" to "${slugify(draft.title)}-${eventRef.id.take(6).lowercase()}",
                            "status" to EventStatus.DRAFT.name,
                            "registrationCount" to 0,
                            "attendeeCount" to 0,
                            "activeSessionCount" to 0,
                            "completedSessionCount" to 0,
                            "completedDurationSeconds" to 0,
                            "lastRegistrationId" to null,
                            "createdBy" to actor.uid,
                            "createdAt" to at,
                            "publishedAt" to null,
                        ),
                    )
                    set(
                        database.collection(collectionPath = "auditLogs").document,
                        auditLog(actor, "EVENT_CREATED", "event", eventRef.id, eventRef.id, at, newValue = mapOf("title" to draft.title.trim())),
                    )
                }.commit()
                return RequestState.Success(data = EventSaveOutcome.Saved(eventRef.id))
            }

            val eventRef = database.collection(collectionPath = "events").document(eventId)
            val outcome = database.runTransaction {
                val existing = get(eventRef)
                if (!existing.exists) return@runTransaction EventSaveOutcome.NotFound
                if (draft.capacity > 0 && draft.capacity < (existing.get<Int?>(field = "registrationCount") ?: 0)) {
                    return@runTransaction EventSaveOutcome.CapacityBelowRegistrations
                }
                update(eventRef, *fields.toList().toTypedArray())
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(actor, "EVENT_UPDATED", "event", eventId, eventId, at, newValue = mapOf("title" to draft.title.trim())),
                )
                EventSaveOutcome.Saved(eventId)
            }
            if (outcome is EventSaveOutcome.Saved) syncAssignments(eventId)
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while saving the event: ${e.toUserMessage()}")
        }
    }

    override suspend fun updateEventStatus(eventId: String, status: EventStatus): RequestState<EventStatusOutcome> {
        return try {
            val actor = currentActor()
            val database = Firebase.firestore
            val eventRef = database.collection(collectionPath = "events").document(eventId)
            val outcome = database.runTransaction {
                val event = get(eventRef)
                if (!event.exists) return@runTransaction EventStatusOutcome.EVENT_NOT_FOUND
                val previous = EventStatus.fromRaw(event.get<String?>(field = "status"))
                if (status !in previous.nextStatuses) return@runTransaction EventStatusOutcome.TRANSITION_NOT_ALLOWED
                val at = millisTimestamp(nowMillis())
                if (status == EventStatus.PUBLISHED && event.get<Timestamp?>(field = "publishedAt") == null) {
                    update(eventRef, "status" to status.name, "publishedAt" to at, "updatedAt" to at)
                } else {
                    update(eventRef, "status" to status.name, "updatedAt" to at)
                }
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(
                        actor, "EVENT_STATUS_CHANGED", "event", eventId, eventId, at,
                        previousValue = mapOf("status" to previous.name), newValue = mapOf("status" to status.name),
                    ),
                )
                EventStatusOutcome.UPDATED
            }
            if (outcome == EventStatusOutcome.UPDATED) syncAssignments(eventId)
            RequestState.Success(data = outcome)
        } catch (e: Exception) {
            RequestState.Error("Error while updating the event status: ${e.toUserMessage()}")
        }
    }

    /** Assignments carry a copy of the event's headline fields, so "My events" is one small query. */
    private suspend fun syncAssignments(eventId: String) {
        val database = Firebase.firestore
        val event = database.collection(collectionPath = "events").document(eventId).get()
        val assignments = database.collection(collectionPath = "eventOrganizers")
            .where { "eventId" equalTo eventId }
            .get()
        if (!event.exists || assignments.documents.isEmpty()) return
        database.batch().apply {
            assignments.documents.forEach { assignment ->
                update(
                    assignment.reference,
                    "eventTitle" to event.get<String>(field = "title"),
                    "eventLocation" to event.get<String>(field = "location"),
                    "eventStartAt" to event.get<Timestamp>(field = "startAt"),
                    "eventEndAt" to event.get<Timestamp>(field = "endAt"),
                    "eventTimezone" to event.get<String>(field = "timezone"),
                    "eventStatus" to event.get<String>(field = "status"),
                )
            }
        }.commit()
    }

    private fun eventFrom(document: DocumentSnapshot): ClubEvent {
        return ClubEvent(
            id = document.id,
            title = document.get(field = "title"),
            description = document.get(field = "description"),
            category = document.get(field = "category"),
            location = document.get(field = "location"),
            organizerName = document.get<String?>(field = "organizerName") ?: "",
            startAt = document.get<Timestamp>(field = "startAt").toMillis(),
            endAt = document.get<Timestamp>(field = "endAt").toMillis(),
            timezone = document.get(field = "timezone"),
            registrationOpenAt = document.get<Timestamp>(field = "registrationOpenAt").toMillis(),
            registrationCloseAt = document.get<Timestamp>(field = "registrationCloseAt").toMillis(),
            capacity = document.get(field = "capacity"),
            registrationCount = document.get(field = "registrationCount"),
            allowReentry = document.get<Boolean?>(field = "allowReentry") ?: false,
            featured = document.get<Boolean?>(field = "featured") ?: false,
            status = EventStatus.fromRaw(document.get<String?>(field = "status")),
            attendeeCount = document.get<Int?>(field = "attendeeCount") ?: 0,
            activeSessionCount = document.get<Int?>(field = "activeSessionCount") ?: 0,
            completedSessionCount = document.get<Int?>(field = "completedSessionCount") ?: 0,
            completedDurationSeconds = (document.get<Int?>(field = "completedDurationSeconds") ?: 0).toLong(),
        )
    }
}
