package com.example.checkinoutapp.ui.preview

import com.example.checkinoutapp.domain.event.RegistrationAvailability
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.ScanMode
import com.example.checkinoutapp.domain.model.ScanOutcome
import com.example.checkinoutapp.domain.model.ScanResult
import com.example.checkinoutapp.feature.eventDetails.EventDetailsContent

// Sample values for @Preview composables only. Nothing here is shown by the running app.

private const val HOUR = 60 * 60 * 1000L
private const val PREVIEW_START = 1_794_646_800_000L // 2026-11-14 09:00 UTC

val previewEvent = ClubEvent(
    id = "preview",
    title = "AI & Robotics Workshop",
    description = "A hands-on afternoon building a line-following robot and training a small vision model to steer it.",
    category = "Workshop",
    location = "Innovation Lab, Building C",
    organizerName = "Scientific Club",
    startAt = PREVIEW_START,
    endAt = PREVIEW_START + 3 * HOUR,
    timezone = "UTC",
    registrationOpenAt = PREVIEW_START - 7 * 24 * HOUR,
    registrationCloseAt = PREVIEW_START - HOUR,
    capacity = 60,
    registrationCount = 42,
    allowReentry = false,
    featured = true,
    status = EventStatus.PUBLISHED,
)

val previewEventDetails = EventDetailsContent(event = previewEvent, availability = RegistrationAvailability.OPEN)

val previewCheckIn = ScanOutcome(
    result = ScanResult.VALID_CHECK_IN,
    mode = ScanMode.CHECK_IN,
    participantName = "Ahmed",
    eventTitle = "AI & Robotics Workshop",
    checkInAt = PREVIEW_START + 15 * 60 * 1000,
)

val previewCheckOut = ScanOutcome(
    result = ScanResult.VALID_CHECK_OUT,
    mode = ScanMode.CHECK_OUT,
    participantName = "Ahmed",
    checkInAt = PREVIEW_START + 15 * 60 * 1000,
    checkOutAt = PREVIEW_START + 3 * HOUR + 45 * 60 * 1000,
    durationSeconds = 12_600,
    totalDurationSeconds = 12_600,
)

val previewRevoked = ScanOutcome(result = ScanResult.TICKET_REVOKED, mode = ScanMode.CHECK_IN, participantName = "Ahmed")
