package com.example.checkinoutapp.domain

import com.example.checkinoutapp.domain.analytics.AttendanceOutcome
import com.example.checkinoutapp.domain.analytics.computeClubAnalytics
import com.example.checkinoutapp.domain.analytics.computeEventAnalytics
import com.example.checkinoutapp.domain.attendance.average
import com.example.checkinoutapp.domain.attendance.durationParts
import com.example.checkinoutapp.domain.attendance.durationSeconds
import com.example.checkinoutapp.domain.attendance.median
import com.example.checkinoutapp.domain.attendance.totalCompletedSeconds
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.RegistrationStatus
import com.example.checkinoutapp.domain.report.attendanceCsv
import com.example.checkinoutapp.domain.report.csvCell
import com.example.checkinoutapp.domain.report.eventAnalyticsCsv
import com.example.checkinoutapp.domain.report.eventReportPdf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsTest {

    @Test
    fun durationIsDepartureMinusArrival() {
        assertEquals(12_600, durationSeconds(START + 15 * MINUTE, START + 3 * HOUR + 45 * MINUTE))
        assertEquals(0, durationSeconds(START, START - HOUR), "a duration is never negative")
        assertEquals(3L to 30L, durationParts(12_600).let { it.hours to it.minutes })
    }

    @Test
    fun multipleSessionsAddUpAndAnOpenSessionAddsNothing() {
        val sessions = listOf(
            session("a", START, START + 2 * HOUR),
            session("a", START + 3 * HOUR, START + 5 * HOUR),
            session("a", START + 6 * HOUR, null),
        )
        assertEquals(4 * 3600L, totalCompletedSeconds(sessions))
    }

    @Test
    fun medianAndAverageHandleOddEvenAndEmpty() {
        assertEquals(20.0, median(listOf(30, 10, 20)))
        assertEquals(25.0, median(listOf(40, 10, 20, 30)))
        assertNull(median(emptyList()))
        assertNull(average(emptyList()))
        assertEquals(25.0, average(listOf(10, 20, 30, 40)))
    }

    /**
     * a: 09:00–12:00 full · b: 09:00–10:00 left early · c: 10:00–12:00 arrived late · d: still inside
     * e: never came · f: cancelled (with a session that must be ignored) · g: 09:05–11:55 inside the grace period
     */
    private fun analytics(grace: Int = 10) = computeEventAnalytics(
        event(),
        listOf("a", "b", "c", "d", "e", "g").map { registration(it) } + registration("f", status = RegistrationStatus.CANCELLED),
        listOf(
            session("a", START, END),
            session("b", START, START + HOUR),
            session("c", START + HOUR, END),
            session("d", START + 10 * MINUTE, null),
            session("f", START, END),
            session("g", START + 5 * MINUTE, END - 5 * MINUTE),
        ),
        grace,
    )

    @Test
    fun attendanceRateCountsUniqueParticipantsAgainstValidRegistrations() {
        val a = analytics()
        assertEquals(6, a.validRegistrations, "the cancelled registration is not counted")
        assertEquals(5, a.uniqueAttendees)
        assertEquals(5.0 / 6.0, a.attendanceRate)
        assertNull(computeEventAnalytics(event(), emptyList(), emptyList(), 10).attendanceRate, "no registrations: no rate, not 0%")
    }

    @Test
    fun incompleteSessionsAreShownSeparatelyAndKeptOutOfAverages() {
        val a = analytics()
        assertEquals(4, a.completedSessions)
        assertEquals(1, a.incompleteSessions)
        assertEquals(1, a.missingCheckout)
        // 3h, 1h, 2h, 2h50m → mean 2h12m30s, median 2h25m. The open session changes neither.
        assertEquals((10_800 + 3_600 + 7_200 + 10_200) / 4.0, a.averageSessionSeconds)
        assertEquals((7_200 + 10_200) / 2.0, a.medianSessionSeconds)
        assertEquals(a.averageSessionSeconds, a.averageParticipantSeconds, "one session each: the two averages agree")
    }

    @Test
    fun outcomesSeparateFullEarlyLatePartialMissingAndAbsent() {
        val outcome = analytics().participants.associate { it.registration.id to it.outcome }
        assertEquals(AttendanceOutcome.FULL, outcome["a"])
        assertEquals(AttendanceOutcome.EARLY_DEPARTURE, outcome["b"])
        assertEquals(AttendanceOutcome.PARTIAL, outcome["c"], "left at the end but arrived an hour late")
        assertEquals(AttendanceOutcome.MISSING_CHECKOUT, outcome["d"])
        assertEquals(AttendanceOutcome.ABSENT, outcome["e"])
        assertEquals(AttendanceOutcome.FULL, outcome["g"], "ten minutes short in total is inside the 10-minute grace")
        val a = analytics()
        assertEquals(listOf(2, 1, 1, 1, 1), listOf(a.fullAttendance, a.earlyDepartures, a.partialAttendance, a.missingCheckout, a.absent))
        assertEquals(3, a.completedNormally)
    }

    @Test
    fun theGracePeriodIsConfigurable() {
        val strict = analytics(grace = 0).participants.associate { it.registration.id to it.outcome }
        assertEquals(AttendanceOutcome.EARLY_DEPARTURE, strict["g"], "without tolerance, leaving 5 minutes early is early")
        assertEquals(AttendanceOutcome.FULL, strict["a"])
    }

    @Test
    fun reentrySessionsBelongToOneParticipant() {
        val a = computeEventAnalytics(
            event(), listOf(registration("a")),
            listOf(session("a", START, START + HOUR), session("a", START + 90 * MINUTE, END)), 10,
        )
        assertEquals(1, a.uniqueAttendees)
        assertEquals(2, a.completedSessions)
        assertEquals(4_500.0, a.averageSessionSeconds, "per session: (60 + 90) / 2 minutes")
        assertEquals(9_000.0, a.averageParticipantSeconds, "per participant: 150 minutes")
        assertEquals(AttendanceOutcome.PARTIAL, a.participants.single().outcome)
    }

    @Test
    fun aCorrectedSessionCountsAsCompleted() {
        val a = computeEventAnalytics(event(), listOf(registration("a")), listOf(session("a", START, END, corrected = true)), 10)
        assertEquals(1, a.completedSessions)
        assertEquals(0, a.incompleteSessions)
        assertEquals(AttendanceOutcome.FULL, a.participants.single().outcome)
    }

    @Test
    fun distributionsBucketArrivalsAndDurations() {
        val a = analytics()
        assertEquals(5, a.arrivals.sumOf { it.count })
        assertEquals(4, a.arrivals.first().count, "a, b, d and g arrived in the first quarter of an hour")
        assertEquals(4, a.durations.sumOf { it.count })
        assertEquals(listOf("Science" to 5), a.byFaculty.map { it.label to it.count })
    }

    @Test
    fun anEventWithoutAttendanceHasNoInventedNumbers() {
        val a = computeEventAnalytics(event(), listOf(registration("a")), emptyList(), 10)
        assertTrue(!a.hasAttendance)
        assertEquals(0.0, a.attendanceRate)
        assertNull(a.averageSessionSeconds)
        assertNull(a.medianSessionSeconds)
        assertTrue(a.arrivals.isEmpty() && a.durations.isEmpty())
    }

    @Test
    fun clubAnalyticsComparesEventsFromTheirCounters() {
        val club = computeClubAnalytics(
            listOf(
                event("big", registrationCount = 100, attendeeCount = 80, completedSessionCount = 70, completedDurationSeconds = 70 * 3600L, activeSessionCount = 10),
                event("small", registrationCount = 10, attendeeCount = 9, completedSessionCount = 9, completedDurationSeconds = 9 * 7200L, status = EventStatus.COMPLETED),
                event("draft", status = EventStatus.DRAFT, registrationCount = 999),
                event("empty"),
            ),
            listOf(session("x", START, START + HOUR), session("y", START, START + 3 * HOUR), session("z", START, null)),
        )
        assertEquals(3, club.totalEvents, "drafts are not activity")
        assertEquals(110, club.totalRegistrations)
        assertEquals(89, club.totalAttendees)
        assertEquals(89.0 / 110.0, club.attendanceRate)
        assertEquals(79, club.completedCheckouts)
        assertEquals(10, club.incompleteSessions)
        assertEquals((70 * 3600.0 + 9 * 7200.0) / 79, club.averageSessionSeconds)
        assertEquals(7200.0, club.medianSessionSeconds)
        assertEquals(listOf("big", "small"), club.mostAttended.map { it.event.id })
        assertEquals(listOf("small", "big"), club.longestStays.map { it.event.id })
        assertNull(club.comparisons.first { it.event.id == "empty" }.attendanceRate)
    }

    @Test
    fun csvCellsAreEscapedAndFormulasAreNeutralised() {
        assertEquals("plain", csvCell("plain"))
        assertEquals("\"Benali, Ahmed\"", csvCell("Benali, Ahmed"))
        assertEquals("\"He said \"\"hi\"\"\"", csvCell("He said \"hi\""))
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", csvCell("=HYPERLINK(\"http://x\")"))
        assertEquals("'+213555", csvCell("+213555"))
    }

    @Test
    fun theAttendanceExportHasOneRowPerValidRegistration() {
        val csv = attendanceCsv(analytics())
        val lines = csv.trim().lines()
        assertTrue(csv.startsWith("\uFEFF"), "Excel needs the byte-order mark to read UTF-8")
        assertEquals(1 + 6, lines.size)
        assertTrue(lines[0].contains("Name,Email,Phone"))
        assertTrue(lines.any { it.contains("P a") && it.contains("2026-11-14 09:00") && it.contains("2026-11-14 12:00") && it.endsWith("180,FULL") })
        assertTrue(lines.any { it.contains("P e") && it.endsWith("0,0,ABSENT") })
        assertTrue(eventAnalyticsCsv(analytics()).contains("Incomplete sessions,1"))
    }

    @Test
    fun thePdfReportIsAWellFormedDocument() {
        val bytes = eventReportPdf(analytics(), "Scientific Club", START)
        val text = bytes.decodeToString()
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(text.contains("AI & Robotics Workshop"))
        // The xref offset recorded in the trailer points at the xref table.
        val xref = text.substringAfter("startxref\n").substringBefore("\n").toInt()
        assertEquals("xref", text.substring(xref, xref + 4))
        val many = computeEventAnalytics(event(), (1..120).map { registration("r$it") }, emptyList(), 10)
        assertTrue(eventReportPdf(many, "Club", START).decodeToString().contains("/Count 3"), "long lists continue on new pages")
    }
}
