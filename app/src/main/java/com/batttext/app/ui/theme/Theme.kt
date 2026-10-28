package com.batttext.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.batttext.app.data.prefs.FontMode
import com.batttext.app.data.prefs.ThemeMode

/**
 * Semantic theme roles for BattText.
 * No screen hardcodes colors; all UI components reference BattTextTheme.colors.
 */
@Immutable
data class BattTextColors(
    val isDark: Boolean,
    val background: Color,
    val panel: Color,
    val foreground: Color,
    val muted: Color,
    val label: Color,
    val good: Color,
    val warning: Color,
    val critical: Color,
    val accent: Color,
    val link: Color,
    val border: Color
)

/**
 * Dracula-inspired dark palette: crisp high-contrast terminal look.
 */
val DraculaPalette = BattTextColors(
    isDark = true,
    background = Color(0xFF282A36),
    panel = Color(0xFF21222C),
    foreground = Color(0xFFF8F8F2),
    muted = Color(0xFF6272A4),
    label = Color(0xFF8BE9FD),
    good = Color(0xFF50FA7B),
    warning = Color(0xFFFFB86C),
    critical = Color(0xFFFF5555),
    accent = Color(0xFFBD93F9),
    link = Color(0xFFFF79C6),
    border = Color(0xFF6272A4)
)

/**
 * Alucard-inspired light palette (official Dracula light variant):
 * warm paper background with deep high-contrast ink colors.
 */
val AlucardPalette = BattTextColors(
    isDark = false,
    background = Color(0xFFFFFBEB),
    panel = Color(0xFFF3F0E0),
    foreground = Color(0xFF1F1F1F),
    muted = Color(0xFF6C664B),
    label = Color(0xFF036A96),
    good = Color(0xFF14710A),
    warning = Color(0xFFA34D14),
    critical = Color(0xFFCB3A2A),
    accent = Color(0xFF644AC9),
    link = Color(0xFFA41F69),
    border = Color(0xFF8E8768)
)

/**
 * Border character set so every box, table, divider, and progress bar can switch cleanly
 * between Unicode box-drawing characters and pure 7-bit ASCII characters.
 */
@Immutable
data class BorderCharset(
    val topLeft: Char,
    val topRight: Char,
    val bottomLeft: Char,
    val bottomRight: Char,
    val horizontal: Char,
    val doubleHorizontal: Char,
    val vertical: Char,
    val teeRight: Char,
    val teeLeft: Char,
    val teeDown: Char,
    val teeUp: Char,
    val cross: Char,
    val barFull: Char,
    val barHalf: Char,
    val barEmpty: Char,
    val sparkChars: String
)

val UnicodeCharset = BorderCharset(
    topLeft = '┌',
    topRight = '┐',
    bottomLeft = '└',
    bottomRight = '┘',
    horizontal = '─',
    doubleHorizontal = '═',
    vertical = '│',
    teeRight = '├',
    teeLeft = '┤',
    teeDown = '┬',
    teeUp = '┴',
    cross = '┼',
    barFull = '█',
    barHalf = '▌',
    barEmpty = '░',
    sparkChars = "▁▂▃▄▅▆▇█"
)

val AsciiCharset = BorderCharset(
    topLeft = '+',
    topRight = '+',
    bottomLeft = '+',
    bottomRight = '+',
    horizontal = '-',
    doubleHorizontal = '=',
    vertical = '|',
    teeRight = '+',
    teeLeft = '+',
    teeDown = '+',
    teeUp = '+',
    cross = '+',
    barFull = '#',
    barHalf = '=',
    barEmpty = '.',
    sparkChars = "_.,-~=+*#"
)

/**
 * Zero-ripple indication so taps feel instantaneous like a plain-text terminal scoreboard.
 */
private object NoIndication : Indication, IndicationInstance {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance = this

    override fun ContentDrawScope.drawIndication() {
        drawContent()
    }
}

val LocalBattTextColors = staticCompositionLocalOf { DraculaPalette }
val LocalBattTextFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
val LocalBattTextCharset = staticCompositionLocalOf { UnicodeCharset }

object BattTextTheme {
    val colors: BattTextColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBattTextColors.current

    val fontFamily: FontFamily
        @Composable
        @ReadOnlyComposable
        get() = LocalBattTextFont.current

    val charset: BorderCharset
        @Composable
        @ReadOnlyComposable
        get() = LocalBattTextCharset.current
}

@Composable
fun BattTextTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    fontMode: FontMode = FontMode.SYSTEM,
    pureAscii: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val palette = if (useDark) DraculaPalette else AlucardPalette
    val fontFamily = when (fontMode) {
        FontMode.SYSTEM -> FontFamily.Default
        FontMode.MONOSPACE -> FontFamily.Monospace
    }
    val charset = if (pureAscii) AsciiCharset else UnicodeCharset

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val bgArgb = palette.background.toArgb()
            window.statusBarColor = bgArgb
            window.navigationBarColor = bgArgb
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDark
            insetsController.isAppearanceLightNavigationBars = !useDark
        }
    }

    val m3Scheme = if (useDark) {
        darkColorScheme(
            background = palette.background,
            surface = palette.background,
            onBackground = palette.foreground,
            onSurface = palette.foreground,
            primary = palette.accent
        )
    } else {
        lightColorScheme(
            background = palette.background,
            surface = palette.background,
            onBackground = palette.foreground,
            onSurface = palette.foreground,
            primary = palette.accent
        )
    }

    MaterialTheme(colorScheme = m3Scheme) {
        CompositionLocalProvider(
            LocalBattTextColors provides palette,
            LocalBattTextFont provides fontFamily,
            LocalBattTextCharset provides charset,
            LocalIndication provides NoIndication,
            content = content
        )
    }
}
