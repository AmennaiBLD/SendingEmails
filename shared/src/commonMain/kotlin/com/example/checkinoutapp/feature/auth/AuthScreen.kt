package com.example.checkinoutapp.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.domain.model.ClubSettings
import com.example.checkinoutapp.domain.model.UserRole
import com.example.checkinoutapp.domain.session.SessionState
import com.example.checkinoutapp.feature.home.ClubInfoViewModel
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ClubCard
import com.example.checkinoutapp.ui.components.ClubMark
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.LoadingState
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Centres a narrow column on the page: sign-in and the "no access" explanation share it. */
@Composable
fun StaffCentered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/**
 * Staff sign-in. Once the session is active it moves on by role: administrators to the admin
 * panel, organizers to their events. The role only picks the screen; the backend checks access.
 */
@Composable
fun AuthScreen(
    navigateToOrganizerHome: () -> Unit,
    navigateToAdminPanel: () -> Unit,
    navigateToHome: () -> Unit,
) {
    val viewModel = koinViewModel<AuthViewModel>()
    val clubInfoViewModel = koinViewModel<ClubInfoViewModel>()
    val session by viewModel.session.collectAsState()
    val settings by clubInfoViewModel.settings.collectAsState()

    LaunchedEffect(session) {
        val active = session as? SessionState.Active
        if (active != null) {
            if (active.role == UserRole.ADMIN) navigateToAdminPanel() else navigateToOrganizerHome()
        }
    }

    when (val state = session) {
        is SessionState.Active -> CheckingAccess()
        is SessionState.Resolving -> CheckingAccess()
        SessionState.Unknown -> CheckingAccess()
        SessionState.SignedOut -> SignInForm(settings, viewModel, navigateToHome)
        is SessionState.NoAccess -> NoStaffAccess(state.account.email, viewModel::signOut, navigateToHome)
        is SessionState.Failed -> StaffCentered { ErrorState(state.message, onRetry = viewModel::signOut) }
    }
}

@Composable
private fun SignInForm(settings: ClubSettings, viewModel: AuthViewModel, navigateToHome: () -> Unit) {
    val form by viewModel.form.collectAsState()
    StaffCentered {
        ClubMark(settings.clubName, size = 56.dp)
        Spacer(Modifier.height(Spacing.lg))
        Text(stringResource(Res.string.auth_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(Res.string.auth_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xl))
        ClubCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                FormField(
                    form.email, viewModel::setEmail, stringResource(Res.string.auth_email), keyboardType = KeyboardType.Email, enabled = !form.busy,
                    error = stringResource(Res.string.auth_err_email).takeIf { form.error == SignInError.INVALID_EMAIL },
                )
                FormField(
                    form.password, viewModel::setPassword, stringResource(Res.string.auth_password), password = true, enabled = !form.busy, onDone = viewModel::signIn,
                    error = stringResource(Res.string.auth_err_password).takeIf { form.error == SignInError.PASSWORD_REQUIRED },
                )
                if (form.error == SignInError.FAILED) Notice(form.message.ifBlank { stringResource(Res.string.common_action_failed) }, Tone.Danger)
                ClubButton(stringResource(Res.string.auth_sign_in), viewModel::signIn, Modifier.fillMaxWidth(), loading = form.busy, large = true)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        ClubButton(stringResource(Res.string.auth_public_site), navigateToHome, kind = ButtonKind.Ghost)
    }
}

@Composable
private fun NoStaffAccess(email: String, onSignOut: () -> Unit, navigateToHome: () -> Unit) {
    StaffCentered {
        EmptyState(Icons.Outlined.Lock, stringResource(Res.string.auth_no_access_title), body = stringResource(Res.string.auth_no_access_body, email)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ClubButton(stringResource(Res.string.auth_sign_out), onSignOut, kind = ButtonKind.Secondary)
                ClubButton(stringResource(Res.string.auth_public_site), navigateToHome, kind = ButtonKind.Ghost)
            }
        }
    }
}

@Composable
private fun CheckingAccess() {
    StaffCentered {
        LoadingState()
        Text(stringResource(Res.string.auth_checking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
