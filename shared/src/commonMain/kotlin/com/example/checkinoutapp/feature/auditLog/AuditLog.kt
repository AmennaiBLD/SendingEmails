package com.example.checkinoutapp.feature.auditLog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.feature.admin.MutedText
import com.example.checkinoutapp.feature.organizerManagement.actionWords
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.CellText
import com.example.checkinoutapp.ui.components.CellTwoLines
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.SearchField
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.components.TableColumn
import com.example.checkinoutapp.ui.components.TableHeader
import com.example.checkinoutapp.ui.components.TableRow
import com.example.checkinoutapp.ui.format.formatDateTime
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuditLogScreen() {
    val viewModel = koinViewModel<AuditLogViewModel>()
    val logs by viewModel.logs.collectAsState()
    val search by viewModel.search.collectAsState()
    val columns = listOf(
        TableColumn(stringResource(Res.string.au_col_when), 1.1f), TableColumn(stringResource(Res.string.au_col_actor), 1.1f),
        TableColumn(stringResource(Res.string.au_col_action), 1.2f), TableColumn(stringResource(Res.string.au_col_details), 2.6f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SearchField(search, viewModel::setSearch, stringResource(Res.string.au_search), Modifier.widthIn(max = 420.dp))
        MutedText(stringResource(Res.string.au_note))
        logs.DisplayResult(
            onLoading = { SkeletonList() },
            onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
            onSuccess = { list ->
                if (list.isEmpty()) EmptyState(Icons.Outlined.History, stringResource(Res.string.au_empty))
                else Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TableHeader(columns)
                    list.forEach { log ->
                        TableRow(
                            columns,
                            cells = listOf(
                                { CellText(formatDateTime(log.createdAt, "UTC") + " UTC", muted = true) },
                                { CellText(log.actorName.ifBlank { log.actorId }) },
                                { CellText(actionWords(log.action), strong = true) },
                                { CellTwoLines(log.details.ifBlank { log.entityType }, log.reason?.let { stringResource(Res.string.au_reason, it) }.orEmpty()) },
                            ),
                        )
                    }
                }
            },
        )
    }
}
