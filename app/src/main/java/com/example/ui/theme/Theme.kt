package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class PGuardSemanticColors(
    val statusClear: Color,
    val statusClearContainer: Color,
    val statusClearBorder: Color,
    val onStatusClearContainer: Color,
    val statusAttention: Color,
    val statusAttentionContainer: Color,
    val statusAttentionBorder: Color,
    val onStatusAttentionContainer: Color,
    val statusDanger: Color,
    val statusDangerContainer: Color,
    val statusDangerBorder: Color,
    val onStatusDangerContainer: Color
)

private val LightSemanticColors = PGuardSemanticColors(
    statusClear = StatusClearLight,
    statusClearContainer = StatusClearContainerLight,
    statusClearBorder = StatusClearBorderLight,
    onStatusClearContainer = OnStatusClearContainerLight,
    statusAttention = StatusAttentionLight,
    statusAttentionContainer = StatusAttentionContainerLight,
    statusAttentionBorder = StatusAttentionBorderLight,
    onStatusAttentionContainer = OnStatusAttentionContainerLight,
    statusDanger = StatusDangerLight,
    statusDangerContainer = StatusDangerContainerLight,
    statusDangerBorder = StatusDangerBorderLight,
    onStatusDangerContainer = OnStatusDangerContainerLight
)

private val DarkSemanticColors = PGuardSemanticColors(
    statusClear = StatusClearDark,
    statusClearContainer = StatusClearContainerDark,
    statusClearBorder = StatusClearBorderDark,
    onStatusClearContainer = OnStatusClearContainerDark,
    statusAttention = StatusAttentionDark,
    statusAttentionContainer = StatusAttentionContainerDark,
    statusAttentionBorder = StatusAttentionBorderDark,
    onStatusAttentionContainer = OnStatusAttentionContainerDark,
    statusDanger = StatusDangerDark,
    statusDangerContainer = StatusDangerContainerDark,
    statusDangerBorder = StatusDangerBorderDark,
    onStatusDangerContainer = OnStatusDangerContainerDark
)

val LocalPGuardColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = TealOnPrimaryLight,
    primaryContainer = TealPrimaryContainerLight,
    onPrimaryContainer = TealOnPrimaryContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = SlateOnSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = SlateOnSecondaryContainerLight,
    background = LightBackground,
    onBackground = InkOnSurfaceLight,
    surface = LightSurface,
    onSurface = InkOnSurfaceLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariantLight,
    surfaceContainerLow = LightSurfaceContainerLow,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = StatusDangerLight,
    onError = Color.White,
    errorContainer = StatusDangerContainerLight,
    onErrorContainer = OnStatusDangerContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealOnPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    background = DarkBackground,
    onBackground = InkOnSurfaceDark,
    surface = DarkSurface,
    onSurface = InkOnSurfaceDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariantDark,
    surfaceContainerLow = DarkSurfaceContainerLow,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = StatusDangerDark,
    onError = Color(0xFF3E0B0E),
    errorContainer = StatusDangerContainerDark,
    onErrorContainer = OnStatusDangerContainerDark
)

val PGuardShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object PGuardTheme {
    val semanticColors: PGuardSemanticColors
        @Composable
        get() = LocalPGuardColors.current
}

@Composable
fun PGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalPGuardColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PGuardTypography,
            shapes = PGuardShapes,
            content = content
        )
    }
}
