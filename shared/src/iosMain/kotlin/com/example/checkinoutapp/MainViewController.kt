package com.example.checkinoutapp

import androidx.compose.ui.window.ComposeUIViewController
import com.example.checkinoutapp.di.initializeKoin
import com.example.checkinoutapp.firebase.initializeFirebase

private var started = false

fun MainViewController() = ComposeUIViewController {
    App()
}.also {
    if (!started) {
        started = true
        initializeKoin()
        initializeFirebase()
    }
}
