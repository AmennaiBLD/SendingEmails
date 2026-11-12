package com.example.checkinoutapp.domain

import com.example.checkinoutapp.domain.model.AttendanceSession
import com.example.checkinoutapp.domain.model.AttendanceStatus
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.DeliveryPreference
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.model.RegistrationStatus
import com.example.checkinoutapp.domain.model.SessionStatus

const val MINUTE = 60_000L
const val HOUR = 60 * MINUTE

/** 2026-11-14 09:00 UTC. The event runs 09:00–12:00. */
const val START = 1_794_646_800_000L
const val END = START + 3 * HOUR

fun event(
    id: String = "e1", status: EventStatus = EventStatus.PUBLISHED, capacity: Int = 0, registrationCount: Int = 0,
    startAt: Long = START, endAt: Long = END, title: String = "AI & Robotics Workshop", category: String = "Workshop",
    attendeeCount: Int = 0, completedSessionCount: Int = 0, completedDurationSeconds: Long = 0, activeSessionCount: Int = 0,
) = ClubEvent(
    id = id, title = title, description = "Build a robot", category = category, location = "Hall A", organizerName = "Club",
    startAt = startAt, endAt = endAt, timezone = "UTC", registrationOpenAt = startAt - 7 * 24 * HOUR, registrationCloseAt = startAt - HOUR,
    capacity = capacity, registrationCount = registrationCount, allowReentry = false, featured = false, status = status,
    attendeeCount = attendeeCount, activeSessionCount = activeSessionCount, completedSessionCount = completedSessionCount,
    completedDurationSeconds = completedDurationSeconds,
)

fun registration(id: String, name: String = "P $id", status: RegistrationStatus = RegistrationStatus.REGISTERED, faculty: String = "Science", eventId: String = "e1") =
    Registration(
        id = id, eventId = eventId, participantName = name, email = "$id@example.org", normalizedEmail = "$id@example.org", phone = "+213555000000",
        university = "USTHB", faculty = faculty, department = "CS", studentId = null, status = status, ticketVersion = 1,
        registeredAt = START - 24 * HOUR, deliveryPreference = DeliveryPreference.EMAIL_ONLY, whatsappConsent = false,
        attendanceStatus = AttendanceStatus.NONE, sessionCount = 0, totalDurationSeconds = 0, firstCheckInAt = null, lastCheckOutAt = null,
    )

/** A session for [registrationId]; [outAt] null = still checked in. */
fun session(registrationId: String, inAt: Long, outAt: Long?, id: String = "$registrationId-$inAt", corrected: Boolean = false) = AttendanceSession(
    id = id, eventId = "e1", registrationId = registrationId, participantName = "P $registrationId", faculty = "Science", department = "CS",
    checkInAt = inAt, checkOutAt = outAt, durationSeconds = outAt?.let { (it - inAt) / 1000 },
    status = when { outAt == null -> SessionStatus.ACTIVE; corrected -> SessionStatus.CORRECTED; else -> SessionStatus.COMPLETED },
    corrected = corrected, checkedInByName = "Org", checkedOutByName = outAt?.let { "Org" }, manual = false,
)
