package com.example.checkinoutapp.feature.ticket

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.domain.model.Ticket
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ClubCard
import com.example.checkinoutapp.ui.components.IconLine
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatTimeRange
import com.example.checkinoutapp.ui.theme.Spacing
import io.github.alexzhirkevich.qrose.QrCodePainter
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrOptions
import io.github.alexzhirkevich.qrose.options.solid
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * A participant's ticket: event facts around its QR code, drawn on the device from the ticket
 * code. Plain black on white with a quiet zone and no overlay, so any scanner reads it.
 */
@Composable
fun TicketCard(ticket: Ticket, clubName: String, modifier: Modifier = Modifier) {
    val actions = koinViewModel<TicketActionsViewModel>()
    val snackbar = LocalSnackbar.current
    val saving by actions.saving.collectAsState()
    var showCode by remember { mutableStateOf(false) }
    val qr = remember(ticket.ticketCode) { ticketQrPainter(ticket.ticketCode) }

    LaunchedEffect(actions) {
        actions.events.collect { event ->
            snackbar.showSnackbar(
                getString(
                    when (event) {
                        TicketEvent.DOWNLOADED -> Res.string.tk_downloaded
                        TicketEvent.DOWNLOAD_FAILED -> Res.string.common_action_failed
                    },
                ),
            )
        }
    }

    ClubCard(modifier.widthIn(max = 460.dp).fillMaxWidth(), padding = 0.dp) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF27357E), Color(0xFF0B7A75)))).padding(Spacing.lg)) {
            Text(stringResource(Res.string.tk_label).uppercase() + "  ·  " + clubName, style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFE9EC))
            Spacer(Modifier.height(Spacing.xs))
            Text(ticket.eventTitle, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.tk_participant).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ticket.participantName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                if (ticket.approved) {
                    StatusChip(stringResource(Res.string.tk_status_confirmed), Tone.Success, icon = Icons.Outlined.CheckCircle)
                } else {
                    StatusChip(stringResource(Res.string.tk_status_pending), Tone.Warning, icon = Icons.Outlined.HourglassTop)
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconLine(Icons.Outlined.CalendarMonth, formatDate(ticket.startAt, ticket.timezone))
                IconLine(Icons.Outlined.Schedule, formatTimeRange(ticket.startAt, ticket.endAt, ticket.timezone))
                IconLine(Icons.Outlined.Place, ticket.location)
            }
            // Always on white, in both themes: a dark-mode QR is harder for cameras to read.
            Box(Modifier.clip(MaterialTheme.shapes.medium).background(Color.White).padding(Spacing.sm), contentAlignment = Alignment.Center) {
                Image(qr, stringResource(Res.string.tk_qr_description), Modifier.size(232.dp), contentScale = ContentScale.Fit)
            }
            ClubButton(stringResource(Res.string.tk_download), { actions.download(ticket) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Download, loading = saving)
            ClubButton(stringResource(if (showCode) Res.string.tk_hide_code else Res.string.tk_show_code), { showCode = !showCode }, kind = ButtonKind.Ghost)
            if (showCode) {
                SelectionContainer {
                    Text(ticket.ticketCode, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(stringResource(Res.string.tk_code_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Black modules on white: readable by every camera, also when saved as an image. */
fun ticketQrPainter(ticketCode: String): QrCodePainter = QrCodePainter(
    data = ticketCode,
    options = QrOptions {
        colors {
            dark = QrBrush.solid(Color.Black)
            light = QrBrush.solid(Color.White)
        }
    },
)

/** The confirmation shown after a successful registration or ticket retrieval. */
@Composable
fun TicketConfirmation(ticket: Ticket, clubName: String, title: String, navigateToEvents: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            if (ticket.approved) stringResource(Res.string.tk_confirmed_body, ticket.participantName.substringBefore(' '))
            else stringResource(Res.string.tk_pending_body, ticket.participantName.substringBefore(' ')),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 520.dp),
        )
        if (ticket.approved) {
            TicketCard(ticket, clubName)
            Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (ticket.emailQueued) Notice(stringResource(Res.string.tk_sent_email), Tone.Info)
                if (ticket.whatsappQueued) Notice(stringResource(Res.string.tk_sent_whatsapp), Tone.Info)
                if (!ticket.emailQueued && !ticket.whatsappQueued) Notice(stringResource(Res.string.tk_save_hint), Tone.Info)
            }
        } else {
            // Waiting for approval: no QR yet. It is emailed once an administrator approves.
            Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Notice(stringResource(Res.string.tk_pending_note), Tone.Warning)
            }
        }
        ClubButton(stringResource(Res.string.tk_back_to_events), navigateToEvents, kind = ButtonKind.Secondary)
    }
}
