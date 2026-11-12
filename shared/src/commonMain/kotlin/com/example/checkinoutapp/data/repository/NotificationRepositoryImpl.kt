package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.firebase.FirebaseAppConfig
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.data.dto.ResultResponse
import com.example.checkinoutapp.data.dto.RetryRequest
import com.example.checkinoutapp.data.firebase.toMillis
import com.example.checkinoutapp.domain.model.NotificationChannel
import com.example.checkinoutapp.domain.model.NotificationJob
import com.example.checkinoutapp.domain.model.NotificationKind
import com.example.checkinoutapp.domain.model.NotificationStatus
import com.example.checkinoutapp.domain.repository.NotificationRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class NotificationRepositoryImpl : NotificationRepository {
    override fun readNotificationsFlow(status: NotificationStatus?, limit: Int): Flow<RequestState<List<NotificationJob>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            val collection = database.collection(collectionPath = "notificationJobs")
            val query = if (status != null) {
                collection.where { "status" equalTo status.name }
            } else {
                collection
            }
            query.orderBy("createdAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { snapshot ->
                    val jobs = snapshot.documents.mapNotNull { document ->
                        try {
                            notificationFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable notification '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = jobs))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the notifications: ${e.toUserMessage()}"))
        }
    }

    override suspend fun retryNotification(jobId: String): RequestState<NotificationStatus> {
        return try {
            val response = Firebase.functions(region = FirebaseAppConfig.FUNCTIONS_REGION)
                .httpsCallable("retryNotification")
                .invoke(RetryRequest(jobId))
                .data<ResultResponse>()
            RequestState.Success(data = NotificationStatus.fromRaw(response.status))
        } catch (e: Exception) {
            RequestState.Error("Error while retrying the notification: ${e.toUserMessage()}")
        }
    }

    private fun notificationFrom(document: DocumentSnapshot): NotificationJob {
        return NotificationJob(
            id = document.id,
            registrationId = document.get(field = "registrationId"),
            eventId = document.get(field = "eventId"),
            eventTitle = document.get<String?>(field = "eventTitle") ?: "",
            participantName = document.get<String?>(field = "participantName") ?: "",
            channel = NotificationChannel.fromRaw(document.get<String?>(field = "channel")),
            kind = NotificationKind.fromRaw(document.get<String?>(field = "kind")),
            recipient = document.get(field = "recipient"),
            status = NotificationStatus.fromRaw(document.get<String?>(field = "status")),
            provider = document.get(field = "provider"),
            providerMessageId = document.get(field = "providerMessageId"),
            attemptCount = document.get<Int?>(field = "attemptCount") ?: 0,
            lastError = document.get(field = "lastError"),
            createdAt = document.get<Timestamp>(field = "createdAt").toMillis(),
            sentAt = document.get<Timestamp?>(field = "sentAt")?.toMillis(),
            deliveredAt = document.get<Timestamp?>(field = "deliveredAt")?.toMillis(),
        )
    }
}
