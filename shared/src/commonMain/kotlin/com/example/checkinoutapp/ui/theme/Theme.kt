package com.example.checkinoutapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.checkinoutapp.resources.Res
import com.example.checkinoutapp.resources.bbh_bogle_regular
import org.jetbrains.compose.resources.Font

// Palette: deep indigo for structure, teal for action and "present", amber for attention.
private val Indigo = Color(0xFF27357E)
private val Teal = Color(0xFF0B7A75)

private val LightColors = lightColorScheme(
    primary = Indigo, onPrimary = Color.White, primaryContainer = Color(0xFFE1E6FB), onPrimaryContainer = Color(0xFF141C4D),
    secondary = Teal, onSecondary = Color.White, secondaryContainer = Color(0xFFD5F1EE), onSecondaryContainer = Color(0xFF05403D),
    tertiary = Color(0xFFA1530B), onTertiary = Color.White, tertiaryContainer = Color(0xFFFDE9CF), onTertiaryContainer = Color(0xFF4A2503),
    error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFFBE4E2), onErrorContainer = Color(0xFF5C100B),
    background = Color(0xFFF5F6FA), onBackground = Color(0xFF12162B),
    surface = Color.White, onSurface = Color(0xFF12162B),
    surfaceVariant = Color(0xFFECEEF5), onSurfaceVariant = Color(0xFF545B75),
    surfaceContainer = Color(0xFFF0F2F8), surfaceContainerHigh = Color(0xFFE8EBF3), surfaceContainerLow = Color(0xFFF8F9FC),
    outline = Color(0xFF8A90A6), outlineVariant = Color(0xFFDCE0EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEBBFF), onPrimary = Color(0xFF141C4D), primaryContainer = Color(0xFF2B3877), onPrimaryContainer = Color(0xFFE1E6FB),
    secondary = Color(0xFF6FD6CD), onSecondary = Color(0xFF003734), secondaryContainer = Color(0xFF0D4F4B), onSecondaryContainer = Color(0xFFBDF0EA),
    tertiary = Color(0xFFF5BE7C), onTertiary = Color(0xFF452400), tertiaryContainer = Color(0xFF5E3506), onTertiaryContainer = Color(0xFFFDE9CF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF5C100B), errorContainer = Color(0xFF7A1D16), onErrorContainer = Color(0xFFFBE4E2),
    background = Color(0xFF0C1020), onBackground = Color(0xFFE6E8F2),
    surface = Color(0xFF141930), onSurface = Color(0xFFE6E8F2),
    surfaceVariant = Color(0xFF1E2542), onSurfaceVariant = Color(0xFFB3B9D0),
    surfaceContainer = Color(0xFF181E38), surfaceContainerHigh = Color(0xFF212848), surfaceContainerLow = Color(0xFF10152A),
    outline = Color(0xFF7C84A3), outlineVariant = Color(0xFF2B3354),
)

/** Meaning colours that Material's scheme has no slot for. */
@Immutable
data class StatusColors(
    val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val warningContainer: Color, val onWarningContainer: Color,
)

private val LightStatus = StatusColors(
    success = Color(0xFF14804A), onSuccess = Color.White, successContainer = Color(0xFFDDF5E7), onSuccessContainer = Color(0xFF0A4A2A),
    warning = Color(0xFFA1530B), warningContainer = Color(0xFFFDE9CF), onWarningContainer = Color(0xFF4A2503),
)
private val DarkStatus = StatusColors(
    success = Color(0xFF6FDDA0), onSuccess = Color(0xFF00391C), successContainer = Color(0xFF0F4A2B), onSuccessContainer = Color(0xFFC7F2D8),
    warning = Color(0xFFF5BE7C), warningContainer = Color(0xFF5E3506), onWarningContainer = Color(0xFFFDE9CF),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val section = 56.dp
}

private val ClubShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

private val Base = Typography()
private val ClubTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = 0.5.sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = 0.4.sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.3.sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.4.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.4.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

/** Tabular figures keep columns of times and counts aligned. */
val NumericStyle = TextStyle(fontFeatureSettings = "tnum")

/**
 * BBH Bogle (Open Font License, docs/BBH_BOGLE_OFL.txt). The family ships one weight, so the same
 * file is registered for every weight the app asks for: the type keeps its real shapes and is
 * never thickened artificially. It covers basic Latin; other scripts fall back to the system font.
 */
@Composable
fun BbhBogleFont(): FontFamily = FontFamily(
    Font(Res.font.bbh_bogle_regular, FontWeight.Normal),
    Font(Res.font.bbh_bogle_regular, FontWeight.Medium),
    Font(Res.font.bbh_bogle_regular, FontWeight.SemiBold),
    Font(Res.font.bbh_bogle_regular, FontWeight.Bold),
)

private fun Typography.withFont(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun ClubTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = ClubTypography.withFont(BbhBogleFont()),
            shapes = ClubShapes,
            content = content
        )
    }
}
