package com.example.sshsnippets

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Identificatore di una risorsa: l'id numerico di Android. Sul desktop è il nome della risorsa. */
typealias ResId = Int

/** Il font JetBrains Mono incluso nell'app. */
fun jetBrainsMono(): FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
