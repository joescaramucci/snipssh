package com.example.sshsnippets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Sul desktop una risorsa è identificata dal suo nome (vedi l'oggetto R generato in build). */
typealias ResId = String

fun jetBrainsMono(): FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Il nome della stringa con il motivo per cui il testo non è una chiave privata utilizzabile, oppure null se è valido. */
fun validatePrivateKey(text: String?): ResId? = when (privateKeyProblem(text)) {
    PrivateKeyProblem.Empty -> R.string.host_key_empty
    PrivateKeyProblem.Ppk -> R.string.host_key_ppk
    PrivateKeyProblem.Format -> R.string.host_key_format
    null -> null
}

/** Servizi di piattaforma per [OpenSessions]: gli avvisi brevi finiscono in [DesktopPlatform.notice]. */
object DesktopPlatform : AppPlatform {
    override val messages: SshMessages = object : SshMessages {
        override fun connectFailed(hostname: String, detail: String) = Strings.get("ssh_connect_failed", hostname, detail)
        override fun keyRejected() = Strings.get("ssh_key_rejected")
        override fun keyChanged(hostname: String, fingerprint: String) = Strings.get("ssh_key_changed", hostname, fingerprint)
    }

    var notice by mutableStateOf<String?>(null)

    override fun string(id: ResId, vararg args: Any?): String = Strings.get(id, *args)

    override fun toast(text: String?) {
        notice = text
    }
}

/**
 * Le stringhe sono quelle dell'app Android (strings.xml), copiate nelle risorse in fase di build.
 * La lingua è uno stato Compose: cambiandola l'interfaccia si aggiorna senza riavvio.
 */
object Strings {
    var language by mutableStateOf(DEFAULT_APP_LANGUAGE)

    private val cache = HashMap<String, Map<String, String>>()

    private fun folderFor(tag: String) = when (tag) {
        "en" -> "values"
        "zh-CN" -> "values-zh-rCN"
        else -> "values-$tag"
    }

    private fun table(tag: String): Map<String, String> = cache.getOrPut(tag) {
        val stream = Strings::class.java.getResourceAsStream("/strings/${folderFor(tag)}.xml") ?: return@getOrPut emptyMap()
        stream.use {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).getElementsByTagName("string")
            (0 until nodes.length).associate { i ->
                val node = nodes.item(i)
                node.attributes.getNamedItem("name").nodeValue to unescape(node.textContent)
            }
        }
    }

    /** Il testo nella lingua scelta; se manca, in inglese; se manca ancora, il nome della stringa. */
    fun get(id: String, vararg args: Any?): String {
        val raw = table(language)[id] ?: table(DEFAULT_APP_LANGUAGE)[id] ?: return id
        return if (args.isEmpty()) raw else String.format(Locale.ROOT, raw, *args)
    }

    /** Lingua di sistema se tra quelle supportate, altrimenti l'inglese. */
    fun systemLanguage(): String {
        val locale = Locale.getDefault()
        val full = locale.toLanguageTag()
        return AppLanguages.firstOrNull { it.tag.equals(full, ignoreCase = true) }?.tag
            ?: AppLanguages.firstOrNull { it.tag.substringBefore('-') == locale.language }?.tag
            ?: DEFAULT_APP_LANGUAGE
    }

    /** Le sequenze di escape di strings.xml: \n, \', \", \\, \@, \? e \uXXXX. */
    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                when {
                    n == 'n' -> { out.append('\n'); i += 2 }
                    n == 't' -> { out.append('\t'); i += 2 }
                    n == 'u' && s.length >= i + 6 -> {
                        out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6
                    }
                    else -> { out.append(n); i += 2 }
                }
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }
}
