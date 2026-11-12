package com.example.checkinoutapp.feature.organizerHome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.domain.model.AppUser
import com.example.checkinoutapp.domain.session.SessionState
import com.example.checkinoutapp.feature.auth.AuthViewModel
import com.example.checkinoutapp.feature.home.ClubInfoViewModel
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CardGrid
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ClubCard
import com.example.checkinoutapp.ui.components.ClubMark
import com.example.checkinoutapp.ui.components.ContentColumn
import com.example.checkinoutapp.ui.components.DialogFrame
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.IconLine
import com.example.checkinoutapp.ui.components.LabeledValue
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatTimeRange
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.format.tone
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.isCompact
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme
import com.example.checkinoutapp.domain.model.UserRole

/** The bar at the top of every staff screen: where you are, who you are, and the way out. */
@Composable
fun StaffTopBar(title: String, user: AppUser?, onSignOut: () -> Unit, navigateBack: (() -> Unit)? = null, clubName: String = "", actions: @Composable () -> Unit = {}) {
    var showProfile by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (navigateBack != null) IconButton(navigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.common_back)) }
            else if (clubName.isNotBlank()) ClubMark(clubName, size = 32.dp)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
            if (user != null) IconButton({ showProfile = true }) { Icon(Icons.Outlined.AccountCircle, stringResource(Res.string.profile_title)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (showProfile && user != null) {
        DialogFrame(
            stringResource(Res.string.profile_title), { showProfile = false },
            actions = { ClubButton(stringResource(Res.string.auth_sign_out), { showProfile = false; onSignOut() }, kind = ButtonKind.Secondary, icon = Icons.AutoMirrored.Outlined.Logout) },
        ) {
            LabeledValue(stringResource(Res.string.profile_name), user.fullName)
            LabeledValue(stringResource(Res.string.profile_email), user.email)
            LabeledValue(stringResource(Res.string.profile_role), user.role.label())
        }
    }
}

@Composable
fun OrganizerHomeScreen(
    navigateBack: () -> Unit,
    navigateToAuth: () -> Unit,
    navigateToEventWorkspace: (String, String) -> Unit,
) {
    val viewModel = koinViewModel<OrganizerHomeViewModel>()
    val authViewModel = koinViewModel<AuthViewModel>()
    val clubInfoViewModel = koinViewModel<ClubInfoViewModel>()
    val events by viewModel.events.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val session by authViewModel.session.collectAsState()
    val settings by clubInfoViewModel.settings.collectAsState()
    val user = (session as? SessionState.Active)?.user

    // Signed out (here or anywhere): leave for the sign-in screen.
    LaunchedEffect(session) {
        if (session is SessionState.SignedOut || session is SessionState.NoAccess) navigateToAuth()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        StaffTopBar(
            title = stringResource(Res.string.org_title),
            user = user,
            onSignOut = authViewModel::signOut,
            // An administrator arrives here from the admin panel and can go back to it.
            navigateBack = if (isAdmin) navigateBack else null,
            clubName = settings.clubName
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ContentColumn {
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    stringResource(if (isAdmin) Res.string.org_subtitle_admin else Res.string.org_subtitle_organizer),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))
                events.DisplayResult(
                    onLoading = { SkeletonList(rows = 3, rowHeight = 120.dp) },
                    onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
                    onSuccess = { list ->
                        if (list.isEmpty()) {
                            EmptyState(Icons.Outlined.EventBusy, stringResource(Res.string.org_empty_title), body = stringResource(Res.string.org_empty_body))
                        } else {
                            CardGrid(list.sortedByDescending { it.startAt }, if (isCompact) 1 else 2) { event, modifier ->
                                ClubCard(modifier, onClick = { navigateToEventWorkspace(event.eventId, event.title) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(event.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        StatusChip(event.status.label(), event.status.tone())
                                    }
                                    Spacer(Modifier.height(Spacing.sm))
                                    IconLine(Icons.Outlined.CalendarMonth, formatDate(event.startAt, event.timezone))
                                    IconLine(Icons.Outlined.Schedule, formatTimeRange(event.startAt, event.endAt, event.timezone))
                                    IconLine(Icons.Outlined.Place, event.location)
                                    Spacer(Modifier.height(Spacing.md))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                        Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(stringResource(Res.string.org_open), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    },
                )
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Preview
@Composable
private fun StaffTopBarPreview() {
    ClubTheme {
        StaffTopBar(
            title = "AI & Robotics Workshop",
            user = AppUser(uid = "preview", fullName = "Omar Organizer", email = "organizer@example.org", phone = "", role = UserRole.ORGANIZER, isActive = true),
            onSignOut = {},
            navigateBack = {}
        )
    }
}
