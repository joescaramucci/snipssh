package com.example.sshsnippets

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val MAX_HISTORY_PER_HOST = 200
private const val IV_LEN = 12
private const val TAG_BITS = 128

/**
 * Host e impostazioni, che includono le credenziali, stanno in un file cifrato con AES-256-GCM.
 * La chiave è protetta con DPAPI di Windows: solo lo stesso utente Windows sullo stesso PC può leggerla.
 */
class Store(private val dir: File = defaultDir()) : HostKeyStore {
    private val keyFile = File(dir, "master.key")
    private val dataFile = File(dir, "store.dat")
    private val key: SecretKeySpec
    private var data: JSONObject

    init {
        dir.mkdirs()
        key = SecretKeySpec(loadOrCreateKey(), "AES")
        data = readData()
    }

    private fun loadOrCreateKey(): ByteArray {
        if (keyFile.exists()) return unprotect(keyFile.readBytes())
        val fresh = ByteArray(32).also(SecureRandom()::nextBytes)
        keyFile.writeBytes(protect(fresh))
        return fresh
    }

    private fun protect(bytes: ByteArray): ByteArray =
        if (Platform.isWindows()) Crypt32Util.cryptProtectData(bytes) else bytes

    private fun unprotect(bytes: ByteArray): ByteArray =
        if (Platform.isWindows()) Crypt32Util.cryptUnprotectData(bytes) else bytes

    private fun readData(): JSONObject {
        if (!dataFile.exists()) return JSONObject()
        return try {
            val raw = dataFile.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_LEN))
            JSONObject(String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8))
        } catch (e: Exception) {
            // File illeggibile (chiave persa o file danneggiato): lo conservo accanto invece di cancellarlo.
            Files.move(dataFile.toPath(), File(dir, "store.dat.unreadable").toPath(), StandardCopyOption.REPLACE_EXISTING)
            JSONObject()
        }
    }

    @Synchronized
    private fun persist() {
        val iv = ByteArray(IV_LEN).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val tmp = File(dir, "store.dat.tmp")
        tmp.writeBytes(iv + cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8)))
        Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized
    private fun put(name: String, value: Any) {
        data.put(name, value)
        persist()
    }

    private fun string(name: String, default: String): String = data.optString(name, default)

    @Synchronized
    fun loadHosts(): List<Host> = JSONArray(string("hosts", "[]")).mapObjects { it.toHost() }

    fun saveHosts(hosts: List<Host>) = put("hosts", JSONArray(hosts.map { it.toJson() }).toString())

    @Synchronized
    fun loadSnippets(): List<Snippet> = JSONArray(string("snippets", "[]")).mapObjects { it.toSnippet() }

    fun saveSnippets(snippets: List<Snippet>) = put("snippets", JSONArray(snippets.map { it.toJson() }).toString())

    /** Comandi digitati sull'host, dal più usato al meno usato. */
    @Synchronized
    fun loadHistory(hostId: String): List<HistoryEntry> {
        val all = JSONObject(string("history", "{}"))
        return all.optJSONArray(hostId)?.mapObjects { it.toHistoryEntry() }.orEmpty().sortedWith(historyRelevance)
    }

    /** Registra un comando digitato: incrementa il conteggio o lo aggiunge, mantenendo i più rilevanti. */
    @Synchronized
    fun recordCommand(hostId: String, command: String) {
        val all = JSONObject(string("history", "{}"))
        val list = all.optJSONArray(hostId)?.mapObjects { it.toHistoryEntry() }.orEmpty().toMutableList()
        val now = System.currentTimeMillis()
        val i = list.indexOfFirst { it.command == command }
        if (i >= 0) {
            val e = list.removeAt(i)
            list.add(e.copy(count = e.count + 1, lastUsed = now))
        } else {
            list.add(HistoryEntry(command, 1, now))
        }
        all.put(hostId, JSONArray(list.sortedWith(historyRelevance).take(MAX_HISTORY_PER_HOST).map { it.toJson() }))
        put("history", all.toString())
    }

    @Synchronized
    override fun loadHostKey(hostId: String): ByteArray? {
        val all = JSONObject(string("hostkeys", "{}"))
        return if (all.has(hostId)) Base64.getDecoder().decode(all.getString(hostId)) else null
    }

    @Synchronized
    override fun saveHostKey(hostId: String, key: ByteArray) {
        val all = JSONObject(string("hostkeys", "{}"))
        all.put(hostId, Base64.getEncoder().encodeToString(key))
        put("hostkeys", all.toString())
    }

    /** Dimentica la chiave host salvata: alla prossima connessione verrà chiesta di nuovo la conferma. */
    @Synchronized
    fun removeHostKey(hostId: String) {
        val all = JSONObject(string("hostkeys", "{}"))
        all.remove(hostId)
        put("hostkeys", all.toString())
    }

    fun terminalFontSize(): Int = data.optInt("terminal_font_size", DEFAULT_TERMINAL_FONT_SIZE)
        .coerceIn(MIN_TERMINAL_FONT_SIZE, MAX_TERMINAL_FONT_SIZE)

    fun setTerminalFontSize(size: Int) = put("terminal_font_size", size.coerceIn(MIN_TERMINAL_FONT_SIZE, MAX_TERMINAL_FONT_SIZE))

    /** Intervallo keepalive SSH in secondi; 0 = disattivato. */
    override fun keepAliveSeconds(): Int = data.optInt("keepalive_seconds", DEFAULT_KEEPALIVE_SECONDS)
        .coerceIn(0, MAX_KEEPALIVE_SECONDS)

    fun setKeepAliveSeconds(sec: Int) = put("keepalive_seconds", sec.coerceIn(0, MAX_KEEPALIVE_SECONDS))

    fun terminalFontId(): String = string("terminal_font", TerminalFonts[0].id)

    fun setTerminalFontId(id: String) = put("terminal_font", id)

    fun terminalThemeId(): String = string("terminal_theme", TerminalThemes[0].id)

    fun setTerminalThemeId(id: String) = put("terminal_theme", id)

    /** Lingua scelta; se mai impostata, quella del sistema quando supportata. */
    fun appLanguage(): String = data.optString("app_language", "").ifEmpty { Strings.systemLanguage() }

    fun setAppLanguage(tag: String) = put("app_language", tag)

    fun appThemeId(): String = string("app_theme", AppThemes[0].id)

    fun setAppThemeId(id: String) = put("app_theme", id)

    /** Larghezza della colonna snippet nel terminale, in dp. */
    fun snippetsWidthDp(): Int = data.optInt("snippets_width", 240).coerceIn(160, 480)

    fun setSnippetsWidthDp(dp: Int) = put("snippets_width", dp.coerceIn(160, 480))

    /** Contenuto del backup in chiaro: host, snippet e chiavi host. Va cifrato prima di essere salvato. */
    @Synchronized
    fun exportPayload(): String = JSONObject()
        .put("version", 1)
        .put("hosts", JSONArray(loadHosts().map { it.toJson() }))
        .put("snippets", JSONArray(loadSnippets().map { it.toJson() }))
        .put("hostkeys", JSONObject(string("hostkeys", "{}")))
        .toString()

    /** Sostituisce host, snippet e chiavi host con quelli del backup. Tutto il parsing avviene prima di scrivere. */
    @Synchronized
    fun importPayload(json: String) {
        val o = JSONObject(json)
        val hosts = o.getJSONArray("hosts").mapObjects { it.toHost() }
        val snippets = o.getJSONArray("snippets").mapObjects { it.toSnippet() }
        val keys = o.getJSONObject("hostkeys")
        data.put("hosts", JSONArray(hosts.map { it.toJson() }).toString())
        data.put("snippets", JSONArray(snippets.map { it.toJson() }).toString())
        data.put("hostkeys", keys.toString())
        persist()
    }

    companion object {
        fun defaultDir(): File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "SnipSSH")
    }
}
