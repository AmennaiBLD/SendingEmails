package com.example.checkinoutapp

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.checkinoutapp.di.initializeKoin
import com.example.checkinoutapp.firebase.initializeFirebase

fun main() {
    initializeKoin()
    initializeFirebase()
    application {
        Window(onCloseRequest = ::exitApplication, title = "Scientific Club", state = rememberWindowState(size = DpSize(1360.dp, 860.dp))) {
            App()
        }
    }
}
