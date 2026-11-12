package com.example.checkinoutapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.resources.Res
import com.example.checkinoutapp.resources.common_retry
import com.example.checkinoutapp.ui.theme.LocalStatusColors
import com.example.checkinoutapp.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.EventBusy

enum class ButtonKind { Primary, Secondary, Ghost, Danger }

/** The one button. Screens never use raw Material buttons. */
@Composable
fun ClubButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    large: Boolean = false,
) {
    val padding = if (large) PaddingValues(horizontal = 24.dp, vertical = 14.dp) else PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    val sized = modifier.defaultMinSize(minHeight = if (large) 52.dp else 40.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = androidx.compose.material3.LocalContentColor.current)
            Spacer(Modifier.width(Spacing.sm))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val active = enabled && !loading
    when (kind) {
        ButtonKind.Primary -> Button(onClick, sized, enabled = active, shape = MaterialTheme.shapes.small, contentPadding = padding, content = content)
        ButtonKind.Danger -> Button(
            onClick, sized, enabled = active, shape = MaterialTheme.shapes.small, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), content = content,
        )
        ButtonKind.Secondary -> OutlinedButton(
            onClick, sized, enabled = active, shape = MaterialTheme.shapes.small, contentPadding = padding,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content,
        )
        ButtonKind.Ghost -> TextButton(onClick, sized, enabled = active, shape = MaterialTheme.shapes.small, contentPadding = padding, content = content)
    }
}

/** A flat card: surface colour, hairline border, no shadow. */
@Composable
fun ClubCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = Spacing.lg,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.medium).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

enum class Tone { Neutral, Info, Success, Warning, Danger }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    return when (tone) {
        Tone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
        Tone.Info -> scheme.primaryContainer to scheme.onPrimaryContainer
        Tone.Success -> status.successContainer to status.onSuccessContainer
        Tone.Warning -> status.warningContainer to status.onWarningContainer
        Tone.Danger -> scheme.errorContainer to scheme.onErrorContainer
    }
}

/** A small status label. Tone carries meaning; the text always says it too. */
@Composable
fun StatusChip(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val (container, content) = toneColors(tone)
    Row(
        modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = content)
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) action()
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

/** Placeholder rows while a list loads: the page keeps its shape instead of jumping. */
@Composable
fun SkeletonList(rows: Int = 4, modifier: Modifier = Modifier, rowHeight: Dp = 64.dp) {
    val pulse by rememberInfiniteTransition().animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(rows) {
            Box(Modifier.fillMaxWidth().height(rowHeight).alpha(pulse).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
        }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (onRetry != null) ClubButton(stringResource(Res.string.common_retry), onRetry, kind = ButtonKind.Secondary)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Spacing.sm))
            action()
        }
    }
}

/** An inline notice: a tinted box with an icon and a sentence. */
@Composable
fun Notice(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val (container, content) = toneColors(tone)
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(container).padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = content)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, hint: String? = null, tone: Tone = Tone.Info) {
    ClubCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            if (icon != null) {
                val (container, content) = toneColors(tone)
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(container), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(16.dp), tint = content)
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(value, style = MaterialTheme.typography.headlineMedium.merge(com.example.checkinoutapp.ui.theme.NumericStyle), color = MaterialTheme.colorScheme.onSurface)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A label above a value, for detail panels. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Preview
@Composable
private fun ButtonsPreview() {
    ClubTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ClubButton("Complete registration", {}, large = true)
            ClubButton("Primary", {})
            ClubButton("Secondary", {}, kind = ButtonKind.Secondary)
            ClubButton("Ghost", {}, kind = ButtonKind.Ghost)
            ClubButton("Cancel registration", {}, kind = ButtonKind.Danger)
            ClubButton("Saving", {}, loading = true)
        }
    }
}

@Preview
@Composable
private fun StatusChipsPreview() {
    ClubTheme {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusChip("Draft", Tone.Warning)
            StatusChip("Published", Tone.Success)
            StatusChip("Cancelled", Tone.Danger)
            StatusChip("Completed", Tone.Info)
            StatusChip("Archived", Tone.Neutral)
        }
    }
}

@Preview
@Composable
private fun StatCardPreview() {
    ClubTheme {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatCard("Registrations", "128", Modifier.weight(1f), icon = Icons.Outlined.Groups)
            StatCard("Median time spent", "2h 25m", Modifier.weight(1f), hint = "Last 180 days")
        }
    }
}

@Preview
@Composable
private fun StatesPreview() {
    ClubTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Notice("Incomplete sessions are not part of any average.", Tone.Warning)
            ErrorState("No connection. Check the network and try again.", onRetry = {})
            EmptyState(Icons.Outlined.EventBusy, "No upcoming events right now", body = "New events are announced here first.")
            SkeletonList(rows = 2)
        }
    }
}
