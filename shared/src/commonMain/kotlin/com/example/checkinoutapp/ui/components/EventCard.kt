package com.example.checkinoutapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.domain.event.registrationAvailability
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.ui.format.formatDate
import com.example.checkinoutapp.ui.format.formatDayMonth
import com.example.checkinoutapp.ui.format.formatTimeRange
import com.example.checkinoutapp.ui.format.label
import com.example.checkinoutapp.ui.format.tone
import com.example.checkinoutapp.ui.theme.Spacing
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme
import com.example.checkinoutapp.ui.preview.previewEvent

private val COVER_PALETTES = listOf(
    Color(0xFF27357E) to Color(0xFF0B7A75),
    Color(0xFF5B2A86) to Color(0xFF27357E),
    Color(0xFF0B7A75) to Color(0xFF1F6F3F),
    Color(0xFF8A3B12) to Color(0xFF5B2A86),
    Color(0xFF1D4E89) to Color(0xFF0B7A75),
)

private fun categoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "workshop" -> Icons.Outlined.Biotech
    "talk" -> Icons.Outlined.RecordVoiceOver
    "competition" -> Icons.Outlined.EmojiEvents
    "training" -> Icons.Outlined.School
    "conference" -> Icons.Outlined.Groups
    "field trip" -> Icons.Outlined.Explore
    else -> Icons.Outlined.Science
}

/**
 * The event's cover. Generated from its category and title (a stable gradient and an icon), so
 * every event has a distinct, readable cover without anyone uploading artwork.
 */
@Composable
fun EventCover(event: ClubEvent, height: Dp, modifier: Modifier = Modifier, showDate: Boolean = true) {
    val palette = COVER_PALETTES[(event.category.hashCode() * 31 + event.title.length).mod(COVER_PALETTES.size)]
    Box(modifier.fillMaxWidth().height(height).background(Brush.linearGradient(listOf(palette.first, palette.second)))) {
        Icon(categoryIcon(event.category), null, Modifier.align(Alignment.BottomEnd).padding(Spacing.md).size(height * 0.55f), tint = Color.White.copy(alpha = 0.16f))
        if (showDate) {
            val (day, month) = formatDayMonth(event.startAt, event.timezone)
            Column(
                Modifier.align(Alignment.TopStart).padding(Spacing.md).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day, color = Color(0xFF12162B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text(month, color = Color(0xFF545B75), style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(Spacing.md).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.28f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(event.category, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun IconLine(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EventCard(event: ClubEvent, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val availability = event.registrationAvailability(now)
    ClubCard(modifier, onClick = onClick, padding = 0.dp) {
        EventCover(event, height = 132.dp)
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(event.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(event.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.xs))
            IconLine(Icons.Outlined.CalendarMonth, formatDate(event.startAt, event.timezone))
            IconLine(Icons.Outlined.Schedule, formatTimeRange(event.startAt, event.endAt, event.timezone))
            IconLine(Icons.Outlined.Place, event.location)
            Spacer(Modifier.height(Spacing.xs))
            StatusChip(availability.label(event), availability.tone())
        }
    }
}

/** Lays cards out in equal columns; the last row is padded so cards keep one width. */
@Composable
fun <T> CardGrid(items: List<T>, columns: Int, modifier: Modifier = Modifier, spacing: Dp = Spacing.lg, card: @Composable (T, Modifier) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { item -> card(item, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun FillBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Preview
@Composable
private fun EventCardPreview() {
    ClubTheme {
        EventCard(event = previewEvent, now = previewEvent.startAt - 2 * 60 * 60 * 1000L, onClick = {}, modifier = Modifier.padding(Spacing.lg))
    }
}
