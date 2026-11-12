package com.example.checkinoutapp.core

const val FIREBASE_UNAVAILABLE =
    "The app is not connected to its backend on this device. Contact the club administrator."

/** Turns a raw SDK exception into a message a volunteer at the door can act on. */
fun Exception.toUserMessage(): String {
    val raw = message.orEmpty()
    fun has(vararg needles: String) = needles.any { raw.contains(it, ignoreCase = true) }
    return when {
        has("FirebaseApp is not initialized", "No Firebase App", "app/no-app") -> FIREBASE_UNAVAILABLE
        has("PERMISSION_DENIED", "permission-denied", "insufficient permissions") -> "You do not have permission to do this."
        has("UNAUTHENTICATED") -> "Your session has ended. Sign in again."
        has("UNAVAILABLE", "network", "Failed to fetch", "timeout", "Unable to resolve host", "ECONNREFUSED") ->
            "No connection. Check the network and try again."
        has("no user record", "user-not-found", "INVALID_LOGIN_CREDENTIALS", "password is invalid", "wrong-password", "invalid-credential") ->
            "Incorrect email or password."
        has("badly formatted", "invalid-email") -> "That email address is not valid."
        has("user-disabled", "disabled by an administrator") -> "This account has been deactivated."
        has("blocked all requests", "too-many-requests") -> "Too many attempts. Wait a moment and try again."
        raw.isBlank() -> "Something went wrong. Try again."
        else -> raw
    }
}
