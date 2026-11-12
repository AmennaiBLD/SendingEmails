package com.example.checkinoutapp.domain.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Loads the browser's IANA time-zone database. A no-op everywhere else. */
expect fun ensureTimeZoneDatabase()

/** The zone for an IANA id; UTC when the id is unknown on this device. */
fun zoneOf(id: String): TimeZone = try {
    ensureTimeZoneDatabase()
    TimeZone.of(id)
} catch (_: Exception) {
    TimeZone.UTC
}

fun isKnownTimeZone(id: String): Boolean = try {
    ensureTimeZoneDatabase()
    TimeZone.of(id)
    id.isNotBlank()
} catch (_: Exception) {
    false
}

fun Long.toLocal(zone: TimeZone): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)

fun Long.toLocal(zoneId: String): LocalDateTime = toLocal(zoneOf(zoneId))

/** "2026-11-14" + "09:30" in [zoneId] → epoch millis, or null when either part is not valid. */
fun parseLocalDateTime(date: String, time: String, zoneId: String): Long? = try {
    LocalDateTime(LocalDate.parse(date.trim()), LocalTime.parse(time.trim())).toInstant(zoneOf(zoneId)).toEpochMilliseconds()
} catch (_: Exception) {
    null
}

private fun two(value: Int): String = value.toString().padStart(2, '0')

fun LocalDateTime.isoDate(): String = "$year-${two(month.ordinal + 1)}-${two(day)}"

fun LocalDateTime.isoTime(): String = "${two(hour)}:${two(minute)}"

/** "2026-11-14 09:30" — the unambiguous form used in exports. */
fun Long.isoLocal(zoneId: String): String = toLocal(zoneId).let { "${it.isoDate()} ${it.isoTime()}" }

val COMMON_TIME_ZONES = listOf(
    "Africa/Algiers", "Africa/Tunis", "Africa/Casablanca", "Africa/Cairo", "Europe/Paris", "Europe/London",
    "Europe/Istanbul", "Asia/Riyadh", "Asia/Dubai", "America/New_York", "UTC",
)
