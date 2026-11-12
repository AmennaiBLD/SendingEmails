package com.example.checkinoutapp.data.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.core.toUserMessage
import com.example.checkinoutapp.data.firebase.auditLog
import com.example.checkinoutapp.data.firebase.currentActor
import com.example.checkinoutapp.data.firebase.millisTimestamp
import com.example.checkinoutapp.data.firebase.nowMillis
import com.example.checkinoutapp.domain.model.ClubSettings
import com.example.checkinoutapp.domain.repository.SettingsRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class SettingsRepositoryImpl : SettingsRepository {

    override fun readClubSettingsFlow(): Flow<RequestState<ClubSettings>> = channelFlow {
        try {
            send(RequestState.Loading)
            val database = Firebase.firestore
            database.collection(collectionPath = "settings")
                .document("club")
                .snapshots
                .collectLatest { document ->
                    if (document.exists) {
                        // Every field is optional: a club that has not filled in its details yet still loads.
                        val settings = ClubSettings(
                            clubName = document.get<String?>(field = "clubName") ?: ClubSettings.DEFAULT_CLUB_NAME,
                            tagline = document.get<String?>(field = "tagline") ?: "",
                            about = document.get<String?>(field = "about") ?: "",
                            contactEmail = document.get<String?>(field = "contactEmail") ?: "",
                            contactPhone = document.get<String?>(field = "contactPhone") ?: "",
                            address = document.get<String?>(field = "address") ?: "",
                            websiteUrl = document.get<String?>(field = "websiteUrl") ?: "",
                            instagramUrl = document.get<String?>(field = "instagramUrl") ?: "",
                            facebookUrl = document.get<String?>(field = "facebookUrl") ?: "",
                            linkedinUrl = document.get<String?>(field = "linkedinUrl") ?: "",
                            attendanceGraceMinutes = document.get<Int?>(field = "attendanceGraceMinutes") ?: ClubSettings.DEFAULT_GRACE_MINUTES,
                        )
                        send(RequestState.Success(data = settings))
                    } else {
                        send(RequestState.Success(data = ClubSettings()))
                    }
                }
        } catch (e: Exception) {
            send(RequestState.Error("Error while reading the club settings: ${e.toUserMessage()}"))
        }
    }

    override suspend fun updateClubSettings(settings: ClubSettings): RequestState<Unit> {
        return try {
            val actor = currentActor()
            val database = Firebase.firestore
            val at = millisTimestamp(nowMillis())
            database.batch().apply {
                set(
                    database.collection(collectionPath = "settings").document("club"),
                    mapOf(
                        "clubName" to settings.clubName.trim().take(80).ifBlank { ClubSettings.DEFAULT_CLUB_NAME },
                        "tagline" to settings.tagline.trim().take(160),
                        "about" to settings.about.trim().take(2000),
                        "contactEmail" to settings.contactEmail.trim().take(254),
                        "contactPhone" to settings.contactPhone.trim().take(40),
                        "address" to settings.address.trim().take(200),
                        "websiteUrl" to settings.websiteUrl.trim().take(300),
                        "instagramUrl" to settings.instagramUrl.trim().take(300),
                        "facebookUrl" to settings.facebookUrl.trim().take(300),
                        "linkedinUrl" to settings.linkedinUrl.trim().take(300),
                        "attendanceGraceMinutes" to settings.attendanceGraceMinutes.coerceIn(0, 240),
                        "updatedAt" to at,
                    ),
                    merge = true,
                )
                set(
                    database.collection(collectionPath = "auditLogs").document,
                    auditLog(actor, "CLUB_SETTINGS_UPDATED", "settings", "club", null, at),
                )
            }.commit()
            RequestState.Success(data = Unit)
        } catch (e: Exception) {
            RequestState.Error("Error while saving the club settings: ${e.toUserMessage()}")
        }
    }
}
