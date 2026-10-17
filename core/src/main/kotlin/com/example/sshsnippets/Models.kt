package com.example.sshsnippets

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Host(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val password: String = "",
    val privateKey: String = "",
    val keyPassphrase: String = "",
    // Id di un elemento di Distros; vuoto = nessun logo.
    val distro: String = "",
    // Cartella in cui SFTP si apre alla connessione; vuota = home.
    val landingDir: String = "",
    // Colore esadecimale "#RRGGBB" della riga nell'elenco; vuoto = colore predefinito.
    val color: String = "",
)

data class Snippet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    // Variables use the {{name}} syntax, e.g. "docker logs -f {{container}}"
    val command: String,
    val sendEnter: Boolean = true,
) {
    // Le graffe di chiusura vanno escapate: il runtime Android le rifiuta altrimenti.
    private val varRegex get() = Regex("\\{\\{\\s*([\\p{L}\\p{N}_]+)\\s*\\}\\}")

    fun variables(): List<String> =
        varRegex.findAll(command).map { it.groupValues[1] }.distinct().toList()

    fun render(values: Map<String, String>): String =
        varRegex.replace(command) { values[it.groupValues[1]].orEmpty() }
}

fun Host.toJson() = JSONObject()
    .put("id", id).put("name", name).put("hostname", hostname).put("port", port)
    .put("username", username).put("password", password).put("privateKey", privateKey)
    .put("keyPassphrase", keyPassphrase).put("distro", distro).put("landingDir", landingDir)
    .put("color", color)

fun JSONObject.toHost() = Host(
    getString("id"), getString("name"), getString("hostname"), getInt("port"),
    getString("username"), optString("password"), optString("privateKey"), optString("keyPassphrase"),
    optString("distro"), optString("landingDir"), optString("color"),
)

fun Snippet.toJson() = JSONObject()
    .put("id", id).put("name", name).put("command", command).put("sendEnter", sendEnter)

fun JSONObject.toSnippet() = Snippet(
    getString("id"), getString("name"), getString("command"), optBoolean("sendEnter", true),
)

inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
    (0 until length()).map { f(getJSONObject(it)) }

/** Un comando digitato su un host: quante volte e quando è stato usato l'ultima volta (ms). */
data class HistoryEntry(val command: String, val count: Int, val lastUsed: Long)

fun HistoryEntry.toJson() = JSONObject().put("c", command).put("n", count).put("t", lastUsed)

fun JSONObject.toHistoryEntry() = HistoryEntry(getString("c"), getInt("n"), getLong("t"))

/** Più usato prima, a parità di conteggio il più recente. */
val historyRelevance: Comparator<HistoryEntry> =
    compareByDescending<HistoryEntry> { it.count }.thenByDescending { it.lastUsed }

/** Comandi che iniziano con [prefix] (senza distinzione di maiuscole), esclusi quelli uguali al prefisso. */
fun List<HistoryEntry>.suggest(prefix: String, limit: Int = 5): List<String> =
    asSequence()
        .filter { it.command.startsWith(prefix, ignoreCase = true) && !it.command.equals(prefix, ignoreCase = true) }
        .sortedWith(historyRelevance)
        .take(limit)
        .map { it.command }
        .toList()
