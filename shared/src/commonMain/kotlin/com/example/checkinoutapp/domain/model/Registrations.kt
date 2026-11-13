package com.example.checkinoutapp.domain.model

/**
 * PENDING: applied, waiting for an administrator (no QR yet). REGISTERED: approved, has a QR.
 * REJECTED: not accepted. CANCELLED: approved, then cancelled.
 */
enum class RegistrationStatus {
    PENDING, REGISTERED, REJECTED, WAITLISTED, CANCELLED;

    companion object {
        fun fromRaw(raw: String?): RegistrationStatus = entries.firstOrNull { it.name == raw } ?: CANCELLED
    }
}

enum class DeliveryPreference {
    EMAIL_ONLY, WHATSAPP_ONLY, BOTH;

    val usesWhatsApp: Boolean get() = this != EMAIL_ONLY
    val usesEmail: Boolean get() = this != WHATSAPP_ONLY

    companion object {
        fun fromRaw(raw: String?): DeliveryPreference = entries.firstOrNull { it.name == raw } ?: EMAIL_ONLY
    }
}

enum class AttendanceStatus {
    NONE, ACTIVE, COMPLETED;

    companion object {
        fun fromRaw(raw: String?): AttendanceStatus = entries.firstOrNull { it.name == raw } ?: NONE
    }
}

data class Registration(
    val id: String,
    val eventId: String,
    val participantName: String,
    val email: String,
    val normalizedEmail: String,
    val phone: String,
    val university: String,
    val faculty: String,
    val department: String,
    val studentId: String?,
    val status: RegistrationStatus,
    val ticketVersion: Int,
    val registeredAt: Long,
    val deliveryPreference: DeliveryPreference,
    val whatsappConsent: Boolean,
    val attendanceStatus: AttendanceStatus,
    val sessionCount: Int,
    val totalDurationSeconds: Long,
    val firstCheckInAt: Long?,
    val lastCheckOutAt: Long?,
)

/** The public registration form, exactly as typed. Local workflow state, not backend data. */
data class RegistrationForm(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val university: String = "",
    val faculty: String = "",
    val department: String = "",
    val studentId: String = "",
    val deliveryPreference: DeliveryPreference = DeliveryPreference.EMAIL_ONLY,
    val whatsappConsent: Boolean = false,
    val privacyAccepted: Boolean = false,
)

enum class RegistrationField { FULL_NAME, EMAIL, PHONE, UNIVERSITY, FACULTY, DEPARTMENT, WHATSAPP_CONSENT, PRIVACY, DELIVERY }

/** A participant's ticket, shown to its owner. */
class Ticket(
    val registrationId: String,
    val eventId: String,
    val eventTitle: String,
    val participantName: String,
    val location: String,
    val timezone: String,
    val startAt: Long,
    val endAt: Long,
    val ticketVersion: Int,
    /** The QR content (domain/ticket/TicketCode.kt); the image is drawn on the device. */
    val ticketCode: String,
    val emailQueued: Boolean,
    val whatsappQueued: Boolean,
    /** False right after applying: no QR yet ([ticketCode] is empty); it is emailed once an administrator approves. */
    val approved: Boolean = true,
)
