package com.momory.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class AppTheme(val label: String) {
    NEON("Néon"),
    PURPLE("Violet"),
    SUNSET("Coucher de soleil"),
    OCEAN("Océan")
}

/** Couleurs d'accent qui changent selon le thème choisi — le reste de la palette (fonds, texte) reste fixe. */
data class ThemeAccent(val primary: Color, val secondary: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(primary, secondary))
}

fun accentFor(theme: AppTheme): ThemeAccent = when (theme) {
    AppTheme.NEON -> ThemeAccent(MomoryNeon, MomoryBlue)
    AppTheme.PURPLE -> ThemeAccent(Color(0xFFC084FC), Color(0xFF7C3AED))
    AppTheme.SUNSET -> ThemeAccent(Color(0xFFFFA45C), Color(0xFFEF4444))
    AppTheme.OCEAN -> ThemeAccent(Color(0xFF22D3EE), Color(0xFF2563EB))
}

val LocalAccent = staticCompositionLocalOf { accentFor(AppTheme.NEON) }

/** Dégradés réutilisés pour donner du relief aux boutons et fonds sans surcharger la palette. */
object MomoryBrushes {
    val warm = Brush.linearGradient(listOf(MomoryPurple, MomoryBlue))
}

@Composable
fun backgroundBrush(accent: ThemeAccent): Brush =
    Brush.verticalGradient(listOf(MomoryBg, lerp(MomorySurface1, accent.primary, 0.08f)))

@Composable
fun MomoryTheme(theme: AppTheme = AppTheme.NEON, content: @Composable () -> Unit) {
    val accent = accentFor(theme)
    val colorScheme = darkColorScheme(
        background = MomoryBg,
        surface = MomorySurface1,
        surfaceVariant = MomorySurface2,
        primary = accent.primary,
        onPrimary = Color.Black,
        secondary = accent.secondary,
        error = MomoryRed,
        onBackground = MomoryText,
        onSurface = MomoryText,
        outline = MomoryBorder,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
