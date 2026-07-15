package com.billingps.aptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.billingps.aptv.R

private val OrbitronFamily = FontFamily(
    Font(R.font.orbitron_variable)
)

private val DarkGamerScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = TextOnGreen,
    primaryContainer = GamersGreenDark,
    onPrimaryContainer = NeonGreen,

    secondary = NeonCyan,
    onSecondary = TextOnGreen,
    secondaryContainer = Color(0xFF003344),
    onSecondaryContainer = NeonCyan,

    tertiary = NeonPurple,
    onTertiary = TextOnGreen,

    background = DarkBackground,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceV2,
    onSurfaceVariant = TextSecondary,

    error = NeonRed,
    onError = TextPrimary,
    errorContainer = Color(0xFF33000A),
    onErrorContainer = NeonRed,

    outline = NeonGreenDim,
    outlineVariant = DarkSurfaceV3,
)

@Composable
fun BillingPSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkGamerScheme,
        typography = androidx.compose.material3.Typography(
            displayLarge = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 1.sp),
            displayMedium = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.5.sp),
            headlineLarge = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.5.sp),
            headlineMedium = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.3.sp),
            titleLarge = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.3.sp),
            titleMedium = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.3.sp),
            titleSmall = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 10.sp),
            labelLarge = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
            labelMedium = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp),
            labelSmall = TextStyle(fontFamily = OrbitronFamily, fontWeight = FontWeight.Medium, fontSize = 8.sp, letterSpacing = 0.5.sp),
        ),
        content = content,
    )
}
