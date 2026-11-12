package com.example.checkinoutapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.checkinoutapp.di.initializeKoin
import com.example.checkinoutapp.firebase.initializeFirebase
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializeKoin()
    initializeFirebase()
    document.getElementById("loading")?.remove()
    ComposeViewport {
        App()
    }
}
