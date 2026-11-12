package com.example.checkinoutapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.ui.theme.NumericStyle
import com.example.checkinoutapp.ui.theme.Spacing
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme

data class ChartBar(val label: String, val value: Float, val valueLabel: String = value.toInt().toString())

/**
 * A column chart for distributions (arrivals, durations, registrations per day). One series,
 * one colour; the tallest bar and both ends of the axis are labelled so the scale is readable
 * without a legend. [summary] is what a screen reader announces instead of the drawing.
 */
@Composable
fun ColumnChart(bars: List<ChartBar>, summary: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val max = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth().semantics { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth().height(170.dp)) {
            Column(Modifier.fillMaxHeight().width(30.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text(bars.maxByOrNull { it.value }?.valueLabel.orEmpty(), style = MaterialTheme.typography.labelSmall.merge(NumericStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall.merge(NumericStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.sm))
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val slot = size.width / bars.size.coerceAtLeast(1)
                val barWidth = (slot * 0.7f).coerceAtMost(48.dp.toPx())
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                bars.forEachIndexed { index, bar ->
                    val height = size.height * (bar.value / max)
                    if (height > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - height),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 38.dp, top = Spacing.xs)) {
            Text(bars.firstOrNull()?.label.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bars.size > 1) Text(bars.last().label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
    }
}

/** Horizontal bars for comparing named things (events, faculties): label, bar, value. */
@Composable
fun BarList(bars: List<ChartBar>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    val max = bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        bars.forEach { bar ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bar.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(bar.valueLabel, style = MaterialTheme.typography.titleSmall.merge(NumericStyle), color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth((bar.value / max).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
                }
            }
        }
    }
}

/** One bar split into labelled parts of a whole (attendance outcomes). */
@Composable
fun SegmentBar(parts: List<Triple<String, Int, Color>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            parts.filter { it.second > 0 }.forEach { (_, count, color) -> Box(Modifier.weight(count.toFloat() / total).fillMaxHeight().background(color)) }
        }
        parts.forEach { (label, count, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(count.toString(), style = MaterialTheme.typography.titleSmall.merge(NumericStyle), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Preview
@Composable
private fun ChartsPreview() {
    val bars = listOf(ChartBar("09:00", 4f), ChartBar("09:15", 11f), ChartBar("09:30", 7f), ChartBar("09:45", 2f))
    ClubTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ColumnChart(bars, summary = "Arrivals per 15 minutes")
            BarList(listOf(ChartBar("Computer Science", 18f), ChartBar("Physics", 9f), ChartBar("Mathematics", 4f)))
            SegmentBar(
                listOf(
                    Triple("Full attendance", 22, MaterialTheme.colorScheme.secondary),
                    Triple("Left early", 5, MaterialTheme.colorScheme.error),
                    Triple("Absent", 4, MaterialTheme.colorScheme.outline),
                ),
            )
        }
    }
}
