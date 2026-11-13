package com.example.checkinoutapp.ui.components

import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.checkinoutapp.domain.model.ClubSettings
import com.example.checkinoutapp.resources.*
import com.example.checkinoutapp.ui.theme.ContentMaxWidth
import com.example.checkinoutapp.ui.theme.Spacing
import com.example.checkinoutapp.ui.theme.isCompact
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.checkinoutapp.ui.theme.ClubTheme

/** The app-wide snackbar, provided at the root so any screen can report "saved" or "failed". */
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No snackbar host") }

enum class PublicTab { HOME, EVENTS, NONE }

/**
 * The club's mark. A placeholder built from the club name's initials until the real logo is
 * supplied; replace this one composable to brand the whole product.
 */
@Composable
fun ClubMark(clubName: String, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val initials = clubName.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "SC" }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 3.4f))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, style = if (size > 44.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge)
    }
}

/** Centres page content in a column that never exceeds [maxWidth]. */
@Composable
fun ContentColumn(modifier: Modifier = Modifier, maxWidth: Dp = ContentMaxWidth, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = if (isCompact) Spacing.lg else Spacing.xxl), content = content)
    }
}

/** Header, scrolling content and footer of every public page. */
@Composable
fun PublicScaffold(
    settings: ClubSettings,
    tab: PublicTab,
    navigateToHome: () -> Unit,
    navigateToEvents: () -> Unit,
    navigateToAuth: () -> Unit,
    navigateBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            ContentColumn {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (navigateBack != null) {
                        Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = navigateBack).padding(Spacing.sm)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(Res.string.common_back), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Row(Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = navigateToHome).padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        ClubMark(settings.clubName)
                        if (!isCompact || navigateBack == null) {
                            Spacer(Modifier.size(Spacing.md))
                            Text(settings.clubName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    NavLink(stringResource(Res.string.nav_home), tab == PublicTab.HOME, navigateToHome)
                    NavLink(stringResource(Res.string.nav_events), tab == PublicTab.EVENTS, navigateToEvents)
                    if (!isCompact) {
                        ClubButton(stringResource(Res.string.nav_staff), navigateToAuth, kind = ButtonKind.Secondary)
                    } else {
                        // Phones: a sign-in icon, so organizers do not have to scroll to the footer.
                        IconButton(onClick = navigateToAuth) {
                            Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = stringResource(Res.string.nav_staff), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // The content takes at least the viewport, so a short page still ends with the footer at the bottom.
                Column(Modifier.fillMaxWidth().heightIn(min = viewport - 150.dp), content = content)
                PublicFooter(settings, navigateToAuth)
            }
        }
    }
}

@Composable
private fun NavLink(text: String, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PublicFooter(settings: ClubSettings, navigateToAuth: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = Spacing.section).background(MaterialTheme.colorScheme.surface)) {
        ContentColumn {
            Spacer(Modifier.height(Spacing.xl))
            val contacts = listOf(settings.contactEmail, settings.contactPhone, settings.address).filter { it.isNotBlank() }
            val links = listOf(settings.websiteUrl, settings.instagramUrl, settings.facebookUrl, settings.linkedinUrl).filter { it.isNotBlank() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClubMark(settings.clubName, size = 28.dp)
                        Spacer(Modifier.size(Spacing.sm))
                        Text(settings.clubName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(stringResource(Res.string.footer_rights), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isCompact) ClubButton(stringResource(Res.string.nav_staff), navigateToAuth, kind = ButtonKind.Ghost)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(Res.string.footer_contact).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (contacts.isEmpty() && links.isEmpty()) {
                        Text(stringResource(Res.string.footer_contact_placeholder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    (contacts + links).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Preview
@Composable
private fun PublicScaffoldPreview() {
    ClubTheme {
        PublicScaffold(
            settings = ClubSettings(contactEmail = "contact@example.org", address = "University campus"),
            tab = PublicTab.EVENTS,
            navigateToHome = {},
            navigateToEvents = {},
            navigateToAuth = {},
        ) {
            ContentColumn { Text("Page content", Modifier.padding(vertical = Spacing.xl)) }
        }
    }
}
