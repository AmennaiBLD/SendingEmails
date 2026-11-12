package com.example.checkinoutapp.domain.repository

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.AttendanceSession
import com.example.checkinoutapp.domain.model.ScanMode
import com.example.checkinoutapp.domain.model.ScanOutcome
import kotlinx.coroutines.flow.Flow

interface AttendanceRepository {


    suspend fun scan(
        mode: ScanMode,
        eventId: String,
        qr: String?,
        registrationId: String?
    ): RequestState<ScanOutcome>


    fun readSessionsByEventFlow(
        eventId: String,
        limit: Int
    ): Flow<RequestState<List<AttendanceSession>>>


    fun readSessionsSinceFlow(
        since: Long,
        limit: Int
    ): Flow<RequestState<List<AttendanceSession>>>


    suspend fun correctAttendance(
        sessionId: String,
        checkInAt: Long?,
        checkOutAt: Long?,
        reason: String
    ): RequestState<CorrectionOutcome>


}

enum class CorrectionOutcome { CORRECTED, SESSION_NOT_FOUND, NOT_AUTHORIZED, INVALID_TIMES, REASON_REQUIRED, UNKNOWN }
