package com.example.checkinoutapp.domain.model

enum class NotificationChannel {
    EMAIL, WHATSAPP;

    companion object {
        fun fromRaw(raw: String?): NotificationChannel = entries.firstOrNull { it.name == raw } ?: EMAIL
    }
}

enum class NotificationStatus {
    PENDING, PROCESSING, SENT, DELIVERED, FAILED, RETRYING;

    val canRetry: Boolean get() = this == FAILED || this == RETRYING || this == PENDING

    companion object {
        fun fromRaw(raw: String?): NotificationStatus = entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class NotificationKind {
    /** "Registration received, wait for the approval": no QR. */
    REGISTRATION_RECEIVED,
    /** An administrator registered someone directly: the QR ticket. */
    REGISTRATION_CONFIRMATION,
    /** An administrator approved an application: the QR ticket. */
    REGISTRATION_APPROVED,
    TICKET_REPLACEMENT;

    companion object {
        fun fromRaw(raw: String?): NotificationKind = entries.firstOrNull { it.name == raw } ?: REGISTRATION_CONFIRMATION
    }
}

data class NotificationJob(
    val id: String,
    val registrationId: String,
    val eventId: String,
    val eventTitle: String,
    val participantName: String,
    val channel: NotificationChannel,
    val kind: NotificationKind,
    val recipient: String,
    val status: NotificationStatus,
    val provider: String?,
    val providerMessageId: String?,
    val attemptCount: Int,
    val lastError: String?,
    val createdAt: Long,
    val sentAt: Long?,
    val deliveredAt: Long?,
) {
    /** Sent by a mock provider: recorded as sent, but nothing left the server. */
    val isSimulated: Boolean get() = provider == MOCK_PROVIDER

    companion object {
        const val MOCK_PROVIDER = "mock"
    }
}
