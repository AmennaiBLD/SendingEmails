package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.OrganizerAssignment
import kotlinx.coroutines.flow.Flow

interface OrganizerRepository {
    fun readAssignmentsByOrganizerFlow(
        organizerId: String,
        limit: Int
    ): Flow<RequestState<List<OrganizerAssignment>>>
    fun readAssignmentsByEventFlow(eventId: String): Flow<RequestState<List<OrganizerAssignment>>>
    suspend fun assignOrganizer(
        eventId: String,
        organizerId: String,
        active: Boolean
    ): RequestState<AssignOutcome>
}

enum class AssignOutcome { UPDATED, EVENT_NOT_FOUND, ORGANIZER_NOT_FOUND, UNKNOWN }
