package com.rou39.bentohome.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.rou39.bentohome.container
import com.rou39.bentohome.data.ThemePalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * アプリ全体の配色。設定の [ThemePalette] で切り替える。
 * Bento はアイコン（docs/logo.svg）の紺と金、漆は朱と金。壁紙の色は Android の Material You に任せる。
 */
@Composable
fun BentoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val paletteFlow = remember(context) {
        context.container.settingsRepository.settings.map { it.themePalette }.distinctUntilChanged()
    }
    // 既定の配色で始めて、設定を読めたら切り替える（既定のままなら見た目は変わらない）
    val palette by paletteFlow.collectAsState(initial = ThemePalette.BENTO)
    MaterialTheme(colorScheme = colorSchemeFor(palette, dark, context), content = content)
}

fun colorSchemeFor(palette: ThemePalette, dark: Boolean, context: Context): ColorScheme = when (palette) {
    ThemePalette.BENTO -> if (dark) BentoDark else BentoLight
    ThemePalette.LACQUER -> if (dark) LacquerDark else LacquerLight
    ThemePalette.WALLPAPER -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    ThemePalette.STANDARD -> if (dark) darkColorScheme() else lightColorScheme()
}

/** 設定の色見本に描く2色（主の色と、添える色）。壁紙の色は端末の配色から取る。 */
fun swatchColors(palette: ThemePalette, dark: Boolean, context: Context): Pair<Color, Color> {
    val scheme = colorSchemeFor(palette, dark, context)
    return scheme.primary to scheme.secondary
}

// ---- Bento: 紺と金 ----

private val BentoLight = lightColorScheme(
    primary = Color(0xFF2F4690),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1F7),
    onPrimaryContainer = Color(0xFF101833),
    secondary = Color(0xFF8C6418),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E3BD),
    onSecondaryContainer = Color(0xFF3A2A08),
    tertiary = Color(0xFF8C6418),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F7F3),
    onBackground = Color(0xFF1A1C24),
    surface = Color(0xFFF8F7F3),
    onSurface = Color(0xFF1A1C24),
    surfaceVariant = Color(0xFFE2E3EC),
    onSurfaceVariant = Color(0xFF474B5C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F1EC),
    surfaceContainer = Color(0xFFECEBE6),
    surfaceContainerHigh = Color(0xFFE6E5E0),
    surfaceContainerHighest = Color(0xFFE0DFDA),
    outline = Color(0xFF777B8D),
    outlineVariant = Color(0xFFC7C9D6),
)

private val BentoDark = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF102060),
    primaryContainer = Color(0xFF2F4690),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFE6C27A),
    onSecondary = Color(0xFF3E2C00),
    secondaryContainer = Color(0xFF5A4310),
    onSecondaryContainer = Color(0xFFFBE3AE),
    tertiary = Color(0xFFE6C27A),
    onTertiary = Color(0xFF3E2C00),
    background = Color(0xFF0F1320),
    onBackground = Color(0xFFE4E5EF),
    surface = Color(0xFF0F1320),
    onSurface = Color(0xFFE4E5EF),
    surfaceVariant = Color(0xFF2A3043),
    onSurfaceVariant = Color(0xFFB4B8CA),
    surfaceContainerLowest = Color(0xFF0A0D17),
    surfaceContainerLow = Color(0xFF151A29),
    surfaceContainer = Color(0xFF1A1F30),
    surfaceContainerHigh = Color(0xFF22283B),
    surfaceContainerHighest = Color(0xFF2B3146),
    outline = Color(0xFF7E8399),
    outlineVariant = Color(0xFF3A4057),
)

// ---- 漆: 朱と金 ----

private val LacquerLight = lightColorScheme(
    primary = Color(0xFFA3271F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD4),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF8C6418),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E3BD),
    onSecondaryContainer = Color(0xFF3A2A08),
    tertiary = Color(0xFF8C6418),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF6F4),
    onBackground = Color(0xFF221A18),
    surface = Color(0xFFFAF6F4),
    onSurface = Color(0xFF221A18),
    surfaceVariant = Color(0xFFF0E0DC),
    onSurfaceVariant = Color(0xFF55433F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5EEEB),
    surfaceContainer = Color(0xFFEFE7E4),
    surfaceContainerHigh = Color(0xFFE9E1DE),
    surfaceContainerHighest = Color(0xFFE3DBD8),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BD),
)

private val LacquerDark = darkColorScheme(
    primary = Color(0xFFFFB4A8),
    onPrimary = Color(0xFF690003),
    primaryContainer = Color(0xFF8E1B14),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFE6C27A),
    onSecondary = Color(0xFF3E2C00),
    secondaryContainer = Color(0xFF5A4310),
    onSecondaryContainer = Color(0xFFFBE3AE),
    tertiary = Color(0xFFE6C27A),
    onTertiary = Color(0xFF3E2C00),
    background = Color(0xFF130B0A),
    onBackground = Color(0xFFF0DEDA),
    surface = Color(0xFF130B0A),
    onSurface = Color(0xFFF0DEDA),
    surfaceVariant = Color(0xFF3B2A27),
    onSurfaceVariant = Color(0xFFD8C2BD),
    surfaceContainerLowest = Color(0xFF0D0606),
    surfaceContainerLow = Color(0xFF1C1211),
    surfaceContainer = Color(0xFF221716),
    surfaceContainerHigh = Color(0xFF2C201E),
    surfaceContainerHighest = Color(0xFF372A28),
    outline = Color(0xFFA08C88),
    outlineVariant = Color(0xFF55433F),
)
