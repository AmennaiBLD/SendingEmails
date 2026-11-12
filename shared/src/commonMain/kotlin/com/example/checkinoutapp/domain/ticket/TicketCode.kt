package com.example.checkinoutapp.domain.ticket

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The QR on a ticket: `SC2.<registrationId>.<ticketVersion>.<secret>`.
 *
 * The secret is 64 random hex characters made on the device that registers (a secure random
 * source on every platform) and stored on the registration, which only administrators and the
 * event's organizers can read. A scan matches it against the stored one. Replacing a ticket
 * writes a new secret and bumps the version, so every older QR stops working.
 */
const val TICKET_PREFIX = "SC2"

data class ParsedTicket(val registrationId: String, val version: Int, val secret: String)

fun ticketCode(registrationId: String, version: Int, secret: String): String = "$TICKET_PREFIX.$registrationId.$version.$secret"

private val REGISTRATION_ID = Regex("^[A-Za-z0-9]{10,40}$")
private val VERSION = Regex("^\\d{1,6}$")
private val SECRET = Regex("^[a-f0-9]{32,128}$")

/** Null for anything that is not one of our tickets. Never logs the code. */
fun parseTicketCode(raw: String?): ParsedTicket? {
    val text = raw?.trim() ?: return null
    if (text.length > 220) return null
    val parts = text.split('.')
    if (parts.size != 4 || parts[0] != TICKET_PREFIX) return null
    val (_, registrationId, version, secret) = parts
    if (!REGISTRATION_ID.matches(registrationId) || !VERSION.matches(version) || !SECRET.matches(secret)) return null
    return ParsedTicket(registrationId, version.toInt(), secret)
}

@OptIn(ExperimentalUuidApi::class)
fun newTicketSecret(): String = Uuid.random().toHexString() + Uuid.random().toHexString()

/** Compares in constant time, so a scan cannot learn the secret one character at a time. */
fun sameSecret(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
    return diff == 0
}
