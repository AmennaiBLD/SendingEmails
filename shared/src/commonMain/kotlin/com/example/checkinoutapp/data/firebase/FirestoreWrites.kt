package com.example.checkinoutapp.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlin.time.Clock
import kotlin.time.Instant

// Small pieces shared by the repositories that write to Firestore. firestore.rules re-checks
// every write: who may do it, which fields change, and that each instant is close to the
// server's time (request.time).

/** The signed-in staff member, as recorded in `users/{uid}`. */
internal class Actor(val uid: String, val name: String)

internal suspend fun currentActor(): Actor {
    val uid = Firebase.auth.currentUser?.uid ?: throw IllegalStateException("UNAUTHENTICATED")
    val document = Firebase.firestore.collection(collectionPath = "users").document(uid).get()
    return Actor(uid = uid, name = document.get<String?>(field = "fullName") ?: "")
}

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Instants inside audit values travel as Double: Kotlin/JS has no native 64-bit number. */
internal fun Long.asAuditValue(): Double = toDouble()

/**
 * One append-only audit entry. `details` is the one-line summary the audit viewer shows:
 * `field: before -> after` for every changed field.
 */
internal fun auditLog(
    actor: Actor,
    action: String,
    entityType: String,
    entityId: String,
    eventId: String?,
    at: Timestamp,
    reason: String? = null,
    previousValue: Map<String, Any?>? = null,
    newValue: Map<String, Any?>? = null,
): Map<String, Any?> = mapOf(
    "actorId" to actor.uid,
    "actorName" to actor.name,
    "action" to action,
    "entityType" to entityType,
    "entityId" to entityId,
    "eventId" to eventId,
    "reason" to reason,
    "previousValue" to previousValue,
    "newValue" to newValue,
    "details" to describeChange(previousValue, newValue),
    "createdAt" to at,
)

private fun describeChange(before: Map<String, Any?>?, after: Map<String, Any?>?): String {
    val old = before.orEmpty()
    return after.orEmpty().entries.joinToString("; ") { (key, value) ->
        if (key in old) "$key: ${show(old[key])} -> ${show(value)}" else "$key: ${show(value)}"
    }.take(600)
}

/** Epoch milliseconds read better as an instant. */
private fun show(value: Any?): String = when {
    value == null -> "-"
    value is Double && value > 1e11 -> Instant.fromEpochMilliseconds(value.toLong()).toString().take(16).replace('T', ' ') + " UTC"
    value is Double && value == kotlin.math.floor(value) -> value.toLong().toString()
    else -> value.toString()
}
