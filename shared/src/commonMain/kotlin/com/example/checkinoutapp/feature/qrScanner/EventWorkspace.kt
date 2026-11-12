package com.example.checkinoutapp.feature.qrScanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.checkinoutapp.domain.session.SessionState
import com.example.checkinoutapp.feature.auth.AuthViewModel
import com.example.checkinoutapp.feature.attendance.AttendanceEvents
import com.example.checkinoutapp.feature.attendance.AttendanceRecordsPanel
import com.example.checkinoutapp.feature.attendance.AttendanceViewModel
import com.example.checkinoutapp.feature.attendance.ParticipantLookupPanel
import com.example.checkinoutapp.feature.organizerHome.StaffTopBar
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ContentColumn
import com.example.checkinoutapp.ui.components.SegmentedControl
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

enum class WorkspaceTab { SCAN, RECORDS, SEARCH }

/** One event's attendance desk: scan, see who is in, or find someone by name. Two taps deep at most. */
@Composable
fun EventWorkspaceScreen(
    id: String,
    title: String,
    navigateBack: () -> Unit,
    navigateToAuth: () -> Unit,
) {
    val scanner = koinViewModel<QrScannerViewModel>()
    val attendance = koinViewModel<AttendanceViewModel>()
    val authViewModel = koinViewModel<AuthViewModel>()
    val session by authViewModel.session.collectAsState()
    val zone by attendance.timezone.collectAsState()
    val user = (session as? SessionState.Active)?.user
    var tab by rememberSaveable { mutableStateOf(WorkspaceTab.SCAN) }

    LaunchedEffect(session) {
        if (session is SessionState.SignedOut || session is SessionState.NoAccess) navigateToAuth()
    }
    AttendanceEvents(attendance)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        StaffTopBar(
            title = title,
            user = user,
            onSignOut = authViewModel::signOut,
            navigateBack = navigateBack
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ContentColumn {
                Spacer(Modifier.height(Spacing.lg))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SegmentedControl(
                        WorkspaceTab.entries, tab,
                        label = {
                            stringResource(
                                when (it) {
                                    WorkspaceTab.SCAN -> Res.string.ws_tab_scan
                                    WorkspaceTab.RECORDS -> Res.string.ws_tab_records
                                    WorkspaceTab.SEARCH -> Res.string.ws_tab_search
                                },
                            )
                        },
                        onSelect = { tab = it },
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                when (tab) {
                    WorkspaceTab.SCAN -> QrScanner(scanner, zone)
                    WorkspaceTab.RECORDS -> AttendanceRecordsPanel(attendance)
                    WorkspaceTab.SEARCH -> ParticipantLookupPanel(attendance)
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}
