package com.suppprith.dms.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tokens from docs/design.md. One accent, used only for the single primary action and the active tab. */
@Immutable
data class DmsColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val textMuted: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
)

val LightColors = DmsColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F2F2),
    text = Color(0xFF0A0A0A),
    // Instagram uses #737373; one step darker so caption text passes AA on `surface` too.
    textMuted = Color(0xFF6B6B6B),
    divider = Color(0xFFDBDBDB),
    accent = Color(0xFF0095F6),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFED4956),
)

val DarkColors = DmsColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF121212),
    text = Color(0xFFF5F5F5),
    textMuted = Color(0xFFA8A8A8),
    divider = Color(0xFF262626),
    accent = Color(0xFF0095F6),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFED4956),
)

val LocalDmsColors = staticCompositionLocalOf { LightColors }
