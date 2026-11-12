package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.AuditLog
import kotlinx.coroutines.flow.Flow

interface AuditRepository {
    fun readAuditLogsFlow(
        actorId: String?,
        limit: Int
    ): Flow<RequestState<List<AuditLog>>>
}
