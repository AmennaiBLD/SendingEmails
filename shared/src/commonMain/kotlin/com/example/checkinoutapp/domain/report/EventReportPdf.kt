package com.example.checkinoutapp.domain.report

import com.example.checkinoutapp.domain.analytics.EventAnalytics
import com.example.checkinoutapp.domain.attendance.durationParts
import com.example.checkinoutapp.domain.time.isoLocal

private const val LEFT = 48.0
private const val RIGHT = SimplePdf.PAGE_WIDTH - 48.0
private const val BOTTOM = SimplePdf.PAGE_HEIGHT - 56.0

private fun duration(seconds: Double?): String = seconds?.let { durationParts(it.toLong()).let { p -> "${p.hours}h ${p.minutes.toString().padStart(2, '0')}m" } } ?: "n/a"
private fun percent(rate: Double?): String = rate?.let { "${kotlin.math.round(it * 100).toInt()}%" } ?: "n/a"
private fun clip(text: String, max: Int): String = if (text.length <= max) text else text.take(max - 1) + "."

/** The event attendance report: headline metrics, outcomes, then one line per participant. */
fun eventReportPdf(a: EventAnalytics, clubName: String, generatedAt: Long): ByteArray {
    val pdf = SimplePdf()
    val zone = a.event.timezone
    var y = 60.0

    pdf.text(LEFT, y, clubName, size = 10.0, bold = true)
    pdf.text(RIGHT - 190, y, "Generated ${generatedAt.isoLocal(zone)} ($zone)", size = 8.0)
    y += 28
    pdf.text(LEFT, y, clip(a.event.title, 60), size = 18.0, bold = true)
    y += 18
    pdf.text(LEFT, y, "Attendance report  |  ${a.event.startAt.isoLocal(zone)} to ${a.event.endAt.isoLocal(zone)}  |  ${clip(a.event.location, 40)}", size = 9.0)
    y += 12
    pdf.rule(LEFT, y, RIGHT)
    y += 22

    val metrics = listOf(
        "Valid registrations" to a.validRegistrations.toString(),
        "Unique attendees" to a.uniqueAttendees.toString(),
        "Attendance rate" to percent(a.attendanceRate),
        "Completed check-outs" to a.completedSessions.toString(),
        "Incomplete sessions" to a.incompleteSessions.toString(),
        "Average time per session" to duration(a.averageSessionSeconds),
        "Median time per session" to duration(a.medianSessionSeconds),
        "Average time per participant" to duration(a.averageParticipantSeconds),
        "Full attendance (grace ${a.graceMinutes} min)" to a.fullAttendance.toString(),
        "Early departures" to a.earlyDepartures.toString(),
        "Missing check-out" to a.missingCheckout.toString(),
        "Absent" to a.absent.toString(),
    )
    metrics.chunked(2).forEach { pair ->
        pair.forEachIndexed { column, (label, value) ->
            val x = LEFT + column * 255
            pdf.text(x, y, label, size = 9.0)
            pdf.text(x + 170, y, value, size = 10.0, bold = true)
        }
        y += 17
    }
    y += 6
    pdf.text(LEFT, y, "Incomplete sessions have no departure time and are excluded from every average and median.", size = 8.0)
    y += 22

    val columns = listOf(LEFT to "Participant", LEFT + 190 to "First check-in", LEFT + 285 to "Last check-out", LEFT + 380 to "Time", LEFT + 430 to "Outcome")
    fun header() {
        columns.forEach { (x, title) -> pdf.text(x, y, title, size = 8.5, bold = true) }
        y += 5
        pdf.rule(LEFT, y, RIGHT)
        y += 13
    }
    header()
    if (a.participants.isEmpty()) pdf.text(LEFT, y, "No registrations.", size = 9.0)
    a.participants.forEach { p ->
        if (y > BOTTOM) {
            pdf.newPage()
            y = 60.0
            header()
        }
        pdf.text(columns[0].first, y, clip(p.registration.participantName, 38), size = 9.0)
        pdf.text(columns[1].first, y, p.firstCheckInAt?.isoLocal(zone) ?: "-", size = 9.0)
        pdf.text(columns[2].first, y, p.lastCheckOutAt?.isoLocal(zone) ?: "-", size = 9.0)
        pdf.text(columns[3].first, y, if (p.sessionCount == 0) "-" else duration(p.completedSeconds.toDouble()), size = 9.0)
        pdf.text(columns[4].first, y, p.outcome.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, size = 9.0)
        y += 14
    }
    return pdf.build()
}
