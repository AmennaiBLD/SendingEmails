package com.example.checkinoutapp.feature.events

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.domain.event.EventTimeFilter
import com.example.checkinoutapp.feature.home.ClubInfoViewModel
import com.example.checkinoutapp.feature.home.eventColumns
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CardGrid
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ContentColumn
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.EventCard
import com.example.checkinoutapp.ui.components.PublicScaffold
import com.example.checkinoutapp.ui.components.PublicTab
import com.example.checkinoutapp.ui.components.SearchField
import com.example.checkinoutapp.ui.components.SegmentedControl
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EventsScreen(
    navigateToHome: () -> Unit,
    navigateToEventDetails: (String) -> Unit,
    navigateToAuth: () -> Unit,
) {
    val viewModel = koinViewModel<EventsViewModel>()
    val clubInfoViewModel = koinViewModel<ClubInfoViewModel>()
    val events by viewModel.events.collectAsState()
    val query by viewModel.query.collectAsState()
    val settings by clubInfoViewModel.settings.collectAsState()

    PublicScaffold(settings, PublicTab.EVENTS, navigateToHome = navigateToHome, navigateToEvents = {}, navigateToAuth = navigateToAuth) {
        ContentColumn {
            Spacer(Modifier.height(Spacing.xxl))
            Text(stringResource(Res.string.events_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(Res.string.events_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xl))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SearchField(query.search, viewModel::setSearch, stringResource(Res.string.events_search), Modifier.widthIn(min = 220.dp, max = 340.dp))
                SegmentedControl(
                    EventTimeFilter.entries, query.time,
                    label = {
                        stringResource(
                            when (it) {
                                EventTimeFilter.UPCOMING -> Res.string.events_filter_upcoming
                                EventTimeFilter.PAST -> Res.string.events_filter_past
                                EventTimeFilter.ALL -> Res.string.events_filter_all
                            },
                        )
                    },
                    onSelect = viewModel::setTime,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            events.DisplayResult(
                onLoading = { SkeletonList(rows = 2, rowHeight = 220.dp) },
                onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
                onSuccess = { content ->
                    Column {
                        if (content.categories.size > 1) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                FilterChip(query.category == null, { viewModel.setCategory(null) }, { Text(stringResource(Res.string.events_all_categories)) })
                                content.categories.forEach { category ->
                                    FilterChip(query.category == category, { viewModel.setCategory(category) }, { Text(category) })
                                }
                            }
                            Spacer(Modifier.height(Spacing.lg))
                        }
                        if (content.events.isEmpty()) {
                            EmptyState(Icons.Outlined.SearchOff, stringResource(Res.string.events_empty_title), body = stringResource(Res.string.events_empty_body))
                        } else {
                            CardGrid(content.events, eventColumns()) { event, modifier -> EventCard(event, content.now, { navigateToEventDetails(event.id) }, modifier) }
                        }
                        if (content.canLoadMore) {
                            Spacer(Modifier.height(Spacing.xl))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                ClubButton(stringResource(Res.string.events_load_more), viewModel::loadMore, kind = ButtonKind.Secondary)
                            }
                        }
                    }
                },
            )
        }
    }
}
