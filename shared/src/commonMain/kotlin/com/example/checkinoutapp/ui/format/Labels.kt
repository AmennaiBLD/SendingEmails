package com.example.checkinoutapp.ui.format

import androidx.compose.runtime.Composable
import com.example.checkinoutapp.domain.analytics.AttendanceOutcome
import com.example.checkinoutapp.domain.event.RegistrationAvailability
import com.example.checkinoutapp.domain.model.AttendanceStatus
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.DeliveryPreference
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.RegistrationStatus
import com.example.checkinoutapp.domain.model.SessionStatus
import com.example.checkinoutapp.domain.model.UserRole
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.Tone
import org.jetbrains.compose.resources.stringResource

// The ViewModels emit meaning (enums); these turn it into words and tone.

@Composable
fun EventStatus.label(): String = stringResource(
    when (this) {
        EventStatus.DRAFT -> Res.string.status_draft
        EventStatus.PUBLISHED -> Res.string.status_published
        EventStatus.CANCELLED -> Res.string.status_cancelled
        EventStatus.COMPLETED -> Res.string.status_completed
        EventStatus.ARCHIVED -> Res.string.status_archived
    },
)

fun EventStatus.tone(): Tone = when (this) {
    EventStatus.DRAFT -> Tone.Warning
    EventStatus.PUBLISHED -> Tone.Success
    EventStatus.CANCELLED -> Tone.Danger
    EventStatus.COMPLETED -> Tone.Info
    EventStatus.ARCHIVED -> Tone.Neutral
}

@Composable
fun RegistrationAvailability.label(event: ClubEvent): String = when (this) {
    RegistrationAvailability.OPEN -> event.seatsLeft?.let { stringResource(Res.string.avail_seats_left, it) } ?: stringResource(Res.string.avail_open)
    RegistrationAvailability.NOT_YET_OPEN -> stringResource(Res.string.avail_not_yet_open)
    RegistrationAvailability.CLOSED -> stringResource(Res.string.avail_closed)
    RegistrationAvailability.FULL -> stringResource(Res.string.avail_full)
    RegistrationAvailability.EVENT_ENDED -> stringResource(Res.string.avail_ended)
    RegistrationAvailability.NOT_PUBLISHED -> stringResource(Res.string.avail_not_published)
}

fun RegistrationAvailability.tone(): Tone = when (this) {
    RegistrationAvailability.OPEN -> Tone.Success
    RegistrationAvailability.NOT_YET_OPEN -> Tone.Info
    RegistrationAvailability.FULL -> Tone.Warning
    else -> Tone.Neutral
}

@Composable
fun RegistrationStatus.label(): String = stringResource(
    when (this) {
        RegistrationStatus.PENDING -> Res.string.reg_status_pending
        RegistrationStatus.REGISTERED -> Res.string.reg_status_registered
        RegistrationStatus.REJECTED -> Res.string.reg_status_rejected
        RegistrationStatus.WAITLISTED -> Res.string.reg_status_waitlisted
        RegistrationStatus.CANCELLED -> Res.string.reg_status_cancelled
    },
)

fun RegistrationStatus.tone(): Tone = when (this) {
    RegistrationStatus.PENDING -> Tone.Warning
    RegistrationStatus.REGISTERED -> Tone.Success
    RegistrationStatus.REJECTED -> Tone.Danger
    RegistrationStatus.WAITLISTED -> Tone.Warning
    RegistrationStatus.CANCELLED -> Tone.Danger
}

@Composable
fun AttendanceStatus.label(): String = stringResource(
    when (this) {
        AttendanceStatus.NONE -> Res.string.att_none
        AttendanceStatus.ACTIVE -> Res.string.att_active
        AttendanceStatus.COMPLETED -> Res.string.att_completed
    },
)

fun AttendanceStatus.tone(): Tone = when (this) {
    AttendanceStatus.NONE -> Tone.Neutral
    AttendanceStatus.ACTIVE -> Tone.Success
    AttendanceStatus.COMPLETED -> Tone.Info
}

@Composable
fun SessionStatus.label(): String = stringResource(
    when (this) {
        SessionStatus.ACTIVE -> Res.string.session_active
        SessionStatus.COMPLETED -> Res.string.session_completed
        SessionStatus.CORRECTED -> Res.string.session_corrected
    },
)

fun SessionStatus.tone(): Tone = when (this) {
    SessionStatus.ACTIVE -> Tone.Success
    SessionStatus.COMPLETED -> Tone.Info
    SessionStatus.CORRECTED -> Tone.Warning
}

@Composable
fun AttendanceOutcome.label(): String = stringResource(
    when (this) {
        AttendanceOutcome.ABSENT -> Res.string.outcome_absent
        AttendanceOutcome.MISSING_CHECKOUT -> Res.string.outcome_missing_checkout
        AttendanceOutcome.EARLY_DEPARTURE -> Res.string.outcome_early
        AttendanceOutcome.PARTIAL -> Res.string.outcome_partial
        AttendanceOutcome.FULL -> Res.string.outcome_full
    },
)

fun AttendanceOutcome.tone(): Tone = when (this) {
    AttendanceOutcome.ABSENT -> Tone.Neutral
    AttendanceOutcome.MISSING_CHECKOUT -> Tone.Warning
    AttendanceOutcome.EARLY_DEPARTURE -> Tone.Danger
    AttendanceOutcome.PARTIAL -> Tone.Info
    AttendanceOutcome.FULL -> Tone.Success
}

@Composable
fun UserRole.label(): String = stringResource(
    when (this) {
        UserRole.ADMIN -> Res.string.role_admin
        UserRole.ORGANIZER -> Res.string.role_organizer
        UserRole.PARTICIPANT -> Res.string.role_participant
    },
)

@Composable
fun DeliveryPreference.label(): String = stringResource(
    when (this) {
        DeliveryPreference.EMAIL_ONLY -> Res.string.delivery_email_only
        DeliveryPreference.WHATSAPP_ONLY -> Res.string.delivery_whatsapp_only
        DeliveryPreference.BOTH -> Res.string.delivery_both
    },
)
