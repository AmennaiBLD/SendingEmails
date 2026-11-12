package com.example.checkinoutapp.domain.attendance

import com.example.checkinoutapp.domain.model.AttendanceSession

data class DurationParts(val hours: Long, val minutes: Long)

fun durationParts(seconds: Long): DurationParts {
    val safe = seconds.coerceAtLeast(0)
    return DurationParts(hours = safe / 3600, minutes = (safe % 3600) / 60)
}

/** Departure minus arrival, in whole seconds; never negative. */
fun durationSeconds(checkInAt: Long, checkOutAt: Long): Long = ((checkOutAt - checkInAt) / 1000).coerceAtLeast(0)

/** Total attendance = the sum of completed sessions. An open session contributes nothing. */
fun totalCompletedSeconds(sessions: List<AttendanceSession>): Long =
    sessions.filter { it.status.isClosed }.sumOf { it.durationSeconds ?: 0 }

fun median(values: List<Long>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
}

fun average(values: List<Long>): Double? = if (values.isEmpty()) null else values.sum().toDouble() / values.size
