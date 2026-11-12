package com.example.checkinoutapp.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WidthClass {
    Compact, Medium, Expanded, Large;

    companion object {
        fun of(width: Dp): WidthClass = when {
            width < 600.dp -> Compact
            width < 900.dp -> Medium
            width < 1280.dp -> Expanded
            else -> Large
        }
    }
}

/** Provided once at the app root; screens arrange themselves from it. */
val LocalWidthClass = compositionLocalOf { WidthClass.Compact }

val isCompact: Boolean
    @Composable get() = LocalWidthClass.current == WidthClass.Compact

val isWide: Boolean
    @Composable get() = LocalWidthClass.current >= WidthClass.Expanded

@Composable
fun pagePadding(): PaddingValues = when (LocalWidthClass.current) {
    WidthClass.Compact -> PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    WidthClass.Medium -> PaddingValues(horizontal = 24.dp, vertical = 20.dp)
    else -> PaddingValues(horizontal = 32.dp, vertical = 24.dp)
}

/** The public website's content column never grows past this, however wide the window. */
val ContentMaxWidth = 1120.dp
