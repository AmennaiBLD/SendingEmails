package com.example.checkinoutapp.feature.registration

import com.example.checkinoutapp.firebase.FirebaseAppConfig
import com.example.checkinoutapp.navigation.ticketRetrievalVisible
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.example.checkinoutapp.domain.repository.RegistrationOutcome
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.DeliveryPreference
import com.example.checkinoutapp.domain.model.RegistrationField
import com.example.checkinoutapp.domain.repository.RegistrationRejection
import com.example.checkinoutapp.feature.eventDetails.EventDetailsViewModel
import com.example.checkinoutapp.feature.home.ClubInfoViewModel
import com.example.checkinoutapp.feature.ticket.TicketConfirmation
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CheckRow
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ClubCard
import com.example.checkinoutapp.ui.components.ContentColumn
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.IconLine
import com.example.checkinoutapp.ui.components.LoadingState
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.PublicScaffold
import com.example.checkinoutapp.ui.components.PublicTab
import com.example.checkinoutapp.ui.components.SegmentedControl
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatTimeRange
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.isCompact
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegistrationScreen(
    id: String,
    navigateBack: () -> Unit,
    navigateToHome: () -> Unit,
    navigateToEvents: () -> Unit,
    navigateToAuth: () -> Unit,
    navigateToFindTicket: (String) -> Unit,
) {
    val viewModel = koinViewModel<RegistrationViewModel>()
    val eventViewModel = koinViewModel<EventDetailsViewModel>()
    val clubInfoViewModel = koinViewModel<ClubInfoViewModel>()
    val details by eventViewModel.details.collectAsState()
    val state by viewModel.state.collectAsState()
    val ticket by viewModel.ticket.collectAsState()
    val settings by clubInfoViewModel.settings.collectAsState()

    PublicScaffold(settings, PublicTab.NONE, navigateToHome, navigateToEvents, navigateToAuth, navigateBack = navigateBack) {
        ContentColumn(maxWidth = 680.dp) {
            Spacer(Modifier.height(Spacing.xxl))
            val issued = ticket
            if (issued != null) {
                TicketConfirmation(
                    issued, settings.clubName,
                    stringResource(if (issued.approved) Res.string.tk_confirmed_title else Res.string.rg_submitted_title),
                    navigateToEvents,
                )
                return@ContentColumn
            }
            when (val loaded = details) {
                RequestState.Idle, RequestState.Loading -> LoadingState()
                is RequestState.Error -> ErrorState(stringResource(Res.string.common_load_failed) + " " + loaded.message)
                is RequestState.Success -> {
                    val content = loaded.data
                    if (content == null || !content.availability.canRegister) {
                        EmptyState(
                            Icons.Outlined.EventBusy, stringResource(Res.string.rg_unavailable_title),
                            body = content?.let { it.availability.label(it.event) } ?: stringResource(Res.string.details_not_found_body),
                        ) { if (ticketRetrievalVisible) ClubButton(stringResource(Res.string.details_find_ticket), { navigateToFindTicket(id) }, kind = ButtonKind.Secondary) }
                    } else {
                        RegistrationFormContent(content.event, state, viewModel, navigateToFindTicket = { navigateToFindTicket(id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationFormContent(event: ClubEvent, state: RegistrationFormState, viewModel: RegistrationViewModel, navigateToFindTicket: () -> Unit) {
    val form = state.form
    val editable = !state.saving
    @Composable fun error(field: RegistrationField, text: String): String? = if (field in state.invalid) text else null
    val required = stringResource(Res.string.rg_err_required)

    Text(stringResource(Res.string.rg_title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    Text(event.title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(Spacing.sm))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        IconLine(Icons.Outlined.CalendarMonth, formatDate(event.startAt, event.timezone))
        IconLine(Icons.Outlined.Schedule, formatTimeRange(event.startAt, event.endAt, event.timezone))
        IconLine(Icons.Outlined.Place, event.location)
    }
    Spacer(Modifier.height(Spacing.lg))
    Text(stringResource(Res.string.rg_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.lg))

    ClubCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FormField(form.fullName, viewModel::setFullName, stringResource(Res.string.rg_full_name), error = error(RegistrationField.FULL_NAME, stringResource(Res.string.rg_err_full_name)), enabled = editable)
            Pair2(
                { FormField(form.email, viewModel::setEmail, stringResource(Res.string.rg_email), it, error = error(RegistrationField.EMAIL, stringResource(Res.string.rg_err_email)), enabled = editable, keyboardType = KeyboardType.Email) },
                { FormField(form.phone, viewModel::setPhone, stringResource(Res.string.rg_phone), it, error = error(RegistrationField.PHONE, stringResource(Res.string.rg_err_phone)), supporting = stringResource(Res.string.rg_phone_hint), enabled = editable, keyboardType = KeyboardType.Phone) },
            )
            FormField(form.university, viewModel::setUniversity, stringResource(Res.string.rg_university), error = error(RegistrationField.UNIVERSITY, required), enabled = editable)
            Pair2(
                { FormField(form.faculty, viewModel::setFaculty, stringResource(Res.string.rg_faculty), it, error = error(RegistrationField.FACULTY, required), enabled = editable) },
                { FormField(form.department, viewModel::setDepartment, stringResource(Res.string.rg_department), it, error = error(RegistrationField.DEPARTMENT, required), enabled = editable) },
            )
            FormField(form.studentId, viewModel::setStudentId, stringResource(Res.string.rg_student_id), enabled = editable)
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    ClubCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(Res.string.rg_delivery_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            // WhatsApp is offered only once its sender is set up (whatsappDelivery in firebase/app-config.json):
            // otherwise a "WhatsApp only" participant would receive nothing.
            if (FirebaseAppConfig.WHATSAPP_DELIVERY == "true") {
                SegmentedControl(DeliveryPreference.entries, form.deliveryPreference, label = { it.label() }, onSelect = viewModel::setDelivery)
            } else {
                Text(stringResource(Res.string.rg_delivery_email_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (form.deliveryPreference.usesWhatsApp) {
                CheckRow(form.whatsappConsent, viewModel::setWhatsAppConsent, stringResource(Res.string.rg_whatsapp_consent), error = RegistrationField.WHATSAPP_CONSENT in state.invalid, enabled = editable)
                if (RegistrationField.WHATSAPP_CONSENT in state.invalid) Text(stringResource(Res.string.rg_err_consent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            CheckRow(form.privacyAccepted, viewModel::setPrivacy, stringResource(Res.string.rg_privacy), error = RegistrationField.PRIVACY in state.invalid, enabled = editable)
            if (RegistrationField.PRIVACY in state.invalid) Text(stringResource(Res.string.rg_err_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    when (val failure = state.failure) {
        null -> Unit
        RegistrationFailure.InvalidFields -> Notice(stringResource(Res.string.rg_fix_fields), Tone.Warning)
        RegistrationFailure.Network -> Notice(stringResource(Res.string.common_action_failed), Tone.Danger)
        is RegistrationFailure.Rejected -> {
            Notice(
                stringResource(
                    when (failure.reason) {
                        RegistrationRejection.ALREADY_REGISTERED -> Res.string.rg_rejected_already
                        RegistrationRejection.EVENT_FULL -> Res.string.rg_rejected_full
                        RegistrationRejection.REGISTRATION_CLOSED -> Res.string.rg_rejected_closed
                        RegistrationRejection.REGISTRATION_NOT_OPEN -> Res.string.rg_rejected_not_open
                        else -> Res.string.rg_rejected_unavailable
                    },
                ),
                Tone.Warning,
            )
            if (failure.reason == RegistrationRejection.ALREADY_REGISTERED && ticketRetrievalVisible) {
                ClubButton(stringResource(Res.string.details_find_ticket), navigateToFindTicket, kind = ButtonKind.Ghost)
            }
        }
    }
    Spacer(Modifier.height(Spacing.md))
    ClubButton(stringResource(Res.string.rg_submit), viewModel::submit, Modifier.fillMaxWidth(), loading = state.saving, large = true)
    Spacer(Modifier.height(Spacing.xl))
}

/** Two fields side by side on wide screens, stacked on phones. */
@Composable
private fun Pair2(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    if (isCompact) {
        first(Modifier)
        second(Modifier)
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}
