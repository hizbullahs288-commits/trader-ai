package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorOutlineSubtle = androidx.compose.ui.graphics.Color(0xFF1E293B)

private val TradePulseDarkColorScheme = darkColorScheme(
    primary = BullGreen,
    onPrimary = ObsidianBg,
    primaryContainer = BullGreenDark,
    onPrimaryContainer = BullGreen,
    secondary = CyanAccent,
    onSecondary = ObsidianBg,
    secondaryContainer = CyanDark,
    onSecondaryContainer = CyanAccent,
    tertiary = GoldAccent,
    onTertiary = ObsidianBg,
    tertiaryContainer = GoldDark,
    onTertiaryContainer = GoldAccent,
    error = BearRed,
    onError = TextPrimary,
    errorContainer = BearRedDark,
    onErrorContainer = BearRed,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = ElevatedSurface,
    surfaceContainerHighest = HighestSurface,
    outline = BorderSlate,
    outlineVariant = ColorOutlineSubtle
)

val TradePulseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TradePulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TradePulseDarkColorScheme,
        typography = TradePulseTypography,
        shapes = TradePulseShapes,
        content = content
    )
}
