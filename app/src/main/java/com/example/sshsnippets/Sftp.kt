package com.example.sshsnippets

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
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

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }

    companion object {
        suspend fun open(context: Context, host: Host, store: Store, confirmHostKey: suspend (String) -> Boolean): SftpClient =
            withContext(Dispatchers.IO) {
                val session = openVerifiedSession(context, host, store, confirmHostKey)
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

private const val SIX_MONTHS_MS = 182L * 24 * 60 * 60 * 1000

/** Stile delle righe di dettaglio, come l'output di `ls -l` nel terminale. */
private val LsLineStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 13.sp)

private val SmallActionStyle = TextStyle(fontSize = 11.sp)

/** Data in stile `ls -l`: "Jan 28 14:05" se recente, altrimenti "Jan 28  2026". */
private fun lsDate(mtimeSeconds: Long): String {
    val date = Date(mtimeSeconds * 1000)
    val cal = Calendar.getInstance().apply { time = date }
    val month = SimpleDateFormat("MMM", Locale.US).format(date)
    val recent = abs(System.currentTimeMillis() - date.time) < SIX_MONTHS_MS
    val timeOrYear = if (recent) SimpleDateFormat("HH:mm", Locale.US).format(date) else cal.get(Calendar.YEAR).toString()
    return "%s %2d %5s".format(month, cal.get(Calendar.DAY_OF_MONTH), timeOrYear)
}

private val MODE_GROUP_LABELS = listOf(R.string.sftp_owner, R.string.sftp_group, R.string.sftp_other)
private val MODE_GROUP_DESCRIPTIONS = listOf(R.string.sftp_owner_desc, R.string.sftp_group_desc, null)
private val MODE_FLAGS = listOf("r", "w", "x")

/** Bit per (gruppo, flag): proprietario = 0o400/0o200/0o100, gruppo = 0o40/0o20/0o10, altri = 0o4/0o2/0o1. */
private fun modeBit(group: Int, flag: Int): Int = 1 shl (8 - (group * 3 + flag))

@Composable
private fun ChmodDialog(name: String, initialMode: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var mode by remember { mutableIntStateOf(initialMode) }
    var octal by remember { mutableStateOf("%04o".format(initialMode)) }
    val valid = octal.length == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_chmod_title, name)) },
        text = {
            Column {
                OutlinedTextField(
                    value = octal,
                    onValueChange = { input ->
                        val digits = input.filter { it in '0'..'7' }.take(4)
                        octal = digits
                        if (digits.length == 4) mode = digits.toInt(8)
                    },
                    label = { Text(stringResource(R.string.sftp_mode_octal)) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                MODE_GROUP_LABELS.forEachIndexed { group, labelRes ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(labelRes), Modifier.width(80.dp))
                        MODE_FLAGS.forEachIndexed { flag, letter ->
                            val bit = modeBit(group, flag)
                            Row(Modifier.width(64.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = (mode and bit) != 0,
                                    onCheckedChange = { on ->
                                        mode = if (on) mode or bit else mode and bit.inv()
                                        octal = "%04o".format(mode)
                                    },
                                )
                                Text(letter)
                            }
                        }
                    }
                    MODE_GROUP_DESCRIPTIONS[group]?.let { descRes ->
                        Text(
                            stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(mode) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

/** Espande un "~" iniziale nella home remota. */
internal fun expandHome(input: String, homeDir: String): String =
    if (input == "~" || input.startsWith("~/")) homeDir + input.removePrefix("~") else input

/** Schermata di una scheda SFTP. Connessione e cartella corrente stanno in [tab], quindi restano attive cambiando scheda. */
@Composable
fun SftpScreen(tab: OpenSftp, lock: AppLockViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val host = tab.host
    val client = tab.client
    val path = tab.path
    val entries = tab.entries
    val busy = tab.busy
    val error = tab.error
    val homeDir = tab.homeDir
    var toDelete by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleteCount by remember { mutableIntStateOf(0) }
    var toChmod by remember { mutableStateOf<RemoteEntry?>(null) }
    var toRename by remember { mutableStateOf<RemoteEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var downloadPath by remember { mutableStateOf<String?>(null) }
    var pathInput by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)
    LaunchedEffect(path) { pathInput = path }

    fun withClient(block: suspend (SftpClient) -> Unit) = OpenSessions.runSftp(tab, block)

    suspend fun load(c: SftpClient, target: String) = tab.load(c, target)

    fun goTo(input: String) {
        val raw = input.trim()
        if (raw.isEmpty()) return
        val expanded = expandHome(raw, homeDir)
        withClient { c ->
            val dir = c.resolveDir(expanded)
                ?: throw IOException(context.getString(R.string.sftp_not_a_folder, expanded))
            load(c, dir)
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            withClient { c ->
                val target = joinPath(path, displayName(context, uri) ?: "file")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { c.upload(target, it) }
                        ?: throw IOException(context.getString(R.string.file_unreadable))
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
                        ?: throw IOException(context.getString(R.string.file_write_failed))
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.sftp_back)) }
            Text("SFTP ${host.hostname}", style = MaterialTheme.typography.titleMedium)
        }
        SessionChipRow(Modifier.padding(vertical = 4.dp)) { OpenSessions.select(it) }
        OutlinedTextField(
            value = pathInput,
            onValueChange = { pathInput = it },
            label = { Text(stringResource(R.string.sftp_path)) },
            singleLine = true,
            enabled = client != null,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { goTo(pathInput) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            TextButton(
                enabled = client != null && !busy && path.length > 1,
                onClick = { withClient { c -> load(c, parentOf(path)) } },
            ) { Text(stringResource(R.string.sftp_up)) }
            TextButton(
                enabled = client != null && !busy,
                onClick = { withClient { c -> load(c, path) } },
            ) { Text(stringResource(R.string.sftp_refresh)) }
            TextButton(
                enabled = client != null && !busy,
                onClick = {
                    lock.expectExternalActivity()
                    uploadLauncher.launch(arrayOf("*/*"))
                },
            ) { Text(stringResource(R.string.sftp_upload)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(Modifier.weight(1f)) {
            items(entries, key = { it.name }) { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy && e.isDir) {
                            withClient { c -> load(c, joinPath(path, e.name)) }
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    val displayName = if (e.isDir) "${e.name}/" else e.name
                    Row {
                        Text(
                            displayName,
                            style = LsLineStyle,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%s %7d %s".format(e.perms, e.size, lsDate(e.mtime)),
                            style = LsLineStyle,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Row {
                        val small = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        val buttonModifier = Modifier.height(26.dp)
                        if (!e.isDir) {
                            TextButton(
                                enabled = !busy,
                                modifier = buttonModifier,
                                contentPadding = small,
                                onClick = {
                                    downloadPath = joinPath(path, e.name)
                                    lock.expectExternalActivity()
                                    saveLauncher.launch(e.name)
                                },
                            ) { Text(stringResource(R.string.sftp_download), style = SmallActionStyle) }
                        }
                        TextButton(
                            enabled = !busy,
                            modifier = buttonModifier,
                            contentPadding = small,
                            onClick = { toChmod = e },
                        ) { Text(stringResource(R.string.sftp_chmod), style = SmallActionStyle) }
                        TextButton(
                            enabled = !busy,
                            modifier = buttonModifier,
                            contentPadding = small,
                            onClick = {
                                renameText = e.name
                                toRename = e
                            },
                        ) { Text(stringResource(R.string.sftp_rename), style = SmallActionStyle) }
                        TextButton(
                            enabled = !busy,
                            modifier = buttonModifier,
                            contentPadding = small,
                            onClick = {
                                if (e.isDir) {
                                    withClient { c ->
                                        deleteCount = c.countTree(joinPath(path, e.name))
                                        toDelete = e
                                    }
                                } else {
                                    toDelete = e
                                }
                            },
                        ) { Text(stringResource(R.string.common_delete), style = SmallActionStyle) }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.sftp_delete_title, d.name)) },
            text = {
                Text(
                    if (d.isDir && deleteCount == 0) {
                        stringResource(R.string.sftp_delete_folder_empty)
                    } else if (d.isDir) {
                        stringResource(R.string.sftp_delete_folder, deleteCount)
                    } else {
                        stringResource(R.string.sftp_delete_file)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = joinPath(path, d.name)
                    toDelete = null
                    withClient { c ->
                        c.deleteTree(target, d.isDir)
                        load(c, path)
                    }
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    toRename?.let { r ->
        val newName = renameText.trim()
        val valid = newName.isNotEmpty() && !newName.contains('/') && newName != r.name
        AlertDialog(
            onDismissRequest = { toRename = null },
            title = { Text(stringResource(R.string.sftp_rename_title, r.name)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    toRename = null
                    withClient { c ->
                        c.rename(joinPath(path, r.name), joinPath(path, newName))
                        load(c, path)
                    }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { toRename = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    toChmod?.let { e ->
        ChmodDialog(
            name = e.name,
            initialMode = e.mode,
            onDismiss = { toChmod = null },
            onConfirm = { mode ->
                val target = joinPath(path, e.name)
                toChmod = null
                withClient { c ->
                    c.chmod(target, mode)
                    load(c, path)
                }
            },
        )
    }
}
