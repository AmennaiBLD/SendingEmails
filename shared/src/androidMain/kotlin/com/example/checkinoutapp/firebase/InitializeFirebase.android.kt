package com.example.checkinoutapp.firebase

internal actual fun platformFirebaseContext(): Any? = com.google.firebase.FirebaseApp.getInstance().applicationContext

internal actual val runsOnAndroid: Boolean = true
