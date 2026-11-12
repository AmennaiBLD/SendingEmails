package com.example.checkinoutapp.domain.event

import com.example.checkinoutapp.domain.model.EventDraft
import com.example.checkinoutapp.domain.time.isKnownTimeZone

enum class EventField { TITLE, DESCRIPTION, CATEGORY, LOCATION, TIMEZONE, START, END, REGISTRATION_OPEN, REGISTRATION_CLOSE, CAPACITY }

/** The event rules, checked before saving for instant feedback. firestore.rules checks them again. */
fun validateEventDraft(draft: EventDraft): Set<EventField> = buildSet {
    if (draft.title.trim().length < 3) add(EventField.TITLE)
    if (draft.description.isBlank()) add(EventField.DESCRIPTION)
    if (draft.category.isBlank()) add(EventField.CATEGORY)
    if (draft.location.isBlank()) add(EventField.LOCATION)
    if (!isKnownTimeZone(draft.timezone)) add(EventField.TIMEZONE)
    if (draft.startAt <= 0) add(EventField.START)
    if (draft.endAt <= draft.startAt) add(EventField.END)
    if (draft.registrationOpenAt <= 0) add(EventField.REGISTRATION_OPEN)
    if (draft.registrationCloseAt <= draft.registrationOpenAt || draft.registrationCloseAt > draft.endAt) add(EventField.REGISTRATION_CLOSE)
    if (draft.capacity < 0) add(EventField.CAPACITY)
}

/** The backend names invalid fields by their wire name. */
fun eventFieldOf(serverName: String): EventField? = when (serverName) {
    "title" -> EventField.TITLE
    "description" -> EventField.DESCRIPTION
    "category" -> EventField.CATEGORY
    "location" -> EventField.LOCATION
    "timezone" -> EventField.TIMEZONE
    "startAt" -> EventField.START
    "endAt" -> EventField.END
    "registrationOpenAt" -> EventField.REGISTRATION_OPEN
    "registrationCloseAt" -> EventField.REGISTRATION_CLOSE
    "capacity" -> EventField.CAPACITY
    else -> null
}

/** The Firestore field an [EventField] is stored in. */
fun EventField.wireName(): String = when (this) {
    EventField.TITLE -> "title"
    EventField.DESCRIPTION -> "description"
    EventField.CATEGORY -> "category"
    EventField.LOCATION -> "location"
    EventField.TIMEZONE -> "timezone"
    EventField.START -> "startAt"
    EventField.END -> "endAt"
    EventField.REGISTRATION_OPEN -> "registrationOpenAt"
    EventField.REGISTRATION_CLOSE -> "registrationCloseAt"
    EventField.CAPACITY -> "capacity"
}

/** `ai-robotics-workshop`: lowercase ASCII words joined by dashes, for readable event URLs. */
fun slugify(title: String): String = title.lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')
    .take(60)
    .ifEmpty { "event" }
