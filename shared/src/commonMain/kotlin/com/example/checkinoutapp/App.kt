package com.example.checkinoutapp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.domain.repository.AuthRepository
import com.example.checkinoutapp.navigation.Screen
import com.example.checkinoutapp.navigation.SetupNavGraph
import com.example.checkinoutapp.resources.Res
import com.example.checkinoutapp.resources.allStringResources
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.platform.isMobilePlatform
import com.example.checkinoutapp.ui.theme.ClubTheme
import com.example.checkinoutapp.ui.theme.LocalWidthClass
import com.example.checkinoutapp.ui.theme.WidthClass
import com.mmk.kmpauth.google.GoogleAuthCredentials
import com.mmk.kmpauth.google.GoogleAuthProvider
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
    ClubTheme {
        val authRepository = koinInject<AuthRepository>()

        val isUserAuthenticated = remember { authRepository.getCurrentUserId() != null }

        val startDestination = remember {
            if (isUserAuthenticated || isMobilePlatform) Screen.Auth
            else Screen.Home
        }

        val snackbar = remember { SnackbarHostState() }


        LaunchedEffect(Unit) {
            delay(3000)
            Res.allStringResources.values.forEach { runCatching { getString(it) } }
        }

        LaunchedEffect(Unit) {
            GoogleAuthProvider.create(
                credentials = GoogleAuthCredentials(serverId = "110542099407-tckbktfptri4i9m9st3annr4tk46uj2r.apps.googleusercontent.com")
            )

        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalWidthClass provides WidthClass.of(maxWidth),
                LocalSnackbar provides snackbar
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { _ ->
                    SetupNavGraph(
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
