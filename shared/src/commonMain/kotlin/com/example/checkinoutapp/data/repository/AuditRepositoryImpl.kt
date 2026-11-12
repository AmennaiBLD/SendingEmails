package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.data.firebase.toMillis
import com.example.checkinoutapp.domain.model.AuditLog
import com.example.checkinoutapp.domain.repository.AuditRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class AuditRepositoryImpl : AuditRepository {
    override fun readAuditLogsFlow(actorId: String?, limit: Int): Flow<RequestState<List<AuditLog>>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            val collection = database.collection(collectionPath = "auditLogs")
            val query = if (actorId != null) {
                collection.where { "actorId" equalTo actorId }
            } else {
                collection
            }
            query.orderBy("createdAt", Direction.DESCENDING)
                .limit(limit)
                .snapshots
                .collectLatest { snapshot ->
                    val logs = snapshot.documents.mapNotNull { document ->
                        try {
                            auditLogFrom(document)
                        } catch (e: Exception) {
                            println("[club] Skipped an unreadable audit entry '${document.id}': ${e.message}")
                            null
                        }
                    }
                    send(RequestState.Success(data = logs))
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the audit log: ${e.toUserMessage()}"))
        }
    }

    private fun auditLogFrom(document: DocumentSnapshot): AuditLog {
        return AuditLog(
            id = document.id,
            actorId = document.get(field = "actorId"),
            actorName = document.get<String?>(field = "actorName") ?: "",
            action = document.get(field = "action"),
            entityType = document.get<String?>(field = "entityType") ?: "",
            entityId = document.get<String?>(field = "entityId") ?: "",
            eventId = document.get(field = "eventId"),
            reason = document.get(field = "reason"),
            details = document.get<String?>(field = "details") ?: "",
            createdAt = document.get<Timestamp>(field = "createdAt").toMillis(),
        )
    }
}
