package com.example.checkinoutapp.feature.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.feature.admin.EventPicker
import com.example.checkinoutapp.feature.admin.EventSelection
import com.example.checkinoutapp.feature.admin.WithSelectedEvent
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.SegmentedControl
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Which event the administrator's attendance page is showing. */
class AdminAttendanceViewModel(eventRepository: EventRepository, clock: AppClock) : ViewModel() {
    val selection = EventSelection(eventRepository, viewModelScope, clock)
}

/** The administrator's view of one event's attendance: the same records and lookup organizers use. */
@Composable
fun AdminAttendanceScreen(
    navigateToEventWorkspace: (String, String) -> Unit,
) {
    val viewModel = koinViewModel<AdminAttendanceViewModel>()
    val attendance = koinViewModel<AttendanceViewModel>()
    var showLookup by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        EventPicker(viewModel.selection)
        WithSelectedEvent(viewModel.selection) { event ->
            // The attendance panels follow whichever event is picked above.
            LaunchedEffect(event.id) { attendance.setEvent(event.id) }
            AttendanceEvents(attendance)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SegmentedControl(
                    listOf(false, true), showLookup,
                    label = { stringResource(if (it) Res.string.ws_tab_search else Res.string.ws_tab_records) }, onSelect = { showLookup = it },
                )
                ClubButton(stringResource(Res.string.ws_tab_scan), { navigateToEventWorkspace(event.id, event.title) }, icon = Icons.Outlined.QrCodeScanner)
            }
            if (showLookup) ParticipantLookupPanel(attendance) else AttendanceRecordsPanel(attendance)
        }
    }
}
