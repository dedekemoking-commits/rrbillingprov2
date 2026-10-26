package com.billingps.aptv.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

var DarkBackground by mutableStateOf(Color(0xFF0A0A0A))
var DarkSurface by mutableStateOf(Color(0xFF1A1A1A))
var DarkSurfaceV2 by mutableStateOf(Color(0xFF222222))
var DarkSurfaceV3 by mutableStateOf(Color(0xFF2A2A2A))

var NeonGreen by mutableStateOf(Color(0xFF39FF14))
var NeonGreenDim by mutableStateOf(Color(0xFF1A8C0A))
var NeonGreenGlow by mutableStateOf(Color(0x8839FF14))

var NeonCyan by mutableStateOf(Color(0xFF00E5FF))
var NeonPurple by mutableStateOf(Color(0xFFBB00FF))
var NeonRed by mutableStateOf(Color(0xFFFF1744))
var NeonYellow by mutableStateOf(Color(0xFFFFEA00))
var NeonOrange by mutableStateOf(Color(0xFFFF6D00))

var GamersGreenDark by mutableStateOf(Color(0xFF0D2818))
var GamersGreenMedium by mutableStateOf(Color(0xFF144D2B))

var TextPrimary by mutableStateOf(Color(0xFFF0F0F0))
var TextSecondary by mutableStateOf(Color(0xFFAAAAAA))
var TextDim by mutableStateOf(Color(0xFF666666))
var TextOnGreen by mutableStateOf(Color(0xFF0A0A0A))

data class BackgroundColors(
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceV2: Color,
    val darkSurfaceV3: Color,
)

enum class BackgroundOption(val displayName: String, val colors: BackgroundColors) {
    AMOLED_BLACK("AMOLED Black", BackgroundColors(
        darkBackground = Color(0xFF000000),
        darkSurface = Color(0xFF161616),
        darkSurfaceV2 = Color(0xFF1E1E1E),
        darkSurfaceV3 = Color(0xFF262626),
    )),
    GITHUB_DARK("GitHub Dark", BackgroundColors(
        darkBackground = Color(0xFF0D1117),
        darkSurface = Color(0xFF161B22),
        darkSurfaceV2 = Color(0xFF1C2128),
        darkSurfaceV3 = Color(0xFF21262D),
    )),
    ELEGANT_DARK("Elegant Dark", BackgroundColors(
        darkBackground = Color(0xFF0F0F0F),
        darkSurface = Color(0xFF1C1C1C),
        darkSurfaceV2 = Color(0xFF242424),
        darkSurfaceV3 = Color(0xFF2C2C2C),
    )),
    SLATE_DARK("Slate Dark", BackgroundColors(
        darkBackground = Color(0xFF111318),
        darkSurface = Color(0xFF1C1E24),
        darkSurfaceV2 = Color(0xFF252830),
        darkSurfaceV3 = Color(0xFF2D3038),
    )),
    DEEP_MIDNIGHT("Deep Midnight", BackgroundColors(
        darkBackground = Color(0xFF0A0E17),
        darkSurface = Color(0xFF141A26),
        darkSurfaceV2 = Color(0xFF1C2333),
        darkSurfaceV3 = Color(0xFF242B3D),
    )),
    WHITE("Putih", BackgroundColors(
        darkBackground = Color(0xFFFFFFFF),
        darkSurface = Color(0xFFF8FAFC),
        darkSurfaceV2 = Color(0xFFF1F5F9),
        darkSurfaceV3 = Color(0xFFE2E8F0),
    )),
}

data class ThemeColors(
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceV2: Color,
    val darkSurfaceV3: Color,
    val neonGreen: Color,
    val neonGreenDim: Color,
    val neonGreenGlow: Color,
    val neonCyan: Color,
    val neonPurple: Color,
    val neonRed: Color,
    val neonYellow: Color,
    val neonOrange: Color,
    val gamersGreenDark: Color,
    val gamersGreenMedium: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color,
    val textOnGreen: Color,
)

val GamingDarkPalette = ThemeColors(
    darkBackground = Color(0xFF0A0A0A),
    darkSurface = Color(0xFF1A1A1A),
    darkSurfaceV2 = Color(0xFF222222),
    darkSurfaceV3 = Color(0xFF2A2A2A),
    neonGreen = Color(0xFF39FF14),
    neonGreenDim = Color(0xFF1A8C0A),
    neonGreenGlow = Color(0x8839FF14),
    neonCyan = Color(0xFF00E5FF),
    neonPurple = Color(0xFFBB00FF),
    neonRed = Color(0xFFFF1744),
    neonYellow = Color(0xFFFFEA00),
    neonOrange = Color(0xFFFF6D00),
    gamersGreenDark = Color(0xFF0D2818),
    gamersGreenMedium = Color(0xFF144D2B),
    textPrimary = Color(0xFFF0F0F0),
    textSecondary = Color(0xFFAAAAAA),
    textDim = Color(0xFF666666),
    textOnGreen = Color(0xFF0A0A0A),
)

val CyberBluePalette = ThemeColors(
    darkBackground = Color(0xFF0A0E1A),
    darkSurface = Color(0xFF141A2E),
    darkSurfaceV2 = Color(0xFF1C2340),
    darkSurfaceV3 = Color(0xFF242D52),
    neonGreen = Color(0xFF00E5FF),
    neonGreenDim = Color(0xFF0099B3),
    neonGreenGlow = Color(0x8800E5FF),
    neonCyan = Color(0xFF448AFF),
    neonPurple = Color(0xFF7C4DFF),
    neonRed = Color(0xFFFF1744),
    neonYellow = Color(0xFFFFEA00),
    neonOrange = Color(0xFFFF6D00),
    gamersGreenDark = Color(0xFF0D1B3D),
    gamersGreenMedium = Color(0xFF142852),
    textPrimary = Color(0xFFF0F0F0),
    textSecondary = Color(0xFFAAAAAA),
    textDim = Color(0xFF666666),
    textOnGreen = Color(0xFF0A0E1A),
)

val NeonPurplePalette = ThemeColors(
    darkBackground = Color(0xFF0F0A1A),
    darkSurface = Color(0xFF1E1433),
    darkSurfaceV2 = Color(0xFF261A40),
    darkSurfaceV3 = Color(0xFF2E2050),
    neonGreen = Color(0xFFBB00FF),
    neonGreenDim = Color(0xFF7A00B3),
    neonGreenGlow = Color(0x88BB00FF),
    neonCyan = Color(0xFFE040FB),
    neonPurple = Color(0xFF7C4DFF),
    neonRed = Color(0xFFFF1744),
    neonYellow = Color(0xFFFFEA00),
    neonOrange = Color(0xFFFF6D00),
    gamersGreenDark = Color(0xFF1A0D33),
    gamersGreenMedium = Color(0xFF241440),
    textPrimary = Color(0xFFF0F0F0),
    textSecondary = Color(0xFFAAAAAA),
    textDim = Color(0xFF666666),
    textOnGreen = Color(0xFF0F0A1A),
)

val ClassicDarkPalette = ThemeColors(
    darkBackground = Color(0xFF121212),
    darkSurface = Color(0xFF1E1E1E),
    darkSurfaceV2 = Color(0xFF282828),
    darkSurfaceV3 = Color(0xFF323232),
    neonGreen = Color(0xFF4CAF50),
    neonGreenDim = Color(0xFF2E7D32),
    neonGreenGlow = Color(0x884CAF50),
    neonCyan = Color(0xFF00BCD4),
    neonPurple = Color(0xFF9C27B0),
    neonRed = Color(0xFFF44336),
    neonYellow = Color(0xFFFFEB3B),
    neonOrange = Color(0xFFFF9800),
    gamersGreenDark = Color(0xFF1B3D1B),
    gamersGreenMedium = Color(0xFF225222),
    textPrimary = Color(0xFFF0F0F0),
    textSecondary = Color(0xFFAAAAAA),
    textDim = Color(0xFF666666),
    textOnGreen = Color(0xFF121212),
)

val LightModePalette = ThemeColors(
    darkBackground = Color(0xFFF5F5F5),
    darkSurface = Color(0xFFFFFFFF),
    darkSurfaceV2 = Color(0xFFEEEEEE),
    darkSurfaceV3 = Color(0xFFE0E0E0),
    neonGreen = Color(0xFF388E3C),
    neonGreenDim = Color(0xFF1B5E20),
    neonGreenGlow = Color(0x88388E3C),
    neonCyan = Color(0xFF00838F),
    neonPurple = Color(0xFF7B1FA2),
    neonRed = Color(0xFFD32F2F),
    neonYellow = Color(0xFFF57F17),
    neonOrange = Color(0xFFE65100),
    gamersGreenDark = Color(0xFFC8E6C9),
    gamersGreenMedium = Color(0xFFA5D6A7),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF555555),
    textDim = Color(0xFF999999),
    textOnGreen = Color(0xFFFFFFFF),
)

val ToscaGreenPalette = ThemeColors(
    darkBackground = Color(0xFFF0FDF4),
    darkSurface = Color(0xFFFFFFFF),
    darkSurfaceV2 = Color(0xFFE8F5E9),
    darkSurfaceV3 = Color(0xFFC8E6C9),
    neonGreen = Color(0xFF00C9A7),
    neonGreenDim = Color(0xFF00A88E),
    neonGreenGlow = Color(0x8800C9A7),
    neonCyan = Color(0xFF26C6DA),
    neonPurple = Color(0xFFAB47BC),
    neonRed = Color(0xFFEF5350),
    neonYellow = Color(0xFFFFCA28),
    neonOrange = Color(0xFFFF7043),
    gamersGreenDark = Color(0xFFB2DFDB),
    gamersGreenMedium = Color(0xFF80CBC4),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF555555),
    textDim = Color(0xFF999999),
    textOnGreen = Color(0xFFFFFFFF),
)
