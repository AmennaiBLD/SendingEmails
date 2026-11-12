package com.example.checkinoutapp.domain

import com.example.checkinoutapp.domain.event.EventField
import com.example.checkinoutapp.domain.event.EventQuery
import com.example.checkinoutapp.domain.event.EventTimeFilter
import com.example.checkinoutapp.domain.event.RegistrationAvailability
import com.example.checkinoutapp.domain.event.filterEvents
import com.example.checkinoutapp.domain.event.registrationAvailability
import com.example.checkinoutapp.domain.event.validateEventDraft
import com.example.checkinoutapp.domain.model.DeliveryPreference
import com.example.checkinoutapp.domain.model.EventDraft
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.RegistrationField
import com.example.checkinoutapp.domain.model.RegistrationForm
import com.example.checkinoutapp.domain.registration.isValidInternationalPhone
import com.example.checkinoutapp.domain.registration.normalizeEmail
import com.example.checkinoutapp.domain.registration.normalizePhone
import com.example.checkinoutapp.domain.registration.validateRegistration
import com.example.checkinoutapp.domain.time.isoLocal
import com.example.checkinoutapp.domain.time.parseLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventAndRegistrationTest {

    @Test
    fun registrationIsOpenOnlyInsideItsWindowWhilePublishedAndNotFull() {
        val open = START - 2 * HOUR
        assertEquals(RegistrationAvailability.OPEN, event().registrationAvailability(open))
        assertEquals(RegistrationAvailability.NOT_YET_OPEN, event().registrationAvailability(START - 30 * 24 * HOUR))
        assertEquals(RegistrationAvailability.CLOSED, event().registrationAvailability(START - 30 * MINUTE))
        assertEquals(RegistrationAvailability.EVENT_ENDED, event().registrationAvailability(END + 1))
        assertEquals(RegistrationAvailability.FULL, event(capacity = 3, registrationCount = 3).registrationAvailability(open))
        assertEquals(RegistrationAvailability.OPEN, event(capacity = 3, registrationCount = 2).registrationAvailability(open))
        assertEquals(RegistrationAvailability.NOT_PUBLISHED, event(status = EventStatus.DRAFT).registrationAvailability(open))
        assertEquals(RegistrationAvailability.NOT_PUBLISHED, event(status = EventStatus.CANCELLED).registrationAvailability(open))
        assertEquals(RegistrationAvailability.EVENT_ENDED, event(status = EventStatus.COMPLETED).registrationAvailability(open))
    }

    @Test
    fun unlimitedCapacityNeverFillsAndSeatsLeftNeverGoesNegative() {
        assertNull(event(capacity = 0, registrationCount = 500).seatsLeft)
        assertEquals(0, event(capacity = 3, registrationCount = 5).seatsLeft)
        assertEquals(RegistrationAvailability.OPEN, event(capacity = 0, registrationCount = 500).registrationAvailability(START - 2 * HOUR))
    }

    @Test
    fun eventsAreFilteredBySearchCategoryAndTime() {
        val now = START
        val events = listOf(
            event("past", startAt = START - 10 * 24 * HOUR, endAt = START - 10 * 24 * HOUR + HOUR, title = "Old talk", category = "Talk"),
            event("soon", startAt = START + HOUR, endAt = START + 2 * HOUR, title = "Robotics", category = "Workshop"),
            event("later", startAt = START + 48 * HOUR, endAt = START + 50 * HOUR, title = "Astronomy night", category = "Talk"),
            event("done", status = EventStatus.COMPLETED, startAt = START + 5 * HOUR, endAt = START + 6 * HOUR, title = "Closed early"),
        )
        assertEquals(listOf("soon", "later"), filterEvents(events, EventQuery(), now).map { it.id })
        assertEquals(listOf("done", "past"), filterEvents(events, EventQuery(time = EventTimeFilter.PAST), now).map { it.id })
        assertEquals(listOf("later"), filterEvents(events, EventQuery(category = "talk"), now).map { it.id })
        assertEquals(listOf("later"), filterEvents(events, EventQuery(search = "  ASTRO "), now).map { it.id })
        assertEquals(4, filterEvents(events, EventQuery(time = EventTimeFilter.ALL), now).size)
        assertTrue(filterEvents(events, EventQuery(search = "nothing matches"), now).isEmpty())
    }

    private val valid = RegistrationForm(
        fullName = "Ahmed Benali", email = "ahmed@example.org", phone = "+213 555 12 34 56", university = "USTHB",
        faculty = "Computer Science", department = "AI", privacyAccepted = true,
    )

    @Test
    fun aCompleteFormIsValidAndEachMissingPieceIsNamed() {
        assertTrue(validateRegistration(valid).isEmpty())
        assertEquals(setOf(RegistrationField.FULL_NAME), validateRegistration(valid.copy(fullName = " ")))
        assertEquals(setOf(RegistrationField.EMAIL), validateRegistration(valid.copy(email = "ahmed@example")))
        assertEquals(setOf(RegistrationField.PHONE), validateRegistration(valid.copy(phone = "0555123456")))
        assertEquals(setOf(RegistrationField.PRIVACY), validateRegistration(valid.copy(privacyAccepted = false)))
        assertEquals(
            setOf(RegistrationField.UNIVERSITY, RegistrationField.FACULTY, RegistrationField.DEPARTMENT),
            validateRegistration(valid.copy(university = "", faculty = "", department = "")),
        )
    }

    @Test
    fun whatsAppDeliveryRequiresExplicitConsent() {
        assertEquals(setOf(RegistrationField.WHATSAPP_CONSENT), validateRegistration(valid.copy(deliveryPreference = DeliveryPreference.BOTH)))
        assertEquals(setOf(RegistrationField.WHATSAPP_CONSENT), validateRegistration(valid.copy(deliveryPreference = DeliveryPreference.WHATSAPP_ONLY)))
        assertTrue(validateRegistration(valid.copy(deliveryPreference = DeliveryPreference.BOTH, whatsappConsent = true)).isEmpty())
        assertTrue(validateRegistration(valid.copy(whatsappConsent = false)).isEmpty(), "email-only needs no WhatsApp consent")
    }

    @Test
    fun phonesAndEmailsAreNormalised() {
        assertEquals("+213555123456", normalizePhone(" +213 (555) 12-34.56 "))
        assertEquals("+33612345678", normalizePhone("0033 6 12 34 56 78"))
        assertTrue(isValidInternationalPhone("+213555123456"))
        assertFalse(isValidInternationalPhone("+0555123456"))
        assertFalse(isValidInternationalPhone("+12345"))
        assertFalse(isValidInternationalPhone("+2135551234567890123"))
        assertEquals("ahmed@example.org", normalizeEmail("  Ahmed@Example.ORG "))
    }

    @Test
    fun eventDraftRulesCatchImpossibleDates() {
        val draft = EventDraft(
            eventId = null, title = "Robotics", description = "d", category = "Workshop", location = "Hall", organizerName = "", timezone = "UTC",
            startAt = START, endAt = END, registrationOpenAt = START - 24 * HOUR, registrationCloseAt = START, capacity = 50,
            allowReentry = false, featured = false,
        )
        assertTrue(validateEventDraft(draft).isEmpty())
        assertEquals(setOf(EventField.END), validateEventDraft(draft.copy(endAt = START, registrationCloseAt = START - HOUR)))
        assertEquals(setOf(EventField.REGISTRATION_CLOSE), validateEventDraft(draft.copy(registrationCloseAt = END + HOUR)))
        assertEquals(setOf(EventField.REGISTRATION_CLOSE), validateEventDraft(draft.copy(registrationCloseAt = draft.registrationOpenAt)))
        assertEquals(setOf(EventField.TITLE, EventField.CAPACITY), validateEventDraft(draft.copy(title = "ab", capacity = -1)))
        assertEquals(setOf(EventField.TIMEZONE), validateEventDraft(draft.copy(timezone = "Mars/Olympus")))
    }

    @Test
    fun localDateTimesRoundTripThroughTheEventZone() {
        val millis = parseLocalDateTime("2026-11-14", "09:30", "UTC")
        assertEquals("2026-11-14 09:30", millis!!.isoLocal("UTC"))
        assertNull(parseLocalDateTime("14/11/2026", "09:30", "UTC"))
        assertNull(parseLocalDateTime("2026-11-14", "25:00", "UTC"))
    }
}
