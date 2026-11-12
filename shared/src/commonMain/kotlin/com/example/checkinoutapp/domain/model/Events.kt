package com.example.checkinoutapp.domain.model

enum class EventStatus {
    DRAFT, PUBLISHED, CANCELLED, COMPLETED, ARCHIVED;

    /** The status changes an administrator may make from here (mirrors the backend table). */
    val nextStatuses: List<EventStatus>
        get() = when (this) {
            DRAFT -> listOf(PUBLISHED, ARCHIVED)
            PUBLISHED -> listOf(DRAFT, CANCELLED, COMPLETED)
            CANCELLED -> listOf(ARCHIVED)
            COMPLETED -> listOf(PUBLISHED, ARCHIVED)
            ARCHIVED -> emptyList()
        }

    companion object {
        fun fromRaw(raw: String?): EventStatus = entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

/** All instants are epoch milliseconds (UTC). [timezone] is the IANA zone the event is shown in. */
data class ClubEvent(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val organizerName: String,
    val startAt: Long,
    val endAt: Long,
    val timezone: String,
    val registrationOpenAt: Long,
    val registrationCloseAt: Long,
    /** 0 means unlimited. */
    val capacity: Int,
    val registrationCount: Int,
    val allowReentry: Boolean,
    val featured: Boolean,
    val status: EventStatus,
    val attendeeCount: Int = 0,
    val activeSessionCount: Int = 0,
    val completedSessionCount: Int = 0,
    val completedDurationSeconds: Long = 0,
) {
    val scheduledDurationSeconds: Long get() = ((endAt - startAt) / 1000).coerceAtLeast(0)
    val seatsLeft: Int? get() = if (capacity <= 0) null else (capacity - registrationCount).coerceAtLeast(0)
}

/** What an administrator types into the event form. Dates are already instants. */
data class EventDraft(
    val eventId: String?,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val organizerName: String,
    val timezone: String,
    val startAt: Long,
    val endAt: Long,
    val registrationOpenAt: Long,
    val registrationCloseAt: Long,
    val capacity: Int,
    val allowReentry: Boolean,
    val featured: Boolean,
)

val EVENT_CATEGORIES = listOf("Workshop", "Talk", "Competition", "Training", "Conference", "Field trip", "Other")
