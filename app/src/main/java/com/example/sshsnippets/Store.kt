package com.example.sshsnippets

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

private const val MAX_HISTORY_PER_HOST = 200

// Hosts hold credentials, so everything lives in encrypted preferences.
class Store(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_store",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun loadHosts(): List<Host> =
        JSONArray(prefs.getString("hosts", "[]")).mapObjects { it.toHost() }

    fun saveHosts(hosts: List<Host>) =
        prefs.edit().putString("hosts", JSONArray(hosts.map { it.toJson() }).toString()).apply()

    fun loadSnippets(): List<Snippet> =
        JSONArray(prefs.getString("snippets", "[]")).mapObjects { it.toSnippet() }

    fun saveSnippets(snippets: List<Snippet>) =
        prefs.edit().putString("snippets", JSONArray(snippets.map { it.toJson() }).toString()).apply()

    /** Comandi digitati sull'host, dal più usato al meno usato. */
    fun loadHistory(hostId: String): List<HistoryEntry> {
        val all = JSONObject(prefs.getString("history", "{}")!!)
        return all.optJSONArray(hostId)?.mapObjects { it.toHistoryEntry() }.orEmpty().sortedWith(historyRelevance)
    }

    /** Registra un comando digitato: incrementa il conteggio o lo aggiunge, mantenendo i più rilevanti. */
    fun recordCommand(hostId: String, command: String) {
        val all = JSONObject(prefs.getString("history", "{}")!!)
        val list = all.optJSONArray(hostId)?.mapObjects { it.toHistoryEntry() }.orEmpty().toMutableList()
        val now = System.currentTimeMillis()
        val i = list.indexOfFirst { it.command == command }
        if (i >= 0) {
            val e = list.removeAt(i)
            list.add(e.copy(count = e.count + 1, lastUsed = now))
        } else {
            list.add(HistoryEntry(command, 1, now))
        }
        val kept = list.sortedWith(historyRelevance).take(MAX_HISTORY_PER_HOST)
        all.put(hostId, JSONArray(kept.map { it.toJson() }))
        prefs.edit().putString("history", all.toString()).apply()
    }

    /** Chiave host salvata per "host:porta", oppure null se non ancora vista. */
    fun loadHostKey(hostId: String): ByteArray? {
        val all = JSONObject(prefs.getString("hostkeys", "{}")!!)
        return if (all.has(hostId)) Base64.getDecoder().decode(all.getString(hostId)) else null
    }

    fun saveHostKey(hostId: String, key: ByteArray) {
        val all = JSONObject(prefs.getString("hostkeys", "{}")!!)
        all.put(hostId, Base64.getEncoder().encodeToString(key))
        prefs.edit().putString("hostkeys", all.toString()).apply()
    }

    /** Dimentica la chiave host salvata: alla prossima connessione verrà chiesta di nuovo la conferma. */
    fun removeHostKey(hostId: String) {
        val all = JSONObject(prefs.getString("hostkeys", "{}")!!)
        all.remove(hostId)
        prefs.edit().putString("hostkeys", all.toString()).apply()
    }

    fun biometricLock(): Boolean = prefs.getBoolean("biometric_lock", false)

    fun setBiometricLock(enabled: Boolean) = prefs.edit().putBoolean("biometric_lock", enabled).apply()

    fun terminalFontSize(): Int = prefs.getInt("terminal_font_size", DEFAULT_TERMINAL_FONT_SIZE)
        .coerceIn(MIN_TERMINAL_FONT_SIZE, MAX_TERMINAL_FONT_SIZE)

    fun setTerminalFontSize(sp: Int) =
        prefs.edit().putInt("terminal_font_size", sp.coerceIn(MIN_TERMINAL_FONT_SIZE, MAX_TERMINAL_FONT_SIZE)).apply()

    /** Intervallo keepalive SSH in secondi; 0 = disattivato. */
    fun keepAliveSeconds(): Int = prefs.getInt("keepalive_seconds", DEFAULT_KEEPALIVE_SECONDS)
        .coerceIn(0, MAX_KEEPALIVE_SECONDS)

    fun setKeepAliveSeconds(sec: Int) =
        prefs.edit().putInt("keepalive_seconds", sec.coerceIn(0, MAX_KEEPALIVE_SECONDS)).apply()

    fun terminalFontId(): String = prefs.getString("terminal_font", TerminalFonts[0].id)!!

    fun setTerminalFontId(id: String) = prefs.edit().putString("terminal_font", id).apply()

    fun terminalThemeId(): String = prefs.getString("terminal_theme", TerminalThemes[0].id)!!

    fun setTerminalThemeId(id: String) = prefs.edit().putString("terminal_theme", id).apply()

    fun appLanguage(): String = prefs.getString("app_language", DEFAULT_APP_LANGUAGE)!!

    fun setAppLanguage(tag: String) = prefs.edit().putString("app_language", tag).apply()

    /** Contenuto del backup in chiaro: host, snippet e chiavi host. Va cifrato prima di essere salvato. */
    fun exportPayload(): String = JSONObject()
        .put("version", 1)
        .put("hosts", JSONArray(loadHosts().map { it.toJson() }))
        .put("snippets", JSONArray(loadSnippets().map { it.toJson() }))
        .put("hostkeys", JSONObject(prefs.getString("hostkeys", "{}")!!))
        .toString()

    /** Sostituisce host, snippet e chiavi host con quelli del backup. Tutto il parsing avviene prima di scrivere. */
    fun importPayload(json: String) {
        val o = JSONObject(json)
        val hosts = o.getJSONArray("hosts").mapObjects { it.toHost() }
        val snippets = o.getJSONArray("snippets").mapObjects { it.toSnippet() }
        val keys = o.getJSONObject("hostkeys")
        prefs.edit()
            .putString("hosts", JSONArray(hosts.map { it.toJson() }).toString())
            .putString("snippets", JSONArray(snippets.map { it.toJson() }).toString())
            .putString("hostkeys", keys.toString())
            .apply()
    }
}
