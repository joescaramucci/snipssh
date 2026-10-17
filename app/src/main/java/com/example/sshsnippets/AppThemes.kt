package com.example.sshsnippets

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val OnDark = Color(0xFF1C1B1F)
private val OnLight = Color(0xFFE6E1E5)

class AppTheme(val id: String, val labelRes: Int, gradient: List<Color>, val colors: ColorScheme) {
    val brush: Brush = Brush.verticalGradient(gradient)
}

private fun appScheme(
    primary: Color,
    primaryContainer: Color,
    secondary: Color,
    secondaryContainer: Color,
    surface: Color,
    surfaceVariant: Color,
) = darkColorScheme(
    primary = primary,
    onPrimary = OnDark,
    primaryContainer = primaryContainer,
    onPrimaryContainer = OnLight,
    secondary = secondary,
    onSecondary = OnDark,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = OnLight,
    background = surface,
    surface = surface,
    onSurface = OnLight,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = Color(0xFFCAC4D0),
)

val AppThemes = listOf(
    AppTheme(
        id = "aurora",
        labelRes = R.string.theme_aurora,
        gradient = listOf(Color(0xFF1B1430), Color(0xFF2A1F4A), Color(0xFF1C1B1F)),
        colors = appScheme(
            primary = Color(0xFFD0BCFF),
            primaryContainer = Color(0xFF4F378B),
            secondary = Color(0xFFCCC2DC),
            secondaryContainer = Color(0xFF4A4458),
            surface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFF49454F),
        ),
    ),
    AppTheme(
        id = "ocean",
        labelRes = R.string.theme_ocean,
        gradient = listOf(Color(0xFF0A1A2F), Color(0xFF0D3B5E), Color(0xFF0A4D5C)),
        colors = appScheme(
            primary = Color(0xFF7FD3FF),
            primaryContainer = Color(0xFF004D6B),
            secondary = Color(0xFFA8D8EA),
            secondaryContainer = Color(0xFF1F4A5E),
            surface = Color(0xFF0A2A3F),
            surfaceVariant = Color(0xFF1C4257),
        ),
    ),
    AppTheme(
        id = "sunset",
        labelRes = R.string.theme_sunset,
        gradient = listOf(Color(0xFF2B0E1E), Color(0xFF5C1F3A), Color(0xFF7A3A1E)),
        colors = appScheme(
            primary = Color(0xFFFFB38A),
            primaryContainer = Color(0xFF7A3A1E),
            secondary = Color(0xFFF2B8C6),
            secondaryContainer = Color(0xFF5C2A3F),
            surface = Color(0xFF3A1626),
            surfaceVariant = Color(0xFF55243A),
        ),
    ),
    AppTheme(
        id = "forest",
        labelRes = R.string.theme_forest,
        gradient = listOf(Color(0xFF0B1F15), Color(0xFF1D4A34), Color(0xFF2D5E40)),
        colors = appScheme(
            primary = Color(0xFF9ED9B0),
            primaryContainer = Color(0xFF2D5E40),
            secondary = Color(0xFFBFD8B0),
            secondaryContainer = Color(0xFF3A5A44),
            surface = Color(0xFF13291D),
            surfaceVariant = Color(0xFF2A4A36),
        ),
    ),
)

fun appThemeById(id: String): AppTheme = AppThemes.firstOrNull { it.id == id } ?: AppThemes[0]
