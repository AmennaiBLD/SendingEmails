package com.example.checkinoutapp.di

import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.data.repository.AttendanceRepositoryImpl
import com.example.checkinoutapp.data.repository.AuditRepositoryImpl
import com.example.checkinoutapp.data.repository.AuthRepositoryImpl
import com.example.checkinoutapp.data.repository.EventRepositoryImpl
import com.example.checkinoutapp.data.repository.NotificationRepositoryImpl
import com.example.checkinoutapp.data.repository.OrganizerRepositoryImpl
import com.example.checkinoutapp.data.repository.RegistrationRepositoryImpl
import com.example.checkinoutapp.data.repository.SettingsRepositoryImpl
import com.example.checkinoutapp.data.repository.UserRepositoryImpl
import com.example.checkinoutapp.domain.repository.AttendanceRepository
import com.example.checkinoutapp.domain.repository.AuditRepository
import com.example.checkinoutapp.domain.repository.AuthRepository
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.NotificationRepository
import com.example.checkinoutapp.domain.repository.OrganizerRepository
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.domain.repository.SettingsRepository
import com.example.checkinoutapp.domain.repository.UserRepository
import com.example.checkinoutapp.domain.session.SessionManager
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
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sharedModule = module {
    single<AppClock> { AppClock.System }
    single<AuthRepository> { AuthRepositoryImpl() }
    single<UserRepository> { UserRepositoryImpl() }
    single<SettingsRepository> { SettingsRepositoryImpl() }
    single<EventRepository> { EventRepositoryImpl() }
    single<OrganizerRepository> { OrganizerRepositoryImpl() }
    single<RegistrationRepository> { RegistrationRepositoryImpl() }
    single<AttendanceRepository> { AttendanceRepositoryImpl() }
    single<NotificationRepository> { NotificationRepositoryImpl() }
    single<AuditRepository> { AuditRepositoryImpl() }
    single<SessionManager> { SessionManager(get(), get()) }

    viewModelOf(::ClubInfoViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::EventsViewModel)
    viewModelOf(::EventDetailsViewModel)
    viewModelOf(::RegistrationViewModel)
    viewModelOf(::FindTicketViewModel)
    viewModelOf(::TicketActionsViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::OrganizerHomeViewModel)
    viewModelOf(::QrScannerViewModel)
    viewModelOf(::AttendanceViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::EventManagementViewModel)
    viewModelOf(::ParticipantsViewModel)
    viewModelOf(::OrganizersViewModel)
    viewModelOf(::AdminAttendanceViewModel)
    viewModelOf(::AnalyticsViewModel)
    viewModelOf(::NotificationsViewModel)
    viewModelOf(::ReportsViewModel)
    viewModelOf(::AuditLogViewModel)
    viewModelOf(::SettingsViewModel)
}
expect val targetModule: Module

fun initializeKoin(
    config: (KoinApplication.() -> Unit)? = null
) {
    startKoin {
        config?.invoke(this)
        modules(sharedModule, targetModule)
    }
}
