package com.example.checkinoutapp.core

import kotlin.time.Clock

/**
 * The single source of "now" for the data layer.
 *
 * Injected rather than called statically so that tests can pin time and assert on
 * `receivedAt` / `saleDate` deterministically — otherwise every timestamp assertion would
 * have to be a range check.
 *
 * CAVEAT: this is the *client* clock. An operator with a wrong device clock writes wrong
 * audit timestamps, which matters for an append-only trail. Enforcing server time
 * (`request.time` in the rules, or `FieldValue.serverTimestamp`) is a production-hardening
 * item — see docs/IMPLEMENTATION_STATUS.md.
 */
fun interface AppClock {

    /** Milliseconds since the Unix epoch. */
    fun nowMillis(): Long

    companion object {
        val System: AppClock = AppClock { Clock.System.now().toEpochMilliseconds() }
    }
}
