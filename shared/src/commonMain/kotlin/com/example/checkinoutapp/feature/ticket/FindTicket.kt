package com.example.checkinoutapp.feature.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.feature.home.ClubInfoViewModel
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CheckRow
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ClubCard
import com.example.checkinoutapp.ui.components.ContentColumn
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.PublicScaffold
import com.example.checkinoutapp.ui.components.PublicTab
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FindTicketScreen(
    id: String,
    navigateBack: () -> Unit,
    navigateToHome: () -> Unit,
    navigateToEvents: () -> Unit,
    navigateToAuth: () -> Unit,
) {
    val viewModel = koinViewModel<FindTicketViewModel>()
    val clubInfoViewModel = koinViewModel<ClubInfoViewModel>()
    val state by viewModel.state.collectAsState()
    val ticket by viewModel.ticket.collectAsState()
    val settings by clubInfoViewModel.settings.collectAsState()

    PublicScaffold(settings, PublicTab.NONE, navigateToHome, navigateToEvents, navigateToAuth, navigateBack = navigateBack) {
        ContentColumn(maxWidth = 560.dp) {
            Spacer(Modifier.height(Spacing.xxl))
            val found = ticket
            if (found != null) {
                TicketConfirmation(found, settings.clubName, stringResource(Res.string.tk_replaced_title), navigateToEvents)
                return@ContentColumn
            }
            Text(stringResource(Res.string.ft_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(Res.string.ft_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xl))
            ClubCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    val errorText = when (state.error) {
                        FindTicketError.INVALID_EMAIL -> stringResource(Res.string.rg_err_email)
                        FindTicketError.INVALID_CODE -> stringResource(Res.string.ft_err_code)
                        FindTicketError.EXPIRED -> stringResource(Res.string.ft_err_expired)
                        FindTicketError.TOO_MANY_ATTEMPTS -> stringResource(Res.string.ft_err_attempts)
                        FindTicketError.NETWORK -> stringResource(Res.string.common_action_failed)
                        null -> null
                    }
                    if (state.step == FindTicketStep.EMAIL) {
                        FormField(
                            state.email, viewModel::setEmail, stringResource(Res.string.rg_email), keyboardType = KeyboardType.Email,
                            error = errorText.takeIf { state.error == FindTicketError.INVALID_EMAIL }, enabled = !state.busy, onDone = viewModel::sendCode,
                        )
                        if (state.error == FindTicketError.NETWORK) Notice(errorText!!, Tone.Danger)
                        ClubButton(stringResource(Res.string.ft_send_code), viewModel::sendCode, Modifier.fillMaxWidth(), loading = state.busy, large = true)
                    } else {
                        Notice(stringResource(Res.string.ft_code_sent), Tone.Info)
                        FormField(
                            state.code, viewModel::setCode, stringResource(Res.string.ft_code), keyboardType = KeyboardType.NumberPassword,
                            error = errorText, enabled = !state.busy, onDone = viewModel::confirm,
                        )
                        CheckRow(state.replace, viewModel::setReplace, stringResource(Res.string.ft_replace), enabled = !state.busy)
                        ClubButton(stringResource(Res.string.ft_confirm), viewModel::confirm, Modifier.fillMaxWidth(), loading = state.busy, large = true)
                        ClubButton(stringResource(Res.string.ft_change_email), viewModel::changeEmail, kind = ButtonKind.Ghost, enabled = !state.busy)
                    }
                }
            }
        }
    }
}
