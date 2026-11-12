package com.example.checkinoutapp.domain.analytics

import com.example.checkinoutapp.domain.attendance.median
import com.example.checkinoutapp.domain.model.AttendanceSession
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.time.toLocal

/** One event's headline numbers, from the counters the backend keeps on the event document. */
data class EventComparison(
    val event: ClubEvent,
    val attendanceRate: Double?,
    val averageSessionSeconds: Double?,
)

data class MonthStat(val year: Int, val month: Int, val events: Int, val registrations: Int, val attendees: Int)

data class ClubAnalytics(
    val totalEvents: Int,
    val totalRegistrations: Int,
    val totalAttendees: Int,
    val attendanceRate: Double?,
    val completedCheckouts: Int,
    val incompleteSessions: Int,
    val averageSessionSeconds: Double?,
    /** Median over the completed sessions inside the loaded window; null when there are none. */
    val medianSessionSeconds: Double?,
    val comparisons: List<EventComparison>,
    val mostAttended: List<EventComparison>,
    val longestStays: List<EventComparison>,
    val months: List<MonthStat>,
    val arrivalsByDay: List<Bucket>,
)

private const val TOP = 5
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

fun ClubEvent.comparison(): EventComparison = EventComparison(
    event = this,
    attendanceRate = if (registrationCount <= 0) null else (attendeeCount.toDouble() / registrationCount).coerceAtMost(1.0),
    averageSessionSeconds = if (completedSessionCount <= 0) null else completedDurationSeconds.toDouble() / completedSessionCount,
)

/**
 * Club-wide figures. Drafts and archived events are left out: they are not activity.
 * [windowSessions] is a bounded recent slice of sessions, used only for the median and the trend.
 */
fun computeClubAnalytics(events: List<ClubEvent>, windowSessions: List<AttendanceSession>): ClubAnalytics {
    val active = events.filter { it.status == EventStatus.PUBLISHED || it.status == EventStatus.COMPLETED }
    val comparisons = active.sortedByDescending { it.startAt }.map { it.comparison() }
    val registrations = active.sumOf { it.registrationCount }
    val attendees = active.sumOf { it.attendeeCount }
    val completed = active.sumOf { it.completedSessionCount }
    val completedSeconds = active.sumOf { it.completedDurationSeconds }
    val months = active.groupBy { it.startAt.toLocal(it.timezone).let { local -> local.year to (local.month.ordinal + 1) } }
        .map { (key, list) -> MonthStat(key.first, key.second, list.size, list.sumOf { it.registrationCount }, list.sumOf { it.attendeeCount }) }
        .sortedWith(compareBy({ it.year }, { it.month }))
    return ClubAnalytics(
        totalEvents = active.size,
        totalRegistrations = registrations,
        totalAttendees = attendees,
        attendanceRate = if (registrations <= 0) null else (attendees.toDouble() / registrations).coerceAtMost(1.0),
        completedCheckouts = completed,
        incompleteSessions = active.sumOf { it.activeSessionCount },
        averageSessionSeconds = if (completed <= 0) null else completedSeconds.toDouble() / completed,
        medianSessionSeconds = median(windowSessions.filter { it.status.isClosed }.mapNotNull { it.durationSeconds }),
        comparisons = comparisons,
        mostAttended = comparisons.filter { it.event.attendeeCount > 0 }.sortedByDescending { it.event.attendeeCount }.take(TOP),
        longestStays = comparisons.filter { it.averageSessionSeconds != null }.sortedByDescending { it.averageSessionSeconds }.take(TOP),
        months = months,
        arrivalsByDay = histogram(windowSessions.map { it.checkInAt }, DAY_MILLIS, maxBins = 120),
    )
}

data class ParticipantHistoryEntry(val registration: Registration, val event: ClubEvent?)

/** A person's registrations across events, newest first, joined with the events that are loaded. */
fun participantHistory(registrations: List<Registration>, events: List<ClubEvent>): List<ParticipantHistoryEntry> {
    val byId = events.associateBy { it.id }
    return registrations.sortedByDescending { it.registeredAt }.map { ParticipantHistoryEntry(it, byId[it.eventId]) }
}
