package com.example.checkinoutapp.feature.eventDetails

import com.example.checkinoutapp.navigation.ticketRetrievalVisible
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.domain.event.RegistrationAvailability
import com.example.checkinoutapp.feature.home.ClubInfoViewModel
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ClubCard
import com.example.checkinoutapp.ui.components.ContentColumn
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.EventCover
import com.example.checkinoutapp.ui.components.LabeledValue
import com.example.checkinoutapp.ui.components.LoadingState
import com.example.checkinoutapp.ui.components.PublicScaffold
import com.example.checkinoutapp.ui.components.PublicTab
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatDateTime
import com.example.checkinoutapp.ui.format.formatTimeRange
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.format.tone
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.isWide
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme
import com.example.checkinoutapp.ui.preview.previewEventDetails
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun EventDetailsScreen(
    id: String,
    navigateBack: () -> Unit,
    navigateToHome: () -> Unit,
    navigateToEvents: () -> Unit,
    navigateToAuth: () -> Unit,
    navigateToRegistration: (String) -> Unit,
    navigateToFindTicket: (String) -> Unit,
) {
    val viewModel = koinViewModel<EventDetailsViewModel>()
    val clubInfoViewModel = koinViewModel<ClubInfoViewModel>()
    val details by viewModel.details.collectAsState()
    val settings by clubInfoViewModel.settings.collectAsState()

    PublicScaffold(settings, PublicTab.NONE, navigateToHome, navigateToEvents, navigateToAuth, navigateBack = navigateBack) {
        details.DisplayResult(
            onLoading = { LoadingState() },
            onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
            onSuccess = { content ->
                if (content == null) {
                    EmptyState(Icons.Outlined.EventBusy, stringResource(Res.string.details_not_found_title), body = stringResource(Res.string.details_not_found_body)) {
                        ClubButton(stringResource(Res.string.tk_back_to_events), navigateToEvents, kind = ButtonKind.Secondary)
                    }
                } else {
                    DetailsContent(content, navigateToRegistration = { navigateToRegistration(id) }, navigateToFindTicket = { navigateToFindTicket(id) })
                }
            },
        )
    }
}

@Composable
private fun DetailsContent(content: EventDetailsContent, navigateToRegistration: () -> Unit, navigateToFindTicket: () -> Unit) {
    val event = content.event
    ContentColumn {
        Spacer(Modifier.height(Spacing.xl))
        EventCover(event, height = if (isWide) 260.dp else 180.dp, modifier = Modifier.clip(MaterialTheme.shapes.large))
        Spacer(Modifier.height(Spacing.xl))
        val facts: @Composable () -> Unit = {
            ClubCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatusChip(content.availability.label(event), content.availability.tone())
                    LabeledValue(stringResource(Res.string.details_when), formatDate(event.startAt, event.timezone))
                    LabeledValue(stringResource(Res.string.details_time), formatTimeRange(event.startAt, event.endAt, event.timezone))
                    LabeledValue(stringResource(Res.string.details_where), event.location)
                    if (event.organizerName.isNotBlank()) LabeledValue(stringResource(Res.string.details_organizer), event.organizerName)
                    LabeledValue(
                        stringResource(Res.string.details_capacity),
                        if (event.capacity > 0) stringResource(Res.string.events_capacity, event.registrationCount, event.capacity)
                        else stringResource(Res.string.common_unlimited),
                    )
                    if (content.availability == RegistrationAvailability.NOT_YET_OPEN) {
                        LabeledValue(stringResource(Res.string.details_opens), formatDateTime(event.registrationOpenAt, event.timezone))
                    } else {
                        LabeledValue(stringResource(Res.string.details_deadline), formatDateTime(event.registrationCloseAt, event.timezone))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ClubButton(
                        stringResource(Res.string.details_register), navigateToRegistration, Modifier.fillMaxWidth(),
                        enabled = content.availability.canRegister, large = true,
                    )
                    if (ticketRetrievalVisible) {
                        ClubButton(stringResource(Res.string.details_find_ticket), navigateToFindTicket, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
                    }
                    Text(stringResource(Res.string.details_timezone_note, event.timezone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val about: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(event.title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(Res.string.details_about).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (isWide) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
                Column(Modifier.weight(1.6f)) { about() }
                Column(Modifier.weight(1f)) { facts() }
            }
        } else {
            about()
            Spacer(Modifier.height(Spacing.xl))
            facts()
        }
        Spacer(Modifier.padding(bottom = Spacing.xl))
    }
}

@Preview
@Composable
private fun EventDetailsPreview() {
    ClubTheme {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DetailsContent(content = previewEventDetails, navigateToRegistration = {}, navigateToFindTicket = {})
        }
    }
}
