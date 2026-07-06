package com.hush.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = PlumMist,
    onSecondary = OnPrimaryLight,
    secondaryContainer = PrimaryContainerLight,
    onSecondaryContainer = OnPrimaryContainerLight,
    tertiary = HarborTeal,
    background = DawnLinen,
    surface = DawnLinen,
    surfaceVariant = DawnCard,
    surfaceContainerLowest = DawnContainerLowest,
    surfaceContainerLow = DawnContainerLow,
    surfaceContainer = DawnContainer,
    surfaceContainerHigh = DawnContainerHigh,
    surfaceContainerHighest = DawnContainerHighest,
    onBackground = DawnInk,
    onSurface = DawnInk,
    onSurfaceVariant = DawnInkMuted,
    outline = DawnHairline,
    outlineVariant = DawnHairline,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = PlumMist,
    onSecondary = OnPrimaryDark,
    secondaryContainer = PrimaryContainerDark,
    onSecondaryContainer = OnPrimaryContainerDark,
    tertiary = HarborTeal,
    background = MidnightSky,
    surface = MidnightSky,
    surfaceVariant = MidnightCard,
    surfaceContainerLowest = MidnightContainerLowest,
    surfaceContainerLow = MidnightContainerLow,
    surfaceContainer = MidnightContainer,
    surfaceContainerHigh = MidnightContainerHigh,
    surfaceContainerHighest = MidnightContainerHighest,
    onBackground = MidnightInk,
    onSurface = MidnightInk,
    onSurfaceVariant = MidnightInkMuted,
    outline = MidnightHairline,
    outlineVariant = MidnightHairline,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

/** Soft, generous corner language — everything sits like a river stone. */
private val HushShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = HushShapes,
        content = content
    )
}
