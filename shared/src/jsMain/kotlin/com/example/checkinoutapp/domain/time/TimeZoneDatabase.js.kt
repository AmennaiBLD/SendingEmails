package com.example.checkinoutapp.domain.time

@JsModule("@js-joda/timezone")
@JsNonModule
private external object JsJodaTimeZoneModule

private val timeZoneDatabase = JsJodaTimeZoneModule

/** Referencing the module is what makes the bundler ship the tz database kotlinx-datetime needs. */
actual fun ensureTimeZoneDatabase() {
    timeZoneDatabase
}
