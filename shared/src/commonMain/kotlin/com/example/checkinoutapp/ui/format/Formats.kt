package com.example.checkinoutapp.ui.format

import com.example.checkinoutapp.domain.attendance.durationParts
import com.example.checkinoutapp.domain.time.toLocal
import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToInt

// Formatting lives in the UI layer: ViewModels and the domain carry instants and numbers.

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun two(value: Int): String = value.toString().padStart(2, '0')

private fun LocalDateTime.clock(): String {
    val hour12 = ((hour + 11) % 12) + 1
    return "${two(hour12)}:${two(minute)} ${if (hour < 12) "AM" else "PM"}"
}

/** "Sat, 14 Nov 2026" in the event's own time zone. */
fun formatDate(millis: Long, zoneId: String): String =
    millis.toLocal(zoneId).let { "${DAYS[it.dayOfWeek.ordinal]}, ${it.day} ${MONTHS[it.month.ordinal]} ${it.year}" }

/** "09:15 AM" in the event's own time zone. */
fun formatTime(millis: Long, zoneId: String): String = millis.toLocal(zoneId).clock()

fun formatDateTime(millis: Long, zoneId: String): String =
    millis.toLocal(zoneId).let { "${it.day} ${MONTHS[it.month.ordinal]} ${it.year}, ${it.clock()}" }

fun formatTimeRange(startAt: Long, endAt: Long, zoneId: String): String = "${formatTime(startAt, zoneId)} – ${formatTime(endAt, zoneId)}"

fun formatDayMonth(millis: Long, zoneId: String): Pair<String, String> = millis.toLocal(zoneId).let { it.day.toString() to MONTHS[it.month.ordinal].uppercase() }

fun monthName(month: Int): String = MONTHS.getOrElse(month - 1) { "?" }

/** "3h 30m", "45m", and "40s" under a minute, so a very short stay does not read as nothing. */
fun formatDuration(seconds: Long): String {
    if (seconds in 1..59) return "${seconds}s"
    val parts = durationParts(seconds)
    return if (parts.hours > 0) "${parts.hours}h ${two(parts.minutes.toInt())}m" else "${parts.minutes}m"
}

fun formatDuration(seconds: Double?): String? = seconds?.let { formatDuration(it.toLong()) }

fun formatPercent(rate: Double?): String? = rate?.let { "${(it * 100).roundToInt()}%" }
