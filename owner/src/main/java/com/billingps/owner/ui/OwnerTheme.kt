package com.billingps.owner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OwnerBg = Color(0xFF0B1220)
val OwnerPanel = Color(0xFF131C2E)
val OwnerPanel2 = Color(0xFF1A2540)
val OwnerBorder = Color(0xFF243148)
val OwnerGreen = Color(0xFF34D399)
val OwnerGreenDim = Color(0xFF10B981)
val OwnerPurple = Color(0xFF7C3AED)
val OwnerPurpleLight = Color(0xFFA78BFA)
val OwnerCyan = Color(0xFF38BDF8)
val OwnerAmber = Color(0xFFFBBF24)
val OwnerRed = Color(0xFFF87171)
val OwnerPink = Color(0xFFF472B6)
val OwnerText = Color(0xFFE2E8F0)
val OwnerMuted = Color(0xFF8FA3BF)

private val OwnerColors = darkColorScheme(
    primary = OwnerGreen,
    onPrimary = Color(0xFF06281D),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = OwnerGreen,
    secondary = OwnerCyan,
    onSecondary = Color(0xFF06283A),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = OwnerCyan,
    tertiary = OwnerPurpleLight,
    onTertiary = Color(0xFF1E1038),
    tertiaryContainer = Color(0xFF3B1D6E),
    onTertiaryContainer = OwnerPurpleLight,
    background = OwnerBg,
    onBackground = OwnerText,
    surface = OwnerPanel,
    onSurface = OwnerText,
    surfaceVariant = OwnerPanel2,
    onSurfaceVariant = OwnerMuted,
    error = OwnerRed,
    onError = Color(0xFF2A0808),
    errorContainer = Color(0xFF3B1111),
    onErrorContainer = OwnerRed,
    outline = OwnerBorder,
    outlineVariant = Color(0xFF1F2A40),
)

@Composable
fun OwnerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OwnerColors,
        content = content,
    )
}
