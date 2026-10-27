package com.gamevault.unified.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppTheme(val label: String) {
    DEFAULT("Default"),
    MIDNIGHT("Midnight"),
    FOREST("Forest"),
    NEON("Neon Pulse"),
    SYNTHWAVE("Synthwave"),
    RGB("RGB Neon"),
    ARCADE("Arcade")
}

fun appThemeFromName(name: String): AppTheme =
    try {
        AppTheme.valueOf(name)
    } catch (e: IllegalArgumentException) {
        AppTheme.DEFAULT
    }

private val MidnightDark = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF0E4A53),
    onPrimaryContainer = Color(0xFFB3F0FB),
    secondary = Color(0xFFE879F9),
    onSecondary = Color(0xFF3B0A3E),
    secondaryContainer = Color(0xFF5B2160),
    onSecondaryContainer = Color(0xFFF5C9F8),
    tertiary = Color(0xFFA3E635),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF450A0A)
)

private val GamerDark = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF003840),
    primaryContainer = Color(0xFF004C59),
    onPrimaryContainer = Color(0xFF97F7FF),
    secondary = Color(0xFFFF4081),
    onSecondary = Color(0xFF3A0018),
    secondaryContainer = Color(0xFF5A0B32),
    onSecondaryContainer = Color(0xFFFFB3C8),
    tertiary = Color(0xFF76FF03),
    onTertiary = Color(0xFF0A2900),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFFF5454),
    errorContainer = Color(0xFF400E0E)
)

private val GamerLight = lightColorScheme(
    primary = Color(0xFF0097A7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF00373E),
    secondary = Color(0xFFC2185B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8BBD0),
    onSecondaryContainer = Color(0xFF4A0E29),
    tertiary = Color(0xFF558B2F),
    background = Color(0xFFF7F9FA),
    onBackground = Color(0xFF1A1F24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1F24),
    surfaceVariant = Color(0xFFE1E4E8),
    onSurfaceVariant = Color(0xFF44535E),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFCDD2)
)

private val NeonDark = darkColorScheme(
    primary = Color(0xFF39FF14),
    onPrimary = Color(0xFF023000),
    primaryContainer = Color(0xFF054A00),
    onPrimaryContainer = Color(0xFFB9FF99),
    secondary = Color(0xFFFF00FF),
    onSecondary = Color(0xFF330033),
    secondaryContainer = Color(0xFF550055),
    onSecondaryContainer = Color(0xFFFF99FF),
    tertiary = Color(0xFF00FFFF),
    onTertiary = Color(0xFF003333),
    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFE8F0FF),
    surface = Color(0xFF13131C),
    onSurface = Color(0xFFE8F0FF),
    surfaceVariant = Color(0xFF1D1D2B),
    onSurfaceVariant = Color(0xFFB0B8D6),
    error = Color(0xFFFF3B3B),
    errorContainer = Color(0xFF4A0A0A)
)

private val SynthwaveDark = darkColorScheme(
    primary = Color(0xFFFF006E),
    onPrimary = Color(0xFF3A0018),
    primaryContainer = Color(0xFF5A0B32),
    onPrimaryContainer = Color(0xFFFFB3C8),
    secondary = Color(0xFF8338EC),
    onSecondary = Color(0xFF0F0024),
    secondaryContainer = Color(0xFF2B0F55),
    onSecondaryContainer = Color(0xFFD4B4FF),
    tertiary = Color(0xFFFB5607),
    onTertiary = Color(0xFF261000),
    background = Color(0xFF120016),
    onBackground = Color(0xFFFDE8FF),
    surface = Color(0xFF1C0A22),
    onSurface = Color(0xFFFDE8FF),
    surfaceVariant = Color(0xFF2B1238),
    onSurfaceVariant = Color(0xFFCFA8E8),
    error = Color(0xFFFF4D4D),
    errorContainer = Color(0xFF4A0A0A)
)

private val ForestLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0D3311),
    secondary = Color(0xFF558B2F),
    background = Color(0xFFF7F9F2),
    onBackground = Color(0xFF1A2118),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2118),
    surfaceVariant = Color(0xFFE0E8DA),
    onSurfaceVariant = Color(0xFF445344),
    tertiary = Color(0xFF8D6E00)
)

private val ForestDark = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0D3311),
    primaryContainer = Color(0xFF1B4D20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFAED581),
    background = Color(0xFF0C1A0E),
    onBackground = Color(0xFFE4EBE0),
    surface = Color(0xFF122114),
    onSurface = Color(0xFFE4EBE0),
    surfaceVariant = Color(0xFF1E2F21),
    onSurfaceVariant = Color(0xFFB9C9B6),
    tertiary = Color(0xFFFFD54F)
)

private val RgbDark = darkColorScheme(
    primary = Color(0xFFFF1744),
    onPrimary = Color(0xFF1A0004),
    primaryContainer = Color(0xFF44000D),
    onPrimaryContainer = Color(0xFFFFB3BE),
    secondary = Color(0xFF2979FF),
    onSecondary = Color(0xFF001333),
    secondaryContainer = Color(0xFF001944),
    onSecondaryContainer = Color(0xFFB3CCFF),
    tertiary = Color(0xFF00E5FF),
    onTertiary = Color(0xFF00383D),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF12121A),
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF1D1D2B),
    onSurfaceVariant = Color(0xFFB0B8D6),
    error = Color(0xFFFF3B30),
    errorContainer = Color(0xFF4A0A0A)
)

private val ArcadeDark = darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF442F00),
    onPrimaryContainer = Color(0xFFFFE099),
    secondary = Color(0xFFFF6F00),
    onSecondary = Color(0xFF2A0E00),
    secondaryContainer = Color(0xFF4D1C00),
    onSecondaryContainer = Color(0xFFFFB880),
    tertiary = Color(0xFF7CB342),
    onTertiary = Color(0xFF0E1700),
    background = Color(0xFF140F05),
    onBackground = Color(0xFFFFF3D9),
    surface = Color(0xFF1F180B),
    onSurface = Color(0xFFFFF3D9),
    surfaceVariant = Color(0xFF2E2310),
    onSurfaceVariant = Color(0xFFD6CAAA),
    error = Color(0xFFFF5252),
    errorContainer = Color(0xFF4A0A0A)
)

@Composable
fun GameVaultTheme(
    appTheme: AppTheme = AppTheme.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.DEFAULT -> SynthwaveDark
        AppTheme.MIDNIGHT -> MidnightDark
        AppTheme.FOREST -> if (darkTheme) ForestDark else ForestLight
        AppTheme.NEON -> NeonDark
        AppTheme.SYNTHWAVE -> SynthwaveDark
        AppTheme.RGB -> RgbDark
        AppTheme.ARCADE -> ArcadeDark
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}