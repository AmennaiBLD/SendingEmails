package com.example.checkinoutapp.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.initialize
import platform.Foundation.NSBundle

/**
 * Uses GoogleService-Info.plist when the app bundle has one; otherwise firebase/app-config.json.
 * FirebaseApp.configure() must never run twice (it raises an NSException), hence the first check.
 */
fun initializeFirebase() {
    try {
        if (Firebase.apps(null).isNotEmpty()) return
        if (NSBundle.mainBundle.pathForResource("GoogleService-Info", "plist") != null) {
            Firebase.initialize()
        } else {
            val appId = FirebaseAppConfig.IOS_APP_ID.ifBlank { FirebaseAppConfig.WEB_APP_ID }
            if (!hasFirebaseConfig(appId)) {
                println(FIREBASE_NOT_CONFIGURED)
                return
            }
            Firebase.initialize(
                null,
                FirebaseOptions(
                    applicationId = appId,
                    apiKey = FirebaseAppConfig.API_KEY,
                    projectId = FirebaseAppConfig.PROJECT_ID,
                    storageBucket = FirebaseAppConfig.STORAGE_BUCKET.takeIf { it.isNotBlank() },
                    gcmSenderId = FirebaseAppConfig.SENDER_ID.ifBlank { "000000000000" },
                ),
            )
        }
        connectFirebaseEmulators()
    } catch (e: Exception) {
        println("[club] Firebase could not start: ${e.message}")
    }
}

internal actual fun platformFirebaseContext(): Any? = null

internal actual val runsOnAndroid: Boolean = false
