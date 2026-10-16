package com.example.sshsnippets

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Vector

data class RemoteEntry(val name: String, val isDir: Boolean, val size: Long)

/** Un solo canale SFTP per sessione: le operazioni sono serializzate da [mutex]. */
class SftpClient private constructor(private val session: Session, private val channel: ChannelSftp) : AutoCloseable {
    private val mutex = Mutex()

    suspend fun home(): String = op { it.pwd() }

    suspend fun list(path: String): List<RemoteEntry> = op { ch ->
        @Suppress("UNCHECKED_CAST")
        val entries = ch.ls(path) as Vector<ChannelSftp.LsEntry>
        entries
            .filter { it.filename != "." && it.filename != ".." }
            .map { RemoteEntry(it.filename, it.attrs.isDir, it.attrs.size) }
            .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    suspend fun download(remote: String, out: OutputStream) = op { it.get(remote, out) }

    suspend fun upload(remote: String, input: InputStream) = op { it.put(input, remote) }

    /** Le cartelle devono essere vuote: JSch non cancella ricorsivamente. */
    suspend fun delete(remote: String, isDir: Boolean) = op { if (isDir) it.rmdir(remote) else it.rm(remote) }

    private suspend fun <T> op(block: (ChannelSftp) -> T): T = mutex.withLock {
        withContext(Dispatchers.IO) { block(channel) }
    }

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }

    companion object {
        suspend fun open(host: Host, store: Store, confirmHostKey: suspend (String) -> Boolean): SftpClient =
            withContext(Dispatchers.IO) {
                val session = openVerifiedSession(host, store, confirmHostKey)
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

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

@Composable
fun SftpScreen(host: Host, store: Store, lock: AppLockViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prompter = rememberHostKeyPrompter()
    var client by remember { mutableStateOf<SftpClient?>(null) }
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<RemoteEntry?>(null) }
    var downloadPath by remember { mutableStateOf<String?>(null) }

    fun withClient(block: suspend (SftpClient) -> Unit) {
        val c = client ?: return
        scope.launch {
            busy = true
            error = null
            try {
                block(c)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    suspend fun load(c: SftpClient, target: String) {
        entries = c.list(target)
        path = target
    }

    LaunchedEffect(host) {
        busy = true
        try {
            val c = SftpClient.open(host, store) { fingerprint -> prompter.confirm(host.hostname, fingerprint) }
            client = c
            load(c, c.home())
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        } finally {
            busy = false
        }
    }
    DisposableEffect(host) {
        onDispose { client?.close() }
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            withClient { c ->
                val target = joinPath(path, displayName(context, uri) ?: "file")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { c.upload(target, it) }
                        ?: throw IOException("File non leggibile")
                }
                load(c, path)
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val remote = downloadPath
        downloadPath = null
        if (uri != null && remote != null) {
            withClient { c ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { c.download(remote, it) }
                        ?: throw IOException("Impossibile scrivere il file")
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Indietro") }
            Text("SFTP ${host.hostname}", style = MaterialTheme.typography.titleMedium)
        }
        Text(path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(
                enabled = client != null && !busy && path.length > 1,
                onClick = { withClient { c -> load(c, parentOf(path)) } },
            ) { Text("Su") }
            TextButton(
                enabled = client != null && !busy,
                onClick = { withClient { c -> load(c, path) } },
            ) { Text("Aggiorna") }
            TextButton(
                enabled = client != null && !busy,
                onClick = {
                    lock.expectExternalActivity()
                    uploadLauncher.launch(arrayOf("*/*"))
                },
            ) { Text("Carica") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(Modifier.weight(1f)) {
            items(entries, key = { it.name }) { e ->
                ListItem(
                    headlineContent = { Text(if (e.isDir) "${e.name}/" else e.name) },
                    supportingContent = { if (!e.isDir) Text(formatSize(e.size)) },
                    modifier = Modifier.clickable(enabled = !busy && e.isDir) {
                        withClient { c -> load(c, joinPath(path, e.name)) }
                    },
                    trailingContent = {
                        Row {
                            if (!e.isDir) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        downloadPath = joinPath(path, e.name)
                                        lock.expectExternalActivity()
                                        saveLauncher.launch(e.name)
                                    },
                                ) { Text("Scarica") }
                            }
                            TextButton(enabled = !busy, onClick = { toDelete = e }) { Text("Elimina") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminare ${d.name}?") },
            text = {
                Text(if (d.isDir) "La cartella deve essere vuota." else "Il file verrà eliminato dal server.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = joinPath(path, d.name)
                    toDelete = null
                    withClient { c ->
                        c.delete(target, d.isDir)
                        load(c, path)
                    }
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annulla") } },
        )
    }

    HostKeyDialog(prompter)
}
