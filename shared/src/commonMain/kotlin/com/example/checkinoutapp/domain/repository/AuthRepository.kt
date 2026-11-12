package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getCurrentUserId(): String?
    fun readAccountFlow(): Flow<AuthAccount?>
    suspend fun signIn(
        email: String,
        password: String
    ): RequestState<Unit>
    suspend fun signOut(): RequestState<Unit>
}

data class AuthAccount(val uid: String, val email: String)
