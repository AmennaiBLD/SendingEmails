package com.example.checkinoutapp.domain.analytics

import com.example.checkinoutapp.domain.attendance.average
import com.example.checkinoutapp.domain.attendance.median
import com.example.checkinoutapp.domain.model.AttendanceSession
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.model.RegistrationStatus

/** How one registered participant's attendance ended. */
enum class AttendanceOutcome {
    /** Never checked in. */
    ABSENT,
    /** Checked in and still not checked out: no departure time is invented for them. */
    MISSING_CHECKOUT,
    /** Checked out before the scheduled end, beyond the grace period. */
    EARLY_DEPARTURE,
    /** Checked out normally, but was present for less than the scheduled duration (e.g. arrived late). */
    PARTIAL,
    /** Present for the scheduled duration, within the grace period. */
    FULL,
}

data class ParticipantAttendance(
    val registration: Registration,
    val sessionCount: Int,
    val completedSeconds: Long,
    val firstCheckInAt: Long?,
    val lastCheckOutAt: Long?,
    val outcome: AttendanceOutcome,
)

/** A histogram bar: [start] is the lower bound of the bin (an instant, or seconds for durations). */
data class Bucket(val start: Long, val count: Int)

data class LabelCount(val label: String, val count: Int)

data class EventAnalytics(
    val event: ClubEvent,
    val graceMinutes: Int,
    val validRegistrations: Int,
    val uniqueAttendees: Int,
    /** Unique participants who checked in ÷ valid registrations, 0..1. Null without registrations. */
    val attendanceRate: Double?,
    val completedSessions: Int,
    val incompleteSessions: Int,
    val averageSessionSeconds: Double?,
    val medianSessionSeconds: Double?,
    /** Per participant: the sum of their completed sessions, averaged over participants who completed one. */
    val averageParticipantSeconds: Double?,
    val medianParticipantSeconds: Double?,
    val fullAttendance: Int,
    val earlyDepartures: Int,
    val partialAttendance: Int,
    val missingCheckout: Int,
    val absent: Int,
    val arrivals: List<Bucket>,
    val departures: List<Bucket>,
    val durations: List<Bucket>,
    val registrationsByDay: List<Bucket>,
    val byFaculty: List<LabelCount>,
    val byDepartment: List<LabelCount>,
    val participants: List<ParticipantAttendance>,
) {
    val hasAttendance: Boolean get() = uniqueAttendees > 0
    /** Checked out and did not leave early. */
    val completedNormally: Int get() = fullAttendance + partialAttendance
}

const val ARRIVAL_BIN_MILLIS = 15 * 60 * 1000L
const val DURATION_BIN_SECONDS = 30 * 60L
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Fills the gaps between the first and last bin so a chart shows empty intervals as empty. */
internal fun histogram(values: List<Long>, binSize: Long, maxBins: Int = 96): List<Bucket> {
    if (values.isEmpty()) return emptyList()
    val counts = values.groupingBy { Math.floorDiv(it, binSize) * binSize }.eachCount()
    val first = counts.keys.min()
    val last = counts.keys.max()
    if ((last - first) / binSize + 1 > maxBins) return counts.entries.sortedBy { it.key }.map { Bucket(it.key, it.value) }
    return generateSequence(first) { it + binSize }.takeWhile { it <= last }.map { Bucket(it, counts[it] ?: 0) }.toList()
}

private object Math {
    fun floorDiv(a: Long, b: Long): Long = if (a >= 0) a / b else -((-a + b - 1) / b)
}

private fun labelCounts(labels: List<String>): List<LabelCount> =
    labels.map { it.trim().ifBlank { "—" } }.groupingBy { it }.eachCount().map { LabelCount(it.key, it.value) }
        .sortedWith(compareByDescending<LabelCount> { it.count }.thenBy { it.label })

/**
 * Everything the analytics screens and reports show for one event, computed from the event's
 * registrations and attendance sessions. Pure: same inputs, same numbers, on every platform.
 *
 * Rules that matter:
 *  - Only REGISTERED registrations count; cancelled ones are ignored together with their sessions.
 *  - An open session has no duration. It is counted as incomplete and kept out of every average.
 *  - "Early" and "full" both allow [graceMinutes] of tolerance.
 */
fun computeEventAnalytics(
    event: ClubEvent,
    registrations: List<Registration>,
    sessions: List<AttendanceSession>,
    graceMinutes: Int,
): EventAnalytics {
    val valid = registrations.filter { it.status == RegistrationStatus.REGISTERED }
    val validIds = valid.map { it.id }.toSet()
    val counted = sessions.filter { it.registrationId in validIds }
    val byRegistration = counted.groupBy { it.registrationId }
    val graceSeconds = graceMinutes.coerceAtLeast(0) * 60L
    val graceMillis = graceSeconds * 1000

    val participants = valid.map { registration ->
        val own = byRegistration[registration.id].orEmpty()
        val closed = own.filter { it.status.isClosed && it.checkOutAt != null }
        val completedSeconds = closed.sumOf { it.durationSeconds ?: 0 }
        val lastOut = closed.maxOfOrNull { it.checkOutAt!! }
        val outcome = when {
            own.isEmpty() -> AttendanceOutcome.ABSENT
            own.any { !it.status.isClosed } -> AttendanceOutcome.MISSING_CHECKOUT
            completedSeconds >= event.scheduledDurationSeconds - graceSeconds -> AttendanceOutcome.FULL
            lastOut != null && lastOut < event.endAt - graceMillis -> AttendanceOutcome.EARLY_DEPARTURE
            else -> AttendanceOutcome.PARTIAL
        }
        ParticipantAttendance(
            registration = registration,
            sessionCount = own.size,
            completedSeconds = completedSeconds,
            firstCheckInAt = own.minOfOrNull { it.checkInAt },
            lastCheckOutAt = lastOut,
            outcome = outcome,
        )
    }.sortedWith(compareBy<ParticipantAttendance> { it.firstCheckInAt ?: Long.MAX_VALUE }.thenBy { it.registration.participantName.lowercase() })

    val attended = participants.filter { it.sessionCount > 0 }
    val completed = counted.filter { it.status.isClosed && it.durationSeconds != null }
    val sessionDurations = completed.map { it.durationSeconds!! }
    val participantTotals = attended.filter { it.completedSeconds > 0 || it.lastCheckOutAt != null }.map { it.completedSeconds }
    fun count(outcome: AttendanceOutcome) = participants.count { it.outcome == outcome }

    return EventAnalytics(
        event = event,
        graceMinutes = graceMinutes,
        validRegistrations = valid.size,
        uniqueAttendees = attended.size,
        attendanceRate = if (valid.isEmpty()) null else attended.size.toDouble() / valid.size,
        completedSessions = completed.size,
        incompleteSessions = counted.count { !it.status.isClosed },
        averageSessionSeconds = average(sessionDurations),
        medianSessionSeconds = median(sessionDurations),
        averageParticipantSeconds = average(participantTotals),
        medianParticipantSeconds = median(participantTotals),
        fullAttendance = count(AttendanceOutcome.FULL),
        earlyDepartures = count(AttendanceOutcome.EARLY_DEPARTURE),
        partialAttendance = count(AttendanceOutcome.PARTIAL),
        missingCheckout = count(AttendanceOutcome.MISSING_CHECKOUT),
        absent = count(AttendanceOutcome.ABSENT),
        arrivals = histogram(counted.map { it.checkInAt }, ARRIVAL_BIN_MILLIS),
        departures = histogram(completed.mapNotNull { it.checkOutAt }, ARRIVAL_BIN_MILLIS),
        durations = histogram(sessionDurations, DURATION_BIN_SECONDS),
        registrationsByDay = histogram(valid.map { it.registeredAt }, DAY_MILLIS, maxBins = 60),
        byFaculty = labelCounts(attended.map { it.registration.faculty }),
        byDepartment = labelCounts(attended.map { it.registration.department }),
        participants = participants,
    )
}
