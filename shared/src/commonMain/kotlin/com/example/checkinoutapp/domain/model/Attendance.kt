package com.example.checkinoutapp.domain.model

enum class ScanMode { CHECK_IN, CHECK_OUT }

/** Every answer the backend can give to a scan. Unknown values map to [UNKNOWN], never a crash. */
enum class ScanResult {
    VALID_CHECK_IN, VALID_CHECK_OUT, INVALID_QR, REGISTRATION_NOT_FOUND, REGISTRATION_CANCELLED, EVENT_NOT_OPEN,
    ALREADY_CHECKED_IN, NOT_CHECKED_IN, ALREADY_CHECKED_OUT, ORGANIZER_NOT_AUTHORIZED, EVENT_MISMATCH, TICKET_REVOKED,
    UNKNOWN;

    val isSuccess: Boolean get() = this == VALID_CHECK_IN || this == VALID_CHECK_OUT
    /** Not a success, but nothing is wrong with the ticket: the scan was simply a repeat. */
    val isRepeat: Boolean get() = this == ALREADY_CHECKED_IN || this == ALREADY_CHECKED_OUT

    companion object {
        fun fromRaw(raw: String?): ScanResult = entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}

data class ScanOutcome(
    val result: ScanResult,
    val mode: ScanMode,
    val participantName: String? = null,
    val eventTitle: String? = null,
    val checkInAt: Long? = null,
    val checkOutAt: Long? = null,
    val durationSeconds: Long? = null,
    val totalDurationSeconds: Long? = null,
)

enum class SessionStatus {
    ACTIVE, COMPLETED, CORRECTED;

    /** A corrected session with a departure time counts as completed. */
    val isClosed: Boolean get() = this != ACTIVE

    companion object {
        fun fromRaw(raw: String?): SessionStatus = entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

data class AttendanceSession(
    val id: String,
    val eventId: String,
    val registrationId: String,
    val participantName: String,
    val faculty: String,
    val department: String,
    val checkInAt: Long,
    val checkOutAt: Long?,
    val durationSeconds: Long?,
    val status: SessionStatus,
    val corrected: Boolean,
    val checkedInByName: String,
    val checkedOutByName: String?,
    val manual: Boolean,
)
