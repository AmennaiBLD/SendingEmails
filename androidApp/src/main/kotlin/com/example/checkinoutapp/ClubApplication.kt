package com.example.checkinoutapp

import android.app.Application
import com.example.checkinoutapp.di.initializeKoin
import com.example.checkinoutapp.firebase.connectFirebaseEmulators
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import org.koin.android.ext.koin.androidContext

class ClubApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        initializeKoin(
            config = {
                androidContext(this@ClubApplication)
            }
        )
        Firebase.initialize(context = this)
        connectFirebaseEmulators(androidEmulator = true)
    }
}
