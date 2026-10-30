package com.batttext.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.batttext.app.data.prefs.FontMode
import com.batttext.app.data.prefs.ThemeMode

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

val DraculaPalette = BattTextColors(
    isDark = true,
    background = Color(0xFF1B1D26),
    panel = Color(0xFF232634),
    foreground = Color(0xFFF8F8F2),
    muted = Color(0xFF7B88A8),
    label = Color(0xFF8BE9FD),
    good = Color(0xFF50FA7B),
    warning = Color(0xFFFFB86C),
    critical = Color(0xFFFF5555),
    accent = Color(0xFFBD93F9),
    link = Color(0xFFFF79C6),
    border = Color(0xFF3E445E)
)

val AmoledPalette = BattTextColors(
    isDark = true,
    background = Color(0xFF000000),
    panel = Color(0xFF11131A),
    foreground = Color(0xFFF4F4F6),
    muted = Color(0xFF6E768A),
    label = Color(0xFF7CE3F6),
    good = Color(0xFF4AF676),
    warning = Color(0xFFFFB35C),
    critical = Color(0xFFFF4D4D),
    accent = Color(0xFFB78CF6),
    link = Color(0xFFFF70C0),
    border = Color(0xFF2C3142)
)

val AlucardPalette = BattTextColors(
    isDark = false,
    background = Color(0xFFFAF6E8),
    panel = Color(0xFFEDE8D5),
    foreground = Color(0xFF1C1E26),
    muted = Color(0xFF6E6A59),
    label = Color(0xFF04668C),
    good = Color(0xFF14710A),
    warning = Color(0xFFA84A10),
    critical = Color(0xFFC4302B),
    accent = Color(0xFF6244C5),
    link = Color(0xFF9E1C64),
    border = Color(0xFFC8C1A8)
)

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

object NoIndicationNodeFactory : IndicationNodeFactory {
    private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            drawContent()
        }
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
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
    val palette = when (themeMode) {
        ThemeMode.AUTO -> if (systemDark) DraculaPalette else AlucardPalette
        ThemeMode.DARK -> DraculaPalette
        ThemeMode.AMOLED -> AmoledPalette
        ThemeMode.LIGHT -> AlucardPalette
    }
    val useDark = palette.isDark

    val fontFamily = when (fontMode) {
        FontMode.SYSTEM -> FontFamily.Default
        FontMode.MONOSPACE -> FontFamily.Monospace
    }
    val charset = if (pureAscii) AsciiCharset else UnicodeCharset

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            runCatching {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                val bgArgb = palette.background.toArgb()
                window.statusBarColor = bgArgb
                window.navigationBarColor = bgArgb
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !useDark
                insetsController.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(
        LocalBattTextColors provides palette,
        LocalBattTextFont provides fontFamily,
        LocalBattTextCharset provides charset,
        LocalIndication provides NoIndicationNodeFactory,
        content = content
    )
}
