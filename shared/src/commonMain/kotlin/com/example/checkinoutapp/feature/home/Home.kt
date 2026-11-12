package com.example.checkinoutapp.feature.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.DisplayResult
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ButtonKind
import com.example.checkinoutapp.ui.components.CardGrid
import com.example.checkinoutapp.ui.components.ClubButton
import com.example.checkinoutapp.ui.components.ClubCard
import com.example.checkinoutapp.ui.components.ContentColumn
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.EventCard
import com.example.checkinoutapp.ui.components.PublicScaffold
import com.example.checkinoutapp.ui.components.PublicTab
import com.example.checkinoutapp.ui.components.SectionTitle
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.theme.LocalWidthClass
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.WidthClass
import com.example.checkinoutapp.ui.theme.isCompact
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun eventColumns(): Int = when (LocalWidthClass.current) {
    WidthClass.Compact -> 1
    WidthClass.Medium -> 2
    else -> 3
}

@Composable
fun HomeScreen(
    navigateToEvents: () -> Unit,
    navigateToEventDetails: (String) -> Unit,
    navigateToAuth: () -> Unit,
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val clubInfoViewModel = koinViewModel<ClubInfoViewModel>()
    val home by viewModel.home.collectAsState()
    val settings by clubInfoViewModel.settings.collectAsState()
    val now = AppClock.System.nowMillis()

    PublicScaffold(settings, PublicTab.HOME, navigateToHome = {}, navigateToEvents = navigateToEvents, navigateToAuth = navigateToAuth) {
        Hero(settings.tagline, settings.about, navigateToEvents)
        ContentColumn {
            Spacer(Modifier.height(Spacing.section))
            home.DisplayResult(
                onLoading = { SkeletonList(rows = 2, rowHeight = 220.dp) },
                onError = { ErrorState(stringResource(Res.string.common_load_failed) + " " + it) },
                onSuccess = { content ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.section)) {
                        if (content.featured.isNotEmpty()) {
                            EventSection(stringResource(Res.string.home_featured), stringResource(Res.string.home_featured_sub), content.featured, now, navigateToEventDetails, null)
                        }
                        if (content.upcoming.isEmpty()) {
                            ClubCard(Modifier.fillMaxWidth()) {
                                EmptyState(Icons.Outlined.EventBusy, stringResource(Res.string.home_empty_title), body = stringResource(Res.string.home_empty_body))
                            }
                        } else {
                            EventSection(stringResource(Res.string.home_upcoming), stringResource(Res.string.home_upcoming_sub), content.upcoming, now, navigateToEventDetails, navigateToEvents)
                        }
                        HowItWorks()
                        if (content.past.isNotEmpty()) EventSection(stringResource(Res.string.home_past), null, content.past, now, navigateToEventDetails, navigateToEvents)
                    }
                },
            )
        }
    }
}

@Composable
private fun Hero(tagline: String, about: String, onBrowse: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1B2660), Color(0xFF27357E), Color(0xFF0B7A75))))) {
        ContentColumn {
            Column(Modifier.padding(vertical = if (isCompact) Spacing.xxl else 72.dp).widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Text(stringResource(Res.string.home_hero_kicker).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFE9EC))
                Text(
                    tagline.ifBlank { stringResource(Res.string.home_hero_default_tagline) },
                    style = if (isCompact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall, color = Color.White,
                )
                Text(about.ifBlank { stringResource(Res.string.home_hero_default_about) }, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.86f))
                Spacer(Modifier.height(Spacing.xs))
                ClubButton(stringResource(Res.string.home_cta_browse), onBrowse, large = true, icon = Icons.AutoMirrored.Outlined.ArrowForward)
            }
        }
    }
}

@Composable
private fun EventSection(title: String, subtitle: String?, events: List<ClubEvent>, now: Long, navigateToEventDetails: (String) -> Unit, onSeeAll: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        SectionTitle(title, subtitle = subtitle, action = onSeeAll?.let { { ClubButton(stringResource(Res.string.home_see_all), it, kind = ButtonKind.Ghost) } })
        CardGrid(events, eventColumns()) { event, modifier -> EventCard(event, now, { navigateToEventDetails(event.id) }, modifier) }
    }
}

@Composable
private fun HowItWorks() {
    val steps = listOf(
        Res.string.home_how_1_title to Res.string.home_how_1_body, Res.string.home_how_2_title to Res.string.home_how_2_body,
        Res.string.home_how_3_title to Res.string.home_how_3_body, Res.string.home_how_4_title to Res.string.home_how_4_body,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        SectionTitle(stringResource(Res.string.home_how_title))
        CardGrid(steps.withIndex().toList(), if (isCompact) 1 else if (LocalWidthClass.current == WidthClass.Medium) 2 else 4) { step, modifier ->
            ClubCard(modifier) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Text((step.index + 1).toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(stringResource(step.value.first), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(step.value.second), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
