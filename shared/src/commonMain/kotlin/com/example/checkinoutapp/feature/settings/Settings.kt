package com.example.checkinoutapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.feature.admin.MutedText
import com.example.checkinoutapp.feature.admin.Panel
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen() {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.form.collectAsState()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                getString(
                    when (event) {
                        SettingsEvent.SAVED -> Res.string.common_saved
                        SettingsEvent.NAME_REQUIRED -> Res.string.st_name_required
                        SettingsEvent.FAILED -> Res.string.common_action_failed
                    },
                ),
            )
        }
    }
    state.DisplayResult(
        onLoading = { SkeletonList() },
        onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
        onSuccess = { form ->
            val s = form.settings
            val editable = !form.saving
            Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Panel(stringResource(Res.string.st_identity)) {
                    FormField(s.clubName, { v -> viewModel.edit { it.copy(clubName = v.take(80)) } }, stringResource(Res.string.st_club_name), enabled = editable)
                    FormField(s.tagline, { v -> viewModel.edit { it.copy(tagline = v.take(160)) } }, stringResource(Res.string.st_tagline), enabled = editable)
                    FormField(s.about, { v -> viewModel.edit { it.copy(about = v.take(2000)) } }, stringResource(Res.string.st_about), enabled = editable, singleLine = false, minLines = 3)
                    MutedText(stringResource(Res.string.st_logo_note))
                }
                Panel(stringResource(Res.string.st_contact)) {
                    FormField(s.contactEmail, { v -> viewModel.edit { it.copy(contactEmail = v.take(254)) } }, stringResource(Res.string.st_contact_email), enabled = editable, keyboardType = KeyboardType.Email)
                    FormField(s.contactPhone, { v -> viewModel.edit { it.copy(contactPhone = v.take(40)) } }, stringResource(Res.string.st_contact_phone), enabled = editable, keyboardType = KeyboardType.Phone)
                    FormField(s.address, { v -> viewModel.edit { it.copy(address = v.take(200)) } }, stringResource(Res.string.st_address), enabled = editable)
                    FormField(s.websiteUrl, { v -> viewModel.edit { it.copy(websiteUrl = v.take(300)) } }, stringResource(Res.string.st_website), enabled = editable, keyboardType = KeyboardType.Uri)
                    FormField(s.instagramUrl, { v -> viewModel.edit { it.copy(instagramUrl = v.take(300)) } }, stringResource(Res.string.st_instagram), enabled = editable, keyboardType = KeyboardType.Uri)
                    FormField(s.facebookUrl, { v -> viewModel.edit { it.copy(facebookUrl = v.take(300)) } }, stringResource(Res.string.st_facebook), enabled = editable, keyboardType = KeyboardType.Uri)
                    FormField(s.linkedinUrl, { v -> viewModel.edit { it.copy(linkedinUrl = v.take(300)) } }, stringResource(Res.string.st_linkedin), enabled = editable, keyboardType = KeyboardType.Uri)
                }
                Panel(stringResource(Res.string.st_attendance)) {
                    FormField(form.graceText, viewModel::setGrace, stringResource(Res.string.st_grace), supporting = stringResource(Res.string.st_grace_hint), enabled = editable, keyboardType = KeyboardType.Number)
                }
                ClubButton(stringResource(Res.string.common_save), viewModel::save, enabled = form.dirty, loading = form.saving, large = true)
            }
        },
    )
}
