package com.example.sshsnippets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HostsScreen(
    hosts: List<Host>,
    onChange: (List<Host>) -> Unit,
    onConnect: (Host) -> Unit,
    onSftp: (Host) -> Unit,
    onForgetKey: (Host) -> Unit,
) {
    // null = editor chiuso; un Host con id vuoto = nuovo host.
    var editing by remember { mutableStateOf<Host?>(null) }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Button(onClick = { editing = Host(id = "", name = "", hostname = "", username = "") }) {
            Text(stringResource(R.string.host_new))
        }
        ScrollableList(listState, Modifier.weight(1f).padding(top = 8.dp)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(hosts, key = { it.id }) { h ->
                    val logo = distroById(h.distro)
                    ListItem(
                        modifier = Modifier.combinedClickable(onDoubleClick = { onConnect(h) }, onClick = {}),
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (logo != null) {
                                    DistroBadge(logo, size = 24)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(h.name)
                            }
                        },
                        supportingContent = {
                            Column {
                                Text("${h.username}@${h.hostname}:${h.port}")
                                FlowRow {
                                    TextButton(onClick = { onConnect(h) }) { Text(stringResource(R.string.host_connect)) }
                                    TextButton(onClick = { onSftp(h) }) { Text("SFTP") }
                                    TextButton(onClick = { editing = h }) { Text(stringResource(R.string.host_edit)) }
                                    TextButton(onClick = { onForgetKey(h) }) { Text(stringResource(R.string.host_forget_key)) }
                                    if (h.privateKey.isNotBlank()) {
                                        TextButton(onClick = {
                                            onChange(hosts.map { if (it.id == h.id) it.copy(privateKey = "", keyPassphrase = "") else it })
                                        }) { Text(stringResource(R.string.host_remove_key)) }
                                    }
                                    TextButton(onClick = { onChange(hosts - h) }) { Text(stringResource(R.string.common_delete)) }
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { initial ->
        HostEditorDialog(
            initial,
            onSave = { saved ->
                onChange(if (hosts.any { it.id == saved.id }) hosts.map { if (it.id == saved.id) saved else it } else hosts + saved)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostEditorDialog(initial: Host, onSave: (Host) -> Unit, onDismiss: () -> Unit) {
    val isNew = initial.id.isEmpty()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var hostname by remember { mutableStateOf(initial.hostname) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.username) }
    var pass by remember { mutableStateOf(initial.password) }
    var key by remember { mutableStateOf(initial.privateKey) }
    var passphrase by remember { mutableStateOf(initial.keyPassphrase) }
    var distro by remember { mutableStateOf(initial.distro) }
    var landingDir by remember { mutableStateOf(initial.landingDir) }
    var keyError by remember { mutableStateOf<String?>(null) }
    val formScroll = rememberScrollState()

    fun importKey() {
        scope.launch {
            val file = withContext(Dispatchers.IO) { pickFile(Strings.get("host_import_key"), save = false) } ?: return@launch
            try {
                val text = withContext(Dispatchers.IO) { file.readText() }
                val problem = validatePrivateKey(text)
                if (problem == null) {
                    key = text.trim()
                    keyError = null
                } else {
                    keyError = Strings.get(problem)
                }
            } catch (e: Exception) {
                keyError = Strings.get("host_key_read_error", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(620.dp),
        title = { Text(stringResource(if (isNew) R.string.host_new else R.string.host_edit)) },
        text = {
            ScrollableColumn(formScroll, Modifier.heightIn(max = 600.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hostname, { hostname = it }, label = { Text(stringResource(R.string.host_address)) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.host_port)) }, singleLine = true, modifier = Modifier.width(100.dp))
                }
                OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.host_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.host_logo), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = distro.isEmpty(), onClick = { distro = "" }, label = { Text(stringResource(R.string.host_logo_none)) })
                    Distros.forEach { d ->
                        FilterChip(
                            selected = distro == d.id,
                            onClick = { distro = d.id },
                            label = { Text(labelText(d.labelRes, d.label)) },
                            leadingIcon = { DistroBadge(d, size = 18) },
                        )
                    }
                }
                OutlinedTextField(pass, { pass = it }, label = { Text(stringResource(R.string.common_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    landingDir,
                    { landingDir = it },
                    label = { Text(stringResource(R.string.host_landing_dir)) },
                    supportingText = { Text(stringResource(R.string.host_landing_dir_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    key,
                    { key = it; keyError = null },
                    label = { Text(stringResource(R.string.host_private_key)) },
                    isError = keyError != null,
                    supportingText = { keyError?.let { Text(it) } },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = ::importKey) { Text(stringResource(R.string.host_import_key)) }
                OutlinedTextField(
                    passphrase,
                    { passphrase = it },
                    label = { Text(stringResource(R.string.host_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = hostname.isNotBlank() && user.isNotBlank(),
                onClick = {
                    if (key.isNotBlank()) {
                        validatePrivateKey(key)?.let {
                            keyError = Strings.get(it)
                            return@Button
                        }
                    }
                    onSave(
                        Host(
                            id = initial.id.ifEmpty { UUID.randomUUID().toString() },
                            name = name.ifBlank { hostname },
                            hostname = hostname.trim(),
                            port = port.toIntOrNull() ?: 22,
                            username = user,
                            password = pass,
                            privateKey = key,
                            keyPassphrase = passphrase,
                            distro = distro,
                            landingDir = landingDir.trim(),
                        ),
                    )
                },
            ) { Text(stringResource(if (isNew) R.string.common_save else R.string.host_save_changes)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
fun SnippetsScreen(snippets: List<Snippet>, onChange: (List<Snippet>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true, modifier = Modifier.width(240.dp))
            OutlinedTextField(cmd, { cmd = it }, label = { Text(stringResource(R.string.snippets_command_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = {
                if (name.isNotBlank() && cmd.isNotBlank()) {
                    onChange(snippets + Snippet(name = name, command = cmd))
                    name = ""; cmd = ""
                }
            }) { Text(stringResource(R.string.snippets_add)) }
        }
        ScrollableList(listState, Modifier.weight(1f).padding(top = 8.dp)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(snippets, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(s.command, fontFamily = FontFamily.Monospace) },
                        trailingContent = { TextButton(onClick = { onChange(snippets - s) }) { Text(stringResource(R.string.common_delete)) } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
