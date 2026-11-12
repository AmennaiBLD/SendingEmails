package com.example.checkinoutapp.domain

import com.example.checkinoutapp.domain.event.slugify
import com.example.checkinoutapp.domain.registration.isValidEmail
import com.example.checkinoutapp.domain.registration.registrationKeyId
import com.example.checkinoutapp.domain.ticket.ParsedTicket
import com.example.checkinoutapp.domain.ticket.newTicketSecret
import com.example.checkinoutapp.domain.ticket.parseTicketCode
import com.example.checkinoutapp.domain.ticket.sameSecret
import com.example.checkinoutapp.domain.ticket.ticketCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicketCodeTest {

    @Test
    fun aTicketCodeRoundTrips() {
        val secret = newTicketSecret()
        val code = ticketCode("AbCdEf0123456789XyZw", 3, secret)
        assertEquals(ParsedTicket("AbCdEf0123456789XyZw", 3, secret), parseTicketCode(code))
        assertEquals(ParsedTicket("AbCdEf0123456789XyZw", 3, secret), parseTicketCode("  $code\n"))
    }

    @Test
    fun secretsAreLongRandomAndHex() {
        val a = newTicketSecret()
        val b = newTicketSecret()
        assertEquals(64, a.length)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(a, b)
    }

    @Test
    fun anythingElseIsNotATicket() {
        val secret = "a".repeat(64)
        listOf(
            null, "", "https://example.org", "SC1.AbCdEf0123456789XyZw.1.signature",
            "SC2.short.1.$secret", "SC2.AbCdEf0123456789XyZw.x.$secret", "SC2.AbCdEf0123456789XyZw.1.NOTHEX",
            "SC2.AbCdEf0123456789XyZw.1.$secret.extra", "x".repeat(5000),
        ).forEach { assertNull(parseTicketCode(it), "accepted: $it") }
    }

    @Test
    fun secretsCompareExactly() {
        assertTrue(sameSecret("abc123", "abc123"))
        assertFalse(sameSecret("abc123", "abc124"))
        assertFalse(sameSecret("abc123", "abc12"))
    }

    @Test
    fun registrationKeysAndSlugsAreSafeIds() {
        assertEquals("ev1_ahmed@example.org", registrationKeyId("ev1", "ahmed@example.org"))
        assertFalse(isValidEmail("a/b@example.org"), "a slash would break the key's document path")
        assertEquals("ai-robotics-workshop", slugify("AI & Robotics Workshop!"))
        assertEquals("event", slugify("!!!"))
    }
}
