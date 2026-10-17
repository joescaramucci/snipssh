package com.example.sshsnippets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Lingua selezionabile dall'utente. [tag] è un tag BCP 47; la lingua di default è l'inglese. */
class AppLanguage(val tag: String, val labelRes: ResId)

const val DEFAULT_APP_LANGUAGE = "en"

val AppLanguages = listOf(
    AppLanguage("en", R.string.lang_en),
    AppLanguage("it", R.string.lang_it),
    AppLanguage("es", R.string.lang_es),
    AppLanguage("fr", R.string.lang_fr),
    AppLanguage("de", R.string.lang_de),
    AppLanguage("zh-CN", R.string.lang_zh),
    AppLanguage("ru", R.string.lang_ru),
    AppLanguage("ko", R.string.lang_ko),
)

/** Testo localizzato se [labelRes] è presente, altrimenti [fallback] (nomi di marchi e protocolli). */
@Composable
fun labelText(labelRes: ResId?, fallback: String): String = labelRes?.let { stringResource(it) } ?: fallback
