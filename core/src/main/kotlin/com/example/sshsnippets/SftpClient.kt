package com.example.sshsnippets

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Vector
import kotlin.math.abs

/** Bit dei permessi POSIX, incluso setuid/setgid/sticky (07777 in ottale). */
private const val PERMISSION_MASK = 0xFFF

/** [mode] contiene solo i bit dei permessi (inclusi setuid/setgid/sticky), senza il tipo del file. */
data class RemoteEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mode: Int,
    val perms: String,
    val mtime: Long,
)

/** Un solo canale SFTP per sessione: le operazioni sono serializzate da [mutex]. */
class SftpClient private constructor(private val session: Session, private val channel: ChannelSftp) : AutoCloseable {
    private val mutex = Mutex()

    suspend fun home(): String = op { it.pwd() }

    suspend fun list(path: String): List<RemoteEntry> = op { ch ->
        childrenOf(ch, path)
            .map {
                RemoteEntry(
                    it.filename,
                    it.attrs.isDir,
                    it.attrs.size,
                    it.attrs.permissions and PERMISSION_MASK,
                    it.attrs.permissionsString,
                    it.attrs.getMTime().toLong(),
                )
            }
            .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    /** Risolve [path] (anche relativo o con ".."); null se non è una cartella. */
    suspend fun resolveDir(path: String): String? = op { ch ->
        try {
            val real = ch.realpath(path)
            if (ch.stat(real).isDir) real else null
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) null else throw e
        }
    }

    suspend fun download(remote: String, out: OutputStream) = op { it.get(remote, out) }

    suspend fun upload(remote: String, input: InputStream) = op { it.put(input, remote) }

    /** Numero di elementi contenuti in [remote] (ricorsivo); 0 per file o cartelle vuote. */
    suspend fun countTree(remote: String): Int = op { countLocked(it, remote) }

    /** Cancella file, o cartelle con tutto il contenuto. I link simbolici vengono rimossi senza seguirli. */
    suspend fun deleteTree(remote: String, isDir: Boolean) = op { deleteLocked(it, remote, isDir) }

    suspend fun chmod(remote: String, mode: Int) = op { it.chmod(mode and PERMISSION_MASK, remote) }

    suspend fun rename(remote: String, target: String) = op { it.rename(remote, target) }

    private fun childrenOf(ch: ChannelSftp, path: String): List<ChannelSftp.LsEntry> {
        @Suppress("UNCHECKED_CAST")
        val entries = ch.ls(path) as Vector<ChannelSftp.LsEntry>
        return entries.filter { it.filename != "." && it.filename != ".." }
    }

    private fun countLocked(ch: ChannelSftp, remote: String): Int =
        childrenOf(ch, remote).sumOf { child ->
            1 + if (child.attrs.isDir) countLocked(ch, joinPath(remote, child.filename)) else 0
        }

    private fun deleteLocked(ch: ChannelSftp, remote: String, isDir: Boolean) {
        if (isDir) {
            childrenOf(ch, remote).forEach { child ->
                deleteLocked(ch, joinPath(remote, child.filename), child.attrs.isDir)
            }
            ch.rmdir(remote)
        } else {
            ch.rm(remote)
        }
    }

    private suspend fun <T> op(block: (ChannelSftp) -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) { block(channel) }
    }

    val isConnected: Boolean
        get() = channel.isConnected && session.isConnected

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }

    companion object {
        suspend fun open(
            messages: SshMessages,
            host: Host,
            store: HostKeyStore,
            confirmHostKey: suspend (String) -> Boolean,
        ): SftpClient = withContext(Dispatchers.IO) {
            val session = openVerifiedSession(messages, host, store, confirmHostKey)
            try {
                val channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(10_000)
                SftpClient(session, channel)
            } catch (e: Exception) {
                session.disconnect()
                throw e
            }
        }
    }
}

fun parentOf(path: String): String =
    if (path.length <= 1) "/" else path.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" }

fun joinPath(dir: String, name: String): String = if (dir.endsWith("/")) dir + name else "$dir/$name"

/** Espande un "~" iniziale nella home remota. */
fun expandHome(input: String, homeDir: String): String =
    if (input == "~" || input.startsWith("~/")) homeDir + input.removePrefix("~") else input

private const val SIX_MONTHS_MS = 182L * 24 * 60 * 60 * 1000

/** Data in stile `ls -l`: "Jan 28 14:05" se recente, altrimenti "Jan 28  2026". */
fun lsDate(mtimeSeconds: Long): String {
    val date = Date(mtimeSeconds * 1000)
    val cal = Calendar.getInstance().apply { time = date }
    val month = SimpleDateFormat("MMM", Locale.US).format(date)
    val recent = abs(System.currentTimeMillis() - date.time) < SIX_MONTHS_MS
    val timeOrYear = if (recent) SimpleDateFormat("HH:mm", Locale.US).format(date) else cal.get(Calendar.YEAR).toString()
    return "%s %2d %5s".format(month, cal.get(Calendar.DAY_OF_MONTH), timeOrYear)
}
