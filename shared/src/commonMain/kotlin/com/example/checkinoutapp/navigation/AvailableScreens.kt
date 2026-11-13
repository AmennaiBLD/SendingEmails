package com.example.checkinoutapp.navigation

import com.example.checkinoutapp.feature.admin.AdminSection

// Which screens each build shows. The web build follows the GDG SPARK UP specification: its list
// (jsMain/.../navigation/AvailableScreens.js.kt) keeps the other screens commented out. Android,
// iOS and desktop show everything. No screen is deleted from the code.

/** The administrator sections in the sidebar / tab row, in order. */
expect val visibleAdminSections: List<AdminSection>

/** "Already registered? Get your ticket" (ticket by email code). */
expect val ticketRetrievalVisible: Boolean
