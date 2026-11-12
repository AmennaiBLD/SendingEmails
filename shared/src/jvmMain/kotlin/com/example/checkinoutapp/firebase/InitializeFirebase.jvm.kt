package com.example.checkinoutapp.firebase

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize

/**
 * Desktop runs on the community firebase-java-sdk. The session is kept in memory only: nothing
 * about the signed-in user is written to disk, so closing the app signs out.
 */
private class MemoryFirebasePlatform : FirebasePlatform() {
    private val values = mutableMapOf<String, String>()

    @Synchronized override fun store(key: String, value: String) { values[key] = value }
    @Synchronized override fun retrieve(key: String): String? = values[key]
    @Synchronized override fun clear(key: String) { values.remove(key) }
    override fun log(msg: String) = Unit
}

fun initializeFirebase() {
    FirebasePlatform.initializeFirebasePlatform(MemoryFirebasePlatform())
    if (!hasFirebaseConfig(FirebaseAppConfig.WEB_APP_ID)) {
        println(FIREBASE_NOT_CONFIGURED)
        return
    }
    try {
        Firebase.initialize(
            Application(),
            options = FirebaseOptions(
                projectId = FirebaseAppConfig.PROJECT_ID,
                applicationId = FirebaseAppConfig.WEB_APP_ID,
                apiKey = FirebaseAppConfig.API_KEY,
                storageBucket = FirebaseAppConfig.STORAGE_BUCKET.takeIf { it.isNotBlank() },
            ),
        )
        connectFirebaseEmulators()
    } catch (e: Exception) {
        println("[club] Firebase could not start: ${e.message}")
    }
}

internal actual fun platformFirebaseContext(): Any? = Application()

internal actual val runsOnAndroid: Boolean = false
