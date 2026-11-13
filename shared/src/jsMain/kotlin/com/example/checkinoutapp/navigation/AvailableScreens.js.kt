package com.example.checkinoutapp.navigation

import com.example.checkinoutapp.feature.admin.AdminSection

// The WEB version follows the GDG SPARK UP specification:
//   3.4 registration form · 3.3 QR check-in / check-out with a manual fallback ·
//   5 admin account for scanning and manual check-in · results as a spreadsheet (CSV).
// The screens it does not ask for are commented out here, for the web only. They stay in the code
// and in the Android, iOS and desktop apps. To bring one back on the web, uncomment its line.

actual val visibleAdminSections: List<AdminSection> = listOf(
    AdminSection.OVERVIEW,
    // AdminSection.EVENTS,         // not in the specification: events are managed in the app
    AdminSection.PARTICIPANTS,      // the registrations, to review and approve
    // AdminSection.ORGANIZERS,     // not in the specification: staff accounts are managed in the app
    AdminSection.ATTENDANCE,        // check-in / check-out records, manual corrections
    // AdminSection.ANALYTICS,      // "dashboard charts are not necessary": the CSV export is enough
    // AdminSection.NOTIFICATIONS,  // not in the specification
    AdminSection.REPORTS,           // CSV export ("the form results as an Excel file")
    // AdminSection.AUDIT,          // not in the specification
    // AdminSection.SETTINGS,       // not in the specification
)

// Ticket by email code: not in the specification (the QR is emailed after approval).
actual val ticketRetrievalVisible: Boolean = false
