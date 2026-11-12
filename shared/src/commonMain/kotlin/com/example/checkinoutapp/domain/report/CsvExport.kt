package com.example.checkinoutapp.domain.report

import com.example.checkinoutapp.domain.analytics.ClubAnalytics
import com.example.checkinoutapp.domain.analytics.EventAnalytics
import com.example.checkinoutapp.domain.model.AttendanceSession
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.time.isoLocal

/** A byte-order mark makes Excel read the file as UTF-8, so Arabic and accented names survive. */
private const val BOM = "﻿"

/**
 * One CSV cell. Quotes when needed, and neutralises spreadsheet formulas: a name such as
 * "=HYPERLINK(...)" typed into the public form must open as text, not run.
 */
internal fun csvCell(value: String): String {
    val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}

internal fun csv(header: List<String>, rows: List<List<String>>): String =
    BOM + (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { csvCell(it) } } + "\r\n"

private fun minutes(seconds: Long): String = (seconds / 60).toString()
private fun percent(rate: Double?): String = rate?.let { ((it * 1000).toLong() / 10.0).toString() } ?: ""
private fun whole(value: Double?): String = value?.let { (it / 60).toLong().toString() } ?: ""

/** One row per registered participant, with their attendance outcome. Times are in the event's zone. */
fun attendanceCsv(analytics: EventAnalytics): String {
    val zone = analytics.event.timezone
    return csv(
        listOf("Name", "Email", "Phone", "University", "Faculty", "Department", "Student ID", "Registered at", "First check-in", "Last check-out", "Sessions", "Minutes attended", "Outcome"),
        analytics.participants.map { p ->
            val r = p.registration
            listOf(
                r.participantName, r.email, r.phone, r.university, r.faculty, r.department, r.studentId.orEmpty(),
                r.registeredAt.isoLocal(zone), p.firstCheckInAt?.isoLocal(zone).orEmpty(), p.lastCheckOutAt?.isoLocal(zone).orEmpty(),
                p.sessionCount.toString(), minutes(p.completedSeconds), p.outcome.name,
            )
        },
    )
}

fun participantsCsv(event: ClubEvent, registrations: List<Registration>): String = csv(
    listOf("Name", "Email", "Phone", "University", "Faculty", "Department", "Student ID", "Status", "Registered at", "Delivery", "WhatsApp consent", "Attendance"),
    registrations.sortedBy { it.participantName.lowercase() }.map { r ->
        listOf(
            r.participantName, r.email, r.phone, r.university, r.faculty, r.department, r.studentId.orEmpty(), r.status.name,
            r.registeredAt.isoLocal(event.timezone), r.deliveryPreference.name, if (r.whatsappConsent) "yes" else "no", r.attendanceStatus.name,
        )
    },
)

fun sessionsCsv(event: ClubEvent, sessions: List<AttendanceSession>): String = csv(
    listOf("Participant", "Check-in", "Check-out", "Minutes", "Status", "Corrected", "Method", "Checked in by", "Checked out by"),
    sessions.sortedBy { it.checkInAt }.map { s ->
        listOf(
            s.participantName, s.checkInAt.isoLocal(event.timezone), s.checkOutAt?.isoLocal(event.timezone).orEmpty(),
            s.durationSeconds?.let(::minutes).orEmpty(), s.status.name, if (s.corrected) "yes" else "no",
            if (s.manual) "MANUAL" else "QR", s.checkedInByName, s.checkedOutByName.orEmpty(),
        )
    },
)

fun eventAnalyticsCsv(a: EventAnalytics): String = csv(
    listOf("Metric", "Value"),
    listOf(
        listOf("Event", a.event.title),
        listOf("Start", a.event.startAt.isoLocal(a.event.timezone)),
        listOf("End", a.event.endAt.isoLocal(a.event.timezone)),
        listOf("Time zone", a.event.timezone),
        listOf("Valid registrations", a.validRegistrations.toString()),
        listOf("Unique attendees", a.uniqueAttendees.toString()),
        listOf("Attendance rate (%)", percent(a.attendanceRate)),
        listOf("Completed sessions", a.completedSessions.toString()),
        listOf("Incomplete sessions", a.incompleteSessions.toString()),
        listOf("Average minutes per completed session", whole(a.averageSessionSeconds)),
        listOf("Median minutes per completed session", whole(a.medianSessionSeconds)),
        listOf("Average minutes per participant", whole(a.averageParticipantSeconds)),
        listOf("Median minutes per participant", whole(a.medianParticipantSeconds)),
        listOf("Grace period (minutes)", a.graceMinutes.toString()),
        listOf("Full attendance", a.fullAttendance.toString()),
        listOf("Early departures", a.earlyDepartures.toString()),
        listOf("Partial attendance", a.partialAttendance.toString()),
        listOf("Missing check-out", a.missingCheckout.toString()),
        listOf("Absent", a.absent.toString()),
    ),
)

fun eventComparisonCsv(club: ClubAnalytics): String = csv(
    listOf("Event", "Start", "Status", "Registrations", "Attendees", "Attendance rate (%)", "Completed sessions", "Open sessions", "Average minutes per session"),
    club.comparisons.map { c ->
        val e = c.event
        listOf(
            e.title, e.startAt.isoLocal(e.timezone), e.status.name, e.registrationCount.toString(), e.attendeeCount.toString(),
            percent(c.attendanceRate), e.completedSessionCount.toString(), e.activeSessionCount.toString(), whole(c.averageSessionSeconds),
        )
    },
)
