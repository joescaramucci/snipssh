package com.example.sshsnippets

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

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
