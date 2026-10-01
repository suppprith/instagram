package com.suppprith.dms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

object Dms {
    val colors: DmsColors
        @Composable @ReadOnlyComposable get() = LocalDmsColors.current

    val type: DmsType
        @Composable @ReadOnlyComposable get() = LocalDmsType.current

    /** 4 dp grid. */
    val screenPadding = 20.dp
    val rowShape = RoundedCornerShape(12.dp)
    val sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    const val MOTION_MS = 200
}

/** Follows the system light or dark setting. No in-app switch. */
@Composable
fun DmsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val type = DmsType()
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.bg, onBackground = colors.text,
            surface = colors.bg, onSurface = colors.text,
            surfaceVariant = colors.surface, onSurfaceVariant = colors.textMuted,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.surface,
            outline = colors.divider, outlineVariant = colors.divider,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.bg, onBackground = colors.text,
            surface = colors.bg, onSurface = colors.text,
            surfaceVariant = colors.surface, onSurfaceVariant = colors.textMuted,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.surface,
            outline = colors.divider, outlineVariant = colors.divider,
            error = colors.danger,
        )
    }
    val typography = Typography(
        headlineSmall = type.title,
        titleLarge = type.title,
        bodyLarge = type.body,
        bodyMedium = type.body,
        labelLarge = type.label,
        labelMedium = type.label,
        bodySmall = type.caption,
        labelSmall = type.caption,
    )
    val shapes = Shapes(small = Dms.rowShape, medium = Dms.rowShape, large = Dms.sheetShape, extraLarge = Dms.sheetShape)
    CompositionLocalProvider(LocalDmsColors provides colors, LocalDmsType provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
