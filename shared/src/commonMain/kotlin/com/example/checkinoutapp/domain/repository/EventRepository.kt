package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EventDraft
import com.example.checkinoutapp.domain.model.EventStatus
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun readPublicEventsFlow(limit: Int): Flow<RequestState<List<ClubEvent>>>
    fun readAllEventsFlow(limit: Int): Flow<RequestState<List<ClubEvent>>>
    fun readEventByIdFlow(id: String): Flow<RequestState<ClubEvent?>>
    suspend fun saveEvent(draft: EventDraft): RequestState<EventSaveOutcome>
    suspend fun updateEventStatus(
        eventId: String,
        status: EventStatus
    ): RequestState<EventStatusOutcome>
}

sealed interface EventSaveOutcome {
    data class Saved(val eventId: String) : EventSaveOutcome
    data class Invalid(val fields: List<String>) : EventSaveOutcome
    data object NotFound : EventSaveOutcome
    data object CapacityBelowRegistrations : EventSaveOutcome
}

enum class EventStatusOutcome { UPDATED, EVENT_NOT_FOUND, TRANSITION_NOT_ALLOWED, UNKNOWN }
