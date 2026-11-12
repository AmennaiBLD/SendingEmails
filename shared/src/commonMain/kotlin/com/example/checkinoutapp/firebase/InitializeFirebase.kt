package com.example.checkinoutapp.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.app
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.initialize
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions

/**
 * Points Firebase at the local Emulator Suite when firebase/app-config.json names an emulator
 * host. Does nothing otherwise. Call it once, right after Firebase is initialized.
 */
fun connectFirebaseEmulators(androidEmulator: Boolean = false) {
    try {
        val host = emulatorHost(androidEmulator)
        if (host.isNotBlank()) {
            Firebase.auth.useEmulator(host, 9099)
            Firebase.firestore.useEmulator(host, 8080)
            Firebase.functions(region = FirebaseAppConfig.FUNCTIONS_REGION).useEmulator(host, 5001)
        }
    } catch (e: Exception) {
        println("[club] Could not connect to the Firebase emulators: ${e.message}")
    }
}

private fun emulatorHost(androidEmulator: Boolean): String =
    if (androidEmulator && FirebaseAppConfig.EMULATOR_HOST.isNotBlank()) {
        // An Android emulator reaches the host machine at 10.0.2.2.
        FirebaseAppConfig.ANDROID_EMULATOR_HOST.ifBlank { FirebaseAppConfig.EMULATOR_HOST }
    } else {
        FirebaseAppConfig.EMULATOR_HOST
    }

/** What `Firebase.initialize` needs on this platform: the Android context, or nothing. */
internal expect fun platformFirebaseContext(): Any?

internal expect val runsOnAndroid: Boolean

private const val STAFF_APP_NAME = "staff-accounts"

/**
 * A second Firebase app, used only to create staff sign-ins. Its Auth session is separate from
 * the main one, so creating an account never signs the administrator out.
 */
internal fun staffAccountsApp(): FirebaseApp {
    val context = platformFirebaseContext()
    Firebase.apps(context).firstOrNull { it.name == STAFF_APP_NAME }?.let { return it }
    val app = Firebase.initialize(context, Firebase.app.options, STAFF_APP_NAME)
    val host = emulatorHost(runsOnAndroid)
    if (host.isNotBlank()) Firebase.auth(app).useEmulator(host, 9099)
    return app
}

internal fun hasFirebaseConfig(appId: String): Boolean =
    FirebaseAppConfig.API_KEY.isNotBlank() && FirebaseAppConfig.PROJECT_ID.isNotBlank() && appId.isNotBlank()

internal const val FIREBASE_NOT_CONFIGURED = "[club] Firebase is not configured: add firebase/app-config.json (see the README)."
