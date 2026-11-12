package com.example.checkinoutapp.domain.event

import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EventStatus

/** Why registration is or is not possible right now. The backend re-checks all of it. */
enum class RegistrationAvailability {
    OPEN, NOT_YET_OPEN, CLOSED, FULL, EVENT_ENDED, NOT_PUBLISHED;

    val canRegister: Boolean get() = this == OPEN
}

fun ClubEvent.registrationAvailability(now: Long): RegistrationAvailability = when {
    status != EventStatus.PUBLISHED -> if (status == EventStatus.COMPLETED) RegistrationAvailability.EVENT_ENDED else RegistrationAvailability.NOT_PUBLISHED
    now >= endAt -> RegistrationAvailability.EVENT_ENDED
    now < registrationOpenAt -> RegistrationAvailability.NOT_YET_OPEN
    now > registrationCloseAt -> RegistrationAvailability.CLOSED
    capacity > 0 && registrationCount >= capacity -> RegistrationAvailability.FULL
    else -> RegistrationAvailability.OPEN
}

fun ClubEvent.isPast(now: Long): Boolean = status == EventStatus.COMPLETED || now >= endAt

enum class EventTimeFilter { UPCOMING, PAST, ALL }

data class EventQuery(
    val search: String = "",
    val category: String? = null,
    val time: EventTimeFilter = EventTimeFilter.UPCOMING,
)

/** Upcoming events soonest first; past events most recent first. */
fun filterEvents(events: List<ClubEvent>, query: EventQuery, now: Long): List<ClubEvent> {
    val needle = query.search.trim().lowercase()
    val matching = events.filter { event ->
        (query.category == null || event.category.equals(query.category, ignoreCase = true)) &&
            (needle.isEmpty() || event.title.lowercase().contains(needle) || event.description.lowercase().contains(needle) ||
                event.location.lowercase().contains(needle) || event.category.lowercase().contains(needle)) &&
            when (query.time) {
                EventTimeFilter.UPCOMING -> !event.isPast(now)
                EventTimeFilter.PAST -> event.isPast(now)
                EventTimeFilter.ALL -> true
            }
    }
    return when (query.time) {
        EventTimeFilter.PAST -> matching.sortedByDescending { it.startAt }
        else -> matching.sortedBy { it.startAt }
    }
}

fun categoriesOf(events: List<ClubEvent>): List<String> = events.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
