package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.AppUser
import com.example.checkinoutapp.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun readUserFlow(uid: String): Flow<RequestState<AppUser?>>
    fun readStaffFlow(limit: Int): Flow<RequestState<List<AppUser>>>

    suspend fun createStaff(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        role: UserRole
    ): RequestState<StaffOutcome>

    suspend fun updateAccess(
        uid: String,
        role: UserRole,
        isActive: Boolean
    ): RequestState<StaffOutcome>

}

enum class StaffOutcome { SAVED, INVALID_INPUT, EMAIL_IN_USE, USER_NOT_FOUND, CANNOT_CHANGE_SELF, UNKNOWN }
