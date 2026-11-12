package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.ClubSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun readClubSettingsFlow(): Flow<RequestState<ClubSettings>>
    suspend fun updateClubSettings(settings: ClubSettings): RequestState<Unit>
}
