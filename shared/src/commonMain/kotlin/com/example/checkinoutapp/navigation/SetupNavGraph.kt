package com.example.checkinoutapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.checkinoutapp.feature.admin.AdminPanelScreen
import com.example.checkinoutapp.feature.auth.AuthScreen
import com.example.checkinoutapp.feature.eventDetails.EventDetailsScreen
import com.example.checkinoutapp.feature.events.EventsScreen
import com.example.checkinoutapp.feature.home.HomeScreen
import com.example.checkinoutapp.feature.organizerHome.OrganizerHomeScreen
import com.example.checkinoutapp.feature.qrScanner.EventWorkspaceScreen
import com.example.checkinoutapp.feature.registration.RegistrationScreen
import com.example.checkinoutapp.feature.ticket.FindTicketScreen

@Composable
fun SetupNavGraph(
    startDestination: Screen = Screen.Home
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<Screen.Home> {
            HomeScreen(
                navigateToEvents = {
                    navController.navigate(Screen.Events)
                },
                navigateToEventDetails = { id ->
                    navController.navigate(Screen.EventDetails(id))
                },
                navigateToAuth = {
                    navController.navigate(Screen.Auth)
                }
            )
        }
        composable<Screen.Events> {
            EventsScreen(
                navigateToHome = {
                    navController.navigate(Screen.Home) {
                        popUpTo<Screen.Home> { inclusive = true }
                    }
                },
                navigateToEventDetails = { id ->
                    navController.navigate(Screen.EventDetails(id))
                },
                navigateToAuth = {
                    navController.navigate(Screen.Auth)
                }
            )
        }
        composable<Screen.EventDetails> {
            val id = it.toRoute<Screen.EventDetails>().id
            EventDetailsScreen(
                id = id,
                navigateBack = {
                    navController.navigateUp()
                },
                navigateToHome = {
                    navController.navigate(Screen.Home) {
                        popUpTo<Screen.Home> { inclusive = true }
                    }
                },
                navigateToEvents = {
                    navController.navigate(Screen.Events) {
                        launchSingleTop = true
                    }
                },
                navigateToAuth = {
                    navController.navigate(Screen.Auth)
                },
                navigateToRegistration = { eventId ->
                    navController.navigate(Screen.Registration(eventId))
                },
                navigateToFindTicket = { eventId ->
                    navController.navigate(Screen.FindTicket(eventId))
                }
            )
        }
        composable<Screen.Registration> {
            val id = it.toRoute<Screen.Registration>().id
            RegistrationScreen(
                id = id,
                navigateBack = {
                    navController.navigateUp()
                },
                navigateToHome = {
                    navController.navigate(Screen.Home) {
                        popUpTo<Screen.Home> { inclusive = true }
                    }
                },
                navigateToEvents = {
                    navController.navigate(Screen.Events) {
                        launchSingleTop = true
                    }
                },
                navigateToAuth = {
                    navController.navigate(Screen.Auth)
                },
                navigateToFindTicket = { eventId ->
                    navController.navigate(Screen.FindTicket(eventId))
                }
            )
        }
        composable<Screen.FindTicket> {
            val id = it.toRoute<Screen.FindTicket>().id
            FindTicketScreen(
                id = id,
                navigateBack = {
                    navController.navigateUp()
                },
                navigateToHome = {
                    navController.navigate(Screen.Home) {
                        popUpTo<Screen.Home> { inclusive = true }
                    }
                },
                navigateToEvents = {
                    navController.navigate(Screen.Events) {
                        launchSingleTop = true
                    }
                },
                navigateToAuth = {
                    navController.navigate(Screen.Auth)
                }
            )
        }
        composable<Screen.Auth> {
            AuthScreen(
                navigateToOrganizerHome = {
                    navController.navigate(Screen.OrganizerHome) {
                        popUpTo<Screen.Auth> { inclusive = true }
                    }
                },
                navigateToAdminPanel = {
                    navController.navigate(Screen.AdminPanel) {
                        popUpTo<Screen.Auth> { inclusive = true }
                    }
                },
                navigateToHome = {
                    navController.navigate(Screen.Home) {
                        launchSingleTop = true
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable<Screen.OrganizerHome> {
            OrganizerHomeScreen(
                navigateBack = {
                    navController.navigateUp()
                },
                navigateToAuth = {
                    navController.navigate(Screen.Auth) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                navigateToEventWorkspace = { id, title ->
                    navController.navigate(Screen.EventWorkspace(id, title))
                }
            )
        }
        composable<Screen.EventWorkspace> {
            val id = it.toRoute<Screen.EventWorkspace>().id
            val title = it.toRoute<Screen.EventWorkspace>().title
            EventWorkspaceScreen(
                id = id,
                title = title,
                navigateBack = {
                    navController.navigateUp()
                },
                navigateToAuth = {
                    navController.navigate(Screen.Auth) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable<Screen.AdminPanel> {
            AdminPanelScreen(
                navigateToAuth = {
                    navController.navigate(Screen.Auth) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                navigateToHome = {
                    navController.navigate(Screen.Home)
                },
                navigateToOrganizerHome = {
                    navController.navigate(Screen.OrganizerHome)
                },
                navigateToEventWorkspace = { id, title ->
                    navController.navigate(Screen.EventWorkspace(id, title))
                }
            )
        }
    }
}
