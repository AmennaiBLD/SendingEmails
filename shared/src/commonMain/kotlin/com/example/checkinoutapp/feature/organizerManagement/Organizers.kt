package com.example.checkinoutapp.feature.organizerManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.UserRole
import com.example.checkinoutapp.domain.model.StaffField
import com.example.checkinoutapp.feature.admin.MutedText
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CellTwoLines
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.DialogFrame
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.FormField
import com.example.checkinoutapp.ui.components.LoadingState
import com.example.checkinoutapp.ui.components.LocalSnackbar
import com.example.checkinoutapp.ui.components.Notice
import com.example.checkinoutapp.ui.components.RowActions
import com.example.checkinoutapp.ui.components.SegmentedControl
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.StatusChip
import com.example.checkinoutapp.ui.components.TableColumn
import com.example.checkinoutapp.ui.components.TableHeader
import com.example.checkinoutapp.ui.components.TableRow
import com.example.checkinoutapp.ui.components.Tone
import com.example.checkinoutapp.ui.format.formatDateTime
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** "CHECK_IN" → "Check in": audit actions are stored as codes and shown as words. */
fun actionWords(action: String): String = action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
fun OrganizersScreen() {
    val viewModel = koinViewModel<OrganizersViewModel>()
    val staff by viewModel.staff.collectAsState()
    val ownUid by viewModel.ownUid.collectAsState()
    val busyUid by viewModel.busyUid.collectAsState()
    val snackbar = LocalSnackbar.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                OrganizersEvent.Created -> getString(Res.string.og_created)
                OrganizersEvent.Updated -> getString(Res.string.og_updated)
                is OrganizersEvent.Refused -> getString(Res.string.ev_refused, event.reason)
                OrganizersEvent.Failed -> getString(Res.string.common_action_failed)
            }
            snackbar.showSnackbar(message)
        }
    }
    StaffFormDialog(viewModel)
    ActivityDialog(viewModel)

    val columns = listOf(
        TableColumn(stringResource(Res.string.og_col_name), 1.8f), TableColumn(stringResource(Res.string.og_col_role), 0.9f),
        TableColumn(stringResource(Res.string.og_col_access), 0.8f), TableColumn(stringResource(Res.string.og_col_actions), 2.4f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ClubButton(stringResource(Res.string.og_new), viewModel::openCreate, icon = Icons.Outlined.PersonAdd)
        }
        staff.DisplayResult(
            onLoading = { SkeletonList() },
            onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
            onSuccess = { list ->
                if (list.isEmpty()) EmptyState(Icons.Outlined.Badge, stringResource(Res.string.og_empty))
                else Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TableHeader(columns)
                    list.forEach { user ->
                        val self = user.uid == ownUid
                        val enabled = busyUid == null
                        TableRow(
                            columns,
                            cells = listOf(
                                { CellTwoLines(user.fullName + if (self) "  (" + stringResource(Res.string.og_you) + ")" else "", user.email) },
                                { StatusChip(user.role.label(), if (user.role == UserRole.ADMIN) Tone.Info else Tone.Neutral) },
                                { StatusChip(stringResource(if (user.isActive) Res.string.og_active else Res.string.og_inactive), if (user.isActive) Tone.Success else Tone.Danger) },
                                {
                                    RowActions {
                                        ClubButton(stringResource(Res.string.og_activity), { viewModel.openActivity(user) }, kind = ButtonKind.Ghost)
                                        // Nobody can change their own role or lock themselves out.
                                        if (!self) {
                                            val nextRole = if (user.role == UserRole.ADMIN) UserRole.ORGANIZER else UserRole.ADMIN
                                            ClubButton(
                                                stringResource(if (nextRole == UserRole.ADMIN) Res.string.og_make_admin else Res.string.og_make_organizer),
                                                { viewModel.setAccess(user, role = nextRole) }, kind = ButtonKind.Ghost, enabled = enabled,
                                            )
                                            ClubButton(
                                                stringResource(if (user.isActive) Res.string.og_deactivate else Res.string.og_activate),
                                                { viewModel.setAccess(user, isActive = !user.isActive) }, kind = ButtonKind.Secondary, enabled = enabled, loading = busyUid == user.uid,
                                            )
                                        }
                                    }
                                },
                            ),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun StaffFormDialog(viewModel: OrganizersViewModel) {
    val state by viewModel.form.collectAsState()
    val form = state ?: return
    val editable = !form.saving
    DialogFrame(
        stringResource(Res.string.og_new), viewModel::closeForm,
        actions = {
            ClubButton(stringResource(Res.string.common_cancel), viewModel::closeForm, kind = ButtonKind.Ghost, enabled = editable)
            ClubButton(stringResource(Res.string.og_create), viewModel::create, loading = form.saving)
        },
    ) {
        FormField(form.fullName, { v -> viewModel.edit { it.copy(fullName = v.take(120)) } }, stringResource(Res.string.og_f_name), enabled = editable, error = stringResource(Res.string.og_err_name).takeIf { StaffField.FULL_NAME in form.invalid })
        FormField(form.email, { v -> viewModel.edit { it.copy(email = v.take(254)) } }, stringResource(Res.string.og_f_email), enabled = editable, keyboardType = KeyboardType.Email, error = stringResource(Res.string.rg_err_email).takeIf { StaffField.EMAIL in form.invalid })
        FormField(form.phone, { v -> viewModel.edit { it.copy(phone = v.take(24)) } }, stringResource(Res.string.og_f_phone), enabled = editable, keyboardType = KeyboardType.Phone)
        FormField(
            form.password, { v -> viewModel.edit { it.copy(password = v.take(128)) } }, stringResource(Res.string.og_f_password), enabled = editable, password = true,
            supporting = stringResource(Res.string.og_f_password_hint), error = stringResource(Res.string.og_err_password).takeIf { StaffField.PASSWORD in form.invalid },
        )
        Text(stringResource(Res.string.og_f_role), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedControl(listOf(UserRole.ORGANIZER, UserRole.ADMIN), form.role, label = { it.label() }, onSelect = { v -> viewModel.edit { it.copy(role = v) } })
        when (form.failure) {
            StaffFormFailure.EMAIL_IN_USE -> Notice(stringResource(Res.string.og_err_in_use), Tone.Warning)
            StaffFormFailure.FAILED -> Notice(stringResource(Res.string.common_action_failed), Tone.Danger)
            null -> Unit
        }
    }
}

@Composable
private fun ActivityDialog(viewModel: OrganizersViewModel) {
    val state by viewModel.activity.collectAsState()
    val activity = state ?: return
    DialogFrame(
        stringResource(Res.string.og_activity_title), viewModel::closeActivity, subtitle = activity.user.fullName, wide = true,
        actions = { ClubButton(stringResource(Res.string.common_close), viewModel::closeActivity, kind = ButtonKind.Secondary) },
    ) {
        when (val logs = activity.logs) {
            RequestState.Idle, RequestState.Loading -> LoadingState()
            is RequestState.Error -> ErrorState(logs.message)
            is RequestState.Success -> {
                if (logs.data.isEmpty()) MutedText(stringResource(Res.string.og_activity_none))
                logs.data.forEach { log ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Column(Modifier.weight(1f)) { CellTwoLines(actionWords(log.action), log.details) }
                        Text(formatDateTime(log.createdAt, "UTC") + " UTC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
