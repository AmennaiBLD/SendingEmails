package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.NotificationJob
import com.example.checkinoutapp.domain.model.NotificationStatus
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun readNotificationsFlow(
        status: NotificationStatus?,
        limit: Int
    ): Flow<RequestState<List<NotificationJob>>>
    suspend fun retryNotification(jobId: String): RequestState<NotificationStatus>
}
