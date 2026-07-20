package com.billingps.aptv.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.billingps.aptv.R

enum class ThemeOption(val displayName: String, val palette: ThemeColors) {
    GAMING_DARK("Gaming Dark", GamingDarkPalette),
    CYBER_BLUE("Cyber Blue", CyberBluePalette),
    NEON_PURPLE("Neon Purple", NeonPurplePalette),
    CLASSIC_DARK("Classic Dark", ClassicDarkPalette),
    LIGHT_MODE("Light Mode", LightModePalette),
}

fun applyTheme(option: ThemeOption) {
    val p = option.palette
    DarkBackground = p.darkBackground
    DarkSurface = p.darkSurface
    DarkSurfaceV2 = p.darkSurfaceV2
    DarkSurfaceV3 = p.darkSurfaceV3
    NeonGreen = p.neonGreen
    NeonGreenDim = p.neonGreenDim
    NeonGreenGlow = p.neonGreenGlow
    NeonCyan = p.neonCyan
    NeonPurple = p.neonPurple
    NeonRed = p.neonRed
    NeonYellow = p.neonYellow
    NeonOrange = p.neonOrange
    GamersGreenDark = p.gamersGreenDark
    GamersGreenMedium = p.gamersGreenMedium
    TextPrimary = p.textPrimary
    TextSecondary = p.textSecondary
    TextDim = p.textDim
    TextOnGreen = p.textOnGreen
}

@Composable
fun colorSchemeFor(option: ThemeOption): ColorScheme {
    val p = option.palette
    return if (option == ThemeOption.LIGHT_MODE) {
        lightColorScheme(
            primary = p.neonGreen,
            onPrimary = p.textOnGreen,
            primaryContainer = p.gamersGreenDark,
            onPrimaryContainer = p.neonGreen,
            secondary = p.neonCyan,
            onSecondary = p.textOnGreen,
            secondaryContainer = Color(0xFFB3E5FC),
            onSecondaryContainer = p.neonCyan,
            tertiary = p.neonPurple,
            onTertiary = p.textOnGreen,
            background = p.darkBackground,
            onBackground = p.textPrimary,
            surface = p.darkSurface,
            onSurface = p.textPrimary,
            surfaceVariant = p.darkSurfaceV2,
            onSurfaceVariant = p.textSecondary,
            error = p.neonRed,
            onError = p.textPrimary,
            errorContainer = Color(0xFFFFCDD2),
            onErrorContainer = p.neonRed,
            outline = p.neonGreenDim,
            outlineVariant = p.darkSurfaceV3,
        )
    } else {
        darkColorScheme(
            primary = p.neonGreen,
            onPrimary = p.textOnGreen,
            primaryContainer = p.gamersGreenDark,
            onPrimaryContainer = p.neonGreen,
            secondary = p.neonCyan,
            onSecondary = p.textOnGreen,
            secondaryContainer = Color(0xFF003344),
            onSecondaryContainer = p.neonCyan,
            tertiary = p.neonPurple,
            onTertiary = p.textOnGreen,
            background = p.darkBackground,
            onBackground = p.textPrimary,
            surface = p.darkSurface,
            onSurface = p.textPrimary,
            surfaceVariant = p.darkSurfaceV2,
            onSurfaceVariant = p.textSecondary,
            error = p.neonRed,
            onError = p.textPrimary,
            errorContainer = Color(0xFF33000A),
            onErrorContainer = p.neonRed,
            outline = p.neonGreenDim,
            outlineVariant = p.darkSurfaceV3,
        )
    }
}

private val OrbitronFamily = FontFamily(
    Font(R.font.orbitron_variable)
)

@Composable
fun BillingPSTheme(
    themeOption: ThemeOption = ThemeOption.GAMING_DARK,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(themeOption) {
        applyTheme(themeOption)
    }
    MaterialTheme(
        colorScheme = colorSchemeFor(themeOption),
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
