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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Stile delle righe di dettaglio, come l'output di `ls -l` nel terminale. */
private val LsLineStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 13.sp)

private val SmallActionStyle = TextStyle(fontSize = 11.sp)

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

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

    LaunchedEffect(path) { pathInput = path }

    fun withClient(block: suspend (SftpClient) -> Unit) = OpenSessions.runSftp(tab, block)

    suspend fun load(c: SftpClient, target: String) = tab.load(c, target)

    /** Risale di una cartella; alla radice chiude la vista SFTP. Legge tab.path al momento del click, perché ::goUp viene memorizzato da Compose. */
    fun goUp() {
        val current = tab.path
        val parent = parentOf(current)
        if (parent == current) onBack() else withClient { c -> load(c, parent) }
    }

    BackHandler(onBack = ::goUp)

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
            TextButton(onClick = ::goUp) { Text(stringResource(R.string.sftp_back)) }
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
        if (tab.state.value is SessionState.Failed) {
            TextButton(onClick = { OpenSessions.reconnectSftp(tab) }) {
                Text(stringResource(R.string.terminal_reconnect), maxLines = 1, softWrap = false)
            }
        }

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
            owner = e.owner,
            group = e.group,
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
