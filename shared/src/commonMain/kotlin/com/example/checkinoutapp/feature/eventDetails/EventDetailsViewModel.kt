package com.example.checkinoutapp.feature.eventDetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.event.RegistrationAvailability
import com.example.checkinoutapp.domain.event.registrationAvailability
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class EventDetailsContent(val event: ClubEvent, val availability: RegistrationAvailability)

class EventDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    eventRepository: EventRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val eventId = savedStateHandle.get<String>("id") ?: ""

    /** Success(null) = no such published event. */
    val details: StateFlow<RequestState<EventDetailsContent?>> = eventRepository.readEventByIdFlow(eventId)
        .map { state -> state.map { event -> event?.let { EventDetailsContent(it, it.registrationAvailability(clock.nowMillis())) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)
}
