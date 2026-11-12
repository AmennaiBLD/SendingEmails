package com.example.checkinoutapp.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.components.ClubCard
import com.example.checkinoutapp.ui.components.EmptyState
import com.example.checkinoutapp.ui.components.ErrorState
import com.example.checkinoutapp.ui.components.SelectField
import com.example.checkinoutapp.ui.components.SkeletonList
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme

@Composable
fun EventPicker(selection: EventSelection, modifier: Modifier = Modifier) {

    val events by selection.events.collectAsState()
    val selected by selection.selected.collectAsState()

    SelectField(
        label = stringResource(Res.string.common_select_event),
        selected = selected,
        options = events.getSuccessDataOrElse(emptyList()),
        optionLabel = { "${it.title}  ·  ${formatDate(it.startAt, it.timezone)}" },
        onSelect = { selection.select(it.id) },
        modifier = modifier.widthIn(max = 460.dp).fillMaxWidth(),
        placeholder = stringResource(Res.string.common_select_event_placeholder),
    )
}


@Composable
fun WithSelectedEvent(selection: EventSelection, content: @Composable (ClubEvent) -> Unit) {
    val events by selection.events.collectAsState()
    val selected by selection.selected.collectAsState()
    when (val state = events) {
        RequestState.Idle, RequestState.Loading -> SkeletonList()
        is RequestState.Error -> ErrorState(stringResource(Res.string.common_load_failed) + " " + state.message)
        is RequestState.Success -> {
            val event = selected
            if (event == null) EmptyState(Icons.Outlined.EventBusy, stringResource(Res.string.common_no_events_title), body = stringResource(Res.string.adm_no_event_body))
            else content(event)
        }
    }
}

@Composable
fun Panel(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    ClubCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) action()
        }
        Spacer(Modifier.height(Spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
    }
}

@Composable
fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Preview
@Composable
private fun PanelPreview() {
    ClubTheme {
        Panel(title = "Happening now", subtitle = "Events with people checked in") {
            MutedText("No one is checked in to any event right now.")
        }
    }
}
