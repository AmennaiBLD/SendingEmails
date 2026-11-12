package com.example.checkinoutapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.isCompact
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme

/** One table column: a header and the share of the row's width it takes. */
data class TableColumn(val title: String, val weight: Float)

/**
 * A table row that adapts: side-by-side cells on wide screens, a stacked card on phones
 * (first cell as the title, the rest as "label  value" lines). [cells] must match [columns].
 */
@Composable
fun TableRow(columns: List<TableColumn>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, cells: List<@Composable () -> Unit>) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    if (isCompact) {
        ClubCard(modifier.fillMaxWidth(), onClick = onClick, padding = Spacing.md) {
            cells.firstOrNull()?.invoke()
            cells.drop(1).forEachIndexed { index, cell ->
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(columns.getOrNull(index + 1)?.title.orEmpty(), Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(0.58f)) { cell() }
                }
            }
        }
    } else {
        Column(modifier.fillMaxWidth().then(clickable)) {
            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                cells.forEachIndexed { index, cell -> Box(Modifier.weight(columns.getOrNull(index)?.weight ?: 1f)) { cell() } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun TableHeader(columns: List<TableColumn>, modifier: Modifier = Modifier) {
    if (isCompact) return
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        columns.forEach { column ->
            Text(column.title.uppercase(), Modifier.weight(column.weight), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CellText(text: String, strong: Boolean = false, muted: Boolean = false) {
    Text(
        text,
        style = (if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium).merge(com.example.checkinoutapp.ui.theme.NumericStyle),
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

/** Two lines in one cell: a name and a quieter detail under it. */
@Composable
fun CellTwoLines(primary: String, secondary: String) {
    Column {
        Text(primary, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (secondary.isNotBlank()) Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RowActions(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Preview
@Composable
private fun TablePreview() {
    val columns = listOf(TableColumn("Participant", 1.6f), TableColumn("Check-in", 1.2f), TableColumn("Time", 0.7f))
    ClubTheme {
        Column {
            TableHeader(columns)
            TableRow(columns, cells = listOf({ CellTwoLines("Ahmed Benali", "AI") }, { CellText("14 Nov 2026, 09:15 AM") }, { CellText("3h 30m", strong = true) }))
            TableRow(columns, cells = listOf({ CellTwoLines("Lina Haddad", "Optics") }, { CellText("14 Nov 2026, 09:20 AM") }, { CellText("—", muted = true) }))
        }
    }
}
