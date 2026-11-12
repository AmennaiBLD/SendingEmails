package com.example.checkinoutapp.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.domain.model.UserRole
import com.example.checkinoutapp.domain.session.SessionState
import com.example.checkinoutapp.feature.adminDashboard.DashboardScreen
import com.example.checkinoutapp.feature.analytics.AnalyticsScreen
import com.example.checkinoutapp.feature.attendance.AdminAttendanceScreen
import com.example.checkinoutapp.feature.auditLog.AuditLogScreen
import com.example.checkinoutapp.feature.auth.AuthViewModel
import com.example.checkinoutapp.feature.eventManagement.EventManagementScreen
import com.example.checkinoutapp.feature.home.ClubInfoViewModel
import com.example.checkinoutapp.feature.notifications.NotificationsScreen
import com.example.checkinoutapp.feature.organizerHome.StaffTopBar
import com.example.checkinoutapp.feature.organizerManagement.OrganizersScreen
import com.example.checkinoutapp.feature.participantManagement.ParticipantsScreen
import com.example.checkinoutapp.feature.reports.ReportsScreen
import com.example.checkinoutapp.feature.settings.SettingsScreen
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ClubMark
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.isWide
import com.example.checkinoutapp.ui.theme.pagePadding
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

enum class AdminSection(val title: StringResource, val icon: ImageVector) {
    OVERVIEW(Res.string.adm_overview, Icons.Outlined.Dashboard),
    EVENTS(Res.string.adm_events, Icons.Outlined.Event),
    PARTICIPANTS(Res.string.adm_participants, Icons.Outlined.Groups),
    ORGANIZERS(Res.string.adm_organizers, Icons.Outlined.Badge),
    ATTENDANCE(Res.string.adm_attendance, Icons.AutoMirrored.Outlined.FactCheck),
    ANALYTICS(Res.string.adm_analytics, Icons.Outlined.Analytics),
    NOTIFICATIONS(Res.string.adm_notifications, Icons.Outlined.Notifications),
    REPORTS(Res.string.adm_reports, Icons.Outlined.FileDownload),
    AUDIT(Res.string.adm_audit, Icons.Outlined.History),
    SETTINGS(Res.string.adm_settings, Icons.Outlined.Settings),
}

/** The administrator's workspace: a sidebar on wide screens, a scrolling tab row on narrow ones. */
@Composable
fun AdminPanelScreen(
    navigateToAuth: () -> Unit,
    navigateToHome: () -> Unit,
    navigateToOrganizerHome: () -> Unit,
    navigateToEventWorkspace: (String, String) -> Unit,
) {
    val authViewModel = koinViewModel<AuthViewModel>()
    val clubInfoViewModel = koinViewModel<ClubInfoViewModel>()
    val session by authViewModel.session.collectAsState()
    val settings by clubInfoViewModel.settings.collectAsState()
    val user = (session as? SessionState.Active)?.user
    var section by rememberSaveable { mutableStateOf(AdminSection.OVERVIEW) }

    // Signed out, or no longer an administrator: back to sign-in, which routes by role.
    LaunchedEffect(session) {
        val current = session
        val notAdmin = current is SessionState.Active && current.role != UserRole.ADMIN
        if (current is SessionState.SignedOut || current is SessionState.NoAccess || notAdmin) navigateToAuth()
    }

    val topBar: @Composable () -> Unit = {
        StaffTopBar(
            title = stringResource(section.title),
            user = user,
            onSignOut = authViewModel::signOut,
            clubName = if (isWide) "" else settings.clubName
        ) {
            ClubButton(stringResource(Res.string.adm_attendance_desk), navigateToOrganizerHome, kind = ButtonKind.Secondary, icon = Icons.Outlined.QrCodeScanner)
            if (isWide) ClubButton(stringResource(Res.string.adm_public_site), navigateToHome, kind = ButtonKind.Ghost, icon = Icons.Outlined.Public)
        }
    }
    val page: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1320.dp).fillMaxWidth().padding(pagePadding())) {
                when (section) {
                    AdminSection.OVERVIEW -> DashboardScreen(navigateToSection = { section = it })
                    AdminSection.EVENTS -> EventManagementScreen()
                    AdminSection.PARTICIPANTS -> ParticipantsScreen()
                    AdminSection.ORGANIZERS -> OrganizersScreen()
                    AdminSection.ATTENDANCE -> AdminAttendanceScreen(navigateToEventWorkspace = navigateToEventWorkspace)
                    AdminSection.ANALYTICS -> AnalyticsScreen()
                    AdminSection.NOTIFICATIONS -> NotificationsScreen()
                    AdminSection.REPORTS -> ReportsScreen()
                    AdminSection.AUDIT -> AuditLogScreen()
                    AdminSection.SETTINGS -> SettingsScreen()
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        if (isWide) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(settings.clubName, section) { section = it }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    topBar()
                    page()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                topBar()
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    AdminSection.entries.forEach { item ->
                        FilterChip(item == section, { section = item }, { Text(stringResource(item.title)) }, leadingIcon = { Icon(item.icon, null, Modifier.size(16.dp)) })
                    }
                }
                page()
            }
        }
    }
}

@Composable
private fun Sidebar(clubName: String, current: AdminSection, onSelect: (AdminSection) -> Unit) {
    Column(Modifier.width(236.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ClubMark(clubName)
            Text(clubName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
        }
        Spacer(Modifier.height(Spacing.lg))
        AdminSection.entries.forEach { item ->
            val active = item == current
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(item) }.padding(horizontal = Spacing.md, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(item.icon, null, Modifier.size(20.dp), tint = tint)
                Text(stringResource(item.title), style = MaterialTheme.typography.labelLarge, color = tint)
            }
        }
    }
}
