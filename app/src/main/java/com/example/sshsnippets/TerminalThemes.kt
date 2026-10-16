package com.example.sshsnippets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

const val MIN_TERMINAL_FONT_SIZE = 8
const val MAX_TERMINAL_FONT_SIZE = 24
const val DEFAULT_TERMINAL_FONT_SIZE = 12

class TerminalTheme(val id: String, val label: String, val fg: Color, val bg: Color)

val TerminalThemes = listOf(
    TerminalTheme("dark", "Scuro", Color(0xFFE6E6E6), Color(0xFF121212)),
    TerminalTheme("light", "Chiaro", Color(0xFF1E1E1E), Color(0xFFF5F5F5)),
    TerminalTheme("solarized", "Solarized", Color(0xFF839496), Color(0xFF002B36)),
)

fun themeById(id: String): TerminalTheme = TerminalThemes.firstOrNull { it.id == id } ?: TerminalThemes[0]

class TerminalFont(val id: String, val label: String, val family: FontFamily)

private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val TerminalFonts = listOf(
    TerminalFont("jetbrains", "JetBrains Mono", JetBrainsMono),
    TerminalFont("system", "Di sistema", FontFamily.Monospace),
)

fun fontById(id: String): TerminalFont = TerminalFonts.firstOrNull { it.id == id } ?: TerminalFonts[0]

fun terminalTextStyle(font: TerminalFont, sizeSp: Int) = TextStyle(
    fontFamily = font.family,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 5 / 4).sp,
)
