package com.example.checkinoutapp.domain.model

enum class UserRole {
    PARTICIPANT, ORGANIZER, ADMIN;


    val canScan: Boolean get() = this == ORGANIZER || this == ADMIN
    val canAdminister: Boolean get() = this == ADMIN

    companion object {
        fun fromRaw(raw: String?): UserRole = entries.firstOrNull { it.name == raw } ?: PARTICIPANT
    }
}

enum class StaffField { FULL_NAME, EMAIL, PASSWORD }

data class AppUser(
    val uid: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val role: UserRole,
    val isActive: Boolean,
)

data class ClubSettings(
    val clubName: String = DEFAULT_CLUB_NAME,
    val tagline: String = "",
    val about: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val address: String = "",
    val websiteUrl: String = "",
    val instagramUrl: String = "",
    val facebookUrl: String = "",
    val linkedinUrl: String = "",
    /** How early a participant may leave and still count as having attended the whole event. */
    val attendanceGraceMinutes: Int = DEFAULT_GRACE_MINUTES,
) {
    companion object {
        const val DEFAULT_CLUB_NAME = "Scientific Club"
        const val DEFAULT_GRACE_MINUTES = 10
    }
}

data class OrganizerAssignment(
    val eventId: String,
    val organizerId: String,
    val organizerName: String,
    val organizerEmail: String,
    val active: Boolean,
    val eventTitle: String,
    val eventLocation: String,
    val eventStartAt: Long,
    val eventEndAt: Long,
    val eventTimezone: String,
    val eventStatus: EventStatus,
)

data class AuditLog(
    val id: String,
    val actorId: String,
    val actorName: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val eventId: String?,
    val reason: String?,
    val details: String,
    val createdAt: Long,
)
