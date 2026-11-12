package com.example.checkinoutapp.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {

    @Serializable
    data object Home: Screen()

    @Serializable
    data object Events: Screen()

    @Serializable
    data class EventDetails(
        val id :String
    ): Screen()

    @Serializable
    data class Registration(
        val id :String
    ): Screen()

    @Serializable
    data class FindTicket(
        val id :String
    ): Screen()

    @Serializable
    data object Auth: Screen()

    @Serializable
    data object OrganizerHome: Screen()

    @Serializable
    data class EventWorkspace(
        val id :String,
        val title :String
    ): Screen()

    @Serializable
    data object AdminPanel: Screen()
}
