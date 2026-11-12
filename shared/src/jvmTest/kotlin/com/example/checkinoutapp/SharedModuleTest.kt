package com.example.checkinoutapp

import androidx.lifecycle.SavedStateHandle
import com.example.checkinoutapp.di.sharedModule
import com.example.checkinoutapp.di.targetModule
import com.example.checkinoutapp.feature.adminDashboard.DashboardViewModel
import com.example.checkinoutapp.feature.analytics.AnalyticsViewModel
import com.example.checkinoutapp.feature.attendance.AdminAttendanceViewModel
import com.example.checkinoutapp.feature.attendance.AttendanceViewModel
import com.example.checkinoutapp.feature.auditLog.AuditLogViewModel
import com.example.checkinoutapp.feature.auth.AuthViewModel
import com.example.checkinoutapp.feature.eventDetails.EventDetailsViewModel
import com.example.checkinoutapp.feature.eventManagement.EventManagementViewModel
import com.example.checkinoutapp.feature.events.EventsViewModel
import com.example.checkinoutapp.feature.home.ClubInfoViewModel
import com.example.checkinoutapp.feature.home.HomeViewModel
import com.example.checkinoutapp.feature.notifications.NotificationsViewModel
import com.example.checkinoutapp.feature.organizerHome.OrganizerHomeViewModel
import com.example.checkinoutapp.feature.organizerManagement.OrganizersViewModel
import com.example.checkinoutapp.feature.participantManagement.ParticipantsViewModel
import com.example.checkinoutapp.feature.qrScanner.QrScannerViewModel
import com.example.checkinoutapp.feature.registration.RegistrationViewModel
import com.example.checkinoutapp.feature.reports.ReportsViewModel
import com.example.checkinoutapp.feature.settings.SettingsViewModel
import com.example.checkinoutapp.feature.ticket.FindTicketViewModel
import com.example.checkinoutapp.feature.ticket.TicketActionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Resolves every ViewModel from Koin, so a missing binding fails here and not in front of a user. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedModuleTest {
    @Test
    fun everyViewModelResolves() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = startKoin { modules(sharedModule, targetModule) }.koin
        try {
            listOf(
                ClubInfoViewModel::class, HomeViewModel::class, EventsViewModel::class, TicketActionsViewModel::class, AuthViewModel::class,
                OrganizerHomeViewModel::class, DashboardViewModel::class, EventManagementViewModel::class, ParticipantsViewModel::class,
                OrganizersViewModel::class, AdminAttendanceViewModel::class, AnalyticsViewModel::class, NotificationsViewModel::class,
                ReportsViewModel::class, AuditLogViewModel::class, SettingsViewModel::class,
            ).forEach { assertNotNull(koin.get<Any>(it), it.simpleName) }
            listOf(
                EventDetailsViewModel::class, RegistrationViewModel::class, FindTicketViewModel::class, QrScannerViewModel::class, AttendanceViewModel::class,
            ).forEach { assertNotNull(koin.get<Any>(it) { parametersOf(SavedStateHandle(mapOf("id" to "event-1"))) }, it.simpleName) }
        } finally {
            stopKoin()
            Dispatchers.resetMain()
        }
    }
}
