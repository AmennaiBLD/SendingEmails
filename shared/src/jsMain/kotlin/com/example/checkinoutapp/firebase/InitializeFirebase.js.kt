package com.example.checkinoutapp.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.initialize

fun initializeFirebase() {
    if (!hasFirebaseConfig(FirebaseAppConfig.WEB_APP_ID)) {
        println(FIREBASE_NOT_CONFIGURED)
        return
    }
    try {
        Firebase.initialize(
            null,
            options = FirebaseOptions(
                apiKey = FirebaseAppConfig.API_KEY,
                authDomain = FirebaseAppConfig.AUTH_DOMAIN.takeIf { it.isNotBlank() },
                projectId = FirebaseAppConfig.PROJECT_ID,
                storageBucket = FirebaseAppConfig.STORAGE_BUCKET.takeIf { it.isNotBlank() },
                gcmSenderId = FirebaseAppConfig.SENDER_ID.takeIf { it.isNotBlank() },
                applicationId = FirebaseAppConfig.WEB_APP_ID,
            ),
        )
        // The web SDK only honours settings and the emulator on the Firestore instance that is
        // created with them. Create it here, once; every later Firebase.firestore is that instance.
        val host = FirebaseAppConfig.EMULATOR_HOST
        val firestore = Firebase.firestore
        firestore.settings = firestoreSettings { cacheSettings = memoryCacheSettings { } }
        if (host.isNotBlank()) {
            Firebase.auth.useEmulator(host, 9099)
            firestore.useEmulator(host, 8080)
            Firebase.functions(region = FirebaseAppConfig.FUNCTIONS_REGION).useEmulator(host, 5001)
        }
        firestore.collection(collectionPath = "settings")
    } catch (e: Exception) {
        println("[club] Firebase could not start: ${e.message}")
    }
}

internal actual fun platformFirebaseContext(): Any? = null

internal actual val runsOnAndroid: Boolean = false
