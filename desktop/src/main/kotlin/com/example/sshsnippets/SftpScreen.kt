package com.example.sshsnippets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Righe di dettaglio nello stile di `ls -l`, come sull'app Android. */
private val LsLineStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 15.sp)
private val NameStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 18.sp)
private val ActionStyle = TextStyle(fontSize = 12.sp)

/** Schermata di una scheda SFTP. Connessione e cartella corrente stanno in [tab], quindi restano attive cambiando scheda. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SftpScreen(tab: OpenSftp) {
    val scope = rememberCoroutineScope()
    val client = tab.client
    val path = tab.path
    val busy = tab.busy
    var toDelete by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleteCount by remember { mutableIntStateOf(0) }
    var toChmod by remember { mutableStateOf<RemoteEntry?>(null) }
    var toRename by remember { mutableStateOf<RemoteEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pathInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(path) { pathInput = path }

    fun withClient(block: suspend (SftpClient) -> Unit) = OpenSessions.runSftp(tab, block)

    suspend fun load(c: SftpClient, target: String) = tab.load(c, target)

    fun goTo(input: String) {
        val raw = input.trim()
        if (raw.isEmpty()) return
        val expanded = expandHome(raw, tab.homeDir)
        withClient { c ->
            val dir = c.resolveDir(expanded) ?: throw IOException(Strings.get("sftp_not_a_folder", expanded))
            load(c, dir)
        }
    }

    fun upload() {
        scope.launch {
            val files = withContext(Dispatchers.IO) { pickFiles(Strings.get("sftp_upload")) }
            if (files.isEmpty()) return@launch
            val target = tab.path
            withClient { c ->
                withContext(Dispatchers.IO) {
                    files.forEach { f -> f.inputStream().use { c.upload(joinPath(target, f.name), it) } }
                }
                load(c, target)
            }
        }
    }

    fun download(entry: RemoteEntry) {
        scope.launch {
            val file = withContext(Dispatchers.IO) { pickFile(Strings.get("sftp_download"), save = true, suggestedName = entry.name) }
                ?: return@launch
            val remote = joinPath(tab.path, entry.name)
            withClient { c ->
                withContext(Dispatchers.IO) { file.outputStream().use { c.download(remote, it) } }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SFTP ${tab.host.hostname}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
            OutlinedTextField(
                value = pathInput,
                onValueChange = { pathInput = it },
                label = { Text(stringResource(R.string.sftp_path)) },
                singleLine = true,
                enabled = client != null,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { goTo(pathInput) }),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = client != null && !busy && path.length > 1,
                onClick = { withClient { c -> load(c, parentOf(tab.path)) } },
            ) { Text(stringResource(R.string.sftp_up)) }
            TextButton(
                enabled = client != null && !busy,
                onClick = { withClient { c -> load(c, tab.path) } },
            ) { Text(stringResource(R.string.sftp_refresh)) }
            TextButton(enabled = client != null && !busy, onClick = ::upload) { Text(stringResource(R.string.sftp_upload)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        tab.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (tab.state.value is SessionState.Failed) {
            TextButton(onClick = { OpenSessions.reconnectSftp(tab) }) {
                Text(stringResource(R.string.terminal_reconnect), maxLines = 1, softWrap = false)
            }
        }

        ScrollableList(listState, Modifier.weight(1f).padding(top = 4.dp)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(tab.entries, key = { it.name }) { e ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(enabled = !busy && e.isDir, onDoubleClick = {
                                withClient { c -> load(c, joinPath(tab.path, e.name)) }
                            }, onClick = {})
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    ) {
                    Text(
                        if (e.isDir) "${e.name}/" else e.name,
                        style = NameStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "%s %-12s %-12s %9d %s".format(e.perms, e.owner, e.group, e.size, lsDate(e.mtime)),
                            style = LsLineStyle,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        val small = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        val buttonModifier = Modifier.height(28.dp)
                        if (e.isDir) {
                            TextButton(
                                enabled = !busy,
                                modifier = buttonModifier,
                                contentPadding = small,
                                onClick = { withClient { c -> load(c, joinPath(tab.path, e.name)) } },
                            ) { Text("›", style = ActionStyle) }
                        } else {
                            TextButton(enabled = !busy, modifier = buttonModifier, contentPadding = small, onClick = { download(e) }) {
                                Text(stringResource(R.string.sftp_download), style = ActionStyle)
                            }
                        }
                        TextButton(enabled = !busy, modifier = buttonModifier, contentPadding = small, onClick = { toChmod = e }) {
                            Text(stringResource(R.string.sftp_chmod), style = ActionStyle)
                        }
                        TextButton(
                            enabled = !busy,
                            modifier = buttonModifier,
                            contentPadding = small,
                            onClick = {
                                renameText = e.name
                                toRename = e
                            },
                        ) { Text(stringResource(R.string.sftp_rename), style = ActionStyle) }
                        TextButton(
                            enabled = !busy,
                            modifier = buttonModifier,
                            contentPadding = small,
                            onClick = {
                                if (e.isDir) {
                                    withClient { c ->
                                        deleteCount = c.countTree(joinPath(tab.path, e.name))
                                        toDelete = e
                                    }
                                } else {
                                    toDelete = e
                                }
                            },
                        ) { Text(stringResource(R.string.common_delete), style = ActionStyle) }
                    }
                    }
                    HorizontalDivider()
                }
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
                    val dir = tab.path
                    val target = joinPath(dir, d.name)
                    toDelete = null
                    withClient { c ->
                        c.deleteTree(target, d.isDir)
                        load(c, dir)
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
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    val dir = tab.path
                    toRename = null
                    withClient { c ->
                        c.rename(joinPath(dir, r.name), joinPath(dir, newName))
                        load(c, dir)
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
                val dir = tab.path
                val target = joinPath(dir, e.name)
                toChmod = null
                withClient { c ->
                    c.chmod(target, mode)
                    load(c, dir)
                }
            },
        )
    }
}
