package com.example.sshsnippets

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private val lock: AppLockViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase, Store(newBase).appLanguage()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        OpenSessions.attach(store, localizedContext(applicationContext, store.appLanguage()))
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App(store, lock) } }
    }

    override fun onStop() {
        super.onStop()
        lock.onStopped()
    }
}

@Composable
fun App(store: Store, lock: AppLockViewModel) {
    var hosts by remember { mutableStateOf(store.loadHosts()) }
    var snippets by remember { mutableStateOf(store.loadSnippets()) }
    var sftpHost by remember { mutableStateOf<Host?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val terminal = OpenSessions.active.takeIf { OpenSessions.showTerminal }

    Surface(Modifier.fillMaxSize()) {
        // Il contenuto resta composto sotto la schermata di blocco, così il terminale non viene perso.
        Box(Modifier.fillMaxSize()) {
            val s = sftpHost
            when {
                terminal != null -> key(terminal.id) {
                    TerminalScreen(terminal, snippets, store) { OpenSessions.showTerminal = false }
                }
                s != null -> SftpScreen(s, store, lock) { sftpHost = null }
                else -> Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    TabRow(tab) {
                        Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.tab_hosts)) })
                        Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.tab_snippets)) })
                        Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.tab_settings)) })
                    }
                    when (tab) {
                        0 -> HostsScreen(
                            hosts,
                            { hosts = it; store.saveHosts(it) },
                            { OpenSessions.open(it) },
                            { sftpHost = it },
                            { store.removeHostKey("${it.hostname}:${it.port}") },
                            lock,
                        )
                        1 -> SnippetsScreen(snippets) { snippets = it; store.saveSnippets(it) }
                        else -> SettingsScreen(store, lock) {
                            hosts = store.loadHosts()
                            snippets = store.loadSnippets()
                        }
                    }
                }
            }
            HostKeyDialog(OpenSessions.hostKeys)
            if (lock.locked) LockScreen(lock)
        }
    }
}

@Composable
fun HostsScreen(
    hosts: List<Host>,
    onChange: (List<Host>) -> Unit,
    onConnect: (Host) -> Unit,
    onSftp: (Host) -> Unit,
    onForgetKey: (Host) -> Unit,
    lock: AppLockViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var hostname by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var distro by remember { mutableStateOf("") }

    fun resetForm() {
        editingId = null
        name = ""; hostname = ""; port = "22"; user = ""; pass = ""; key = ""; passphrase = ""
        distro = ""
        keyError = null
    }

    val keyFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }
                    val error = validatePrivateKey(text)?.let { context.getString(it) }
                    if (error == null) {
                        key = text!!.trim()
                        keyError = null
                    } else {
                        keyError = error
                    }
                } catch (e: Exception) {
                    keyError = context.getString(R.string.host_key_read_error, e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        if (OpenSessions.list.isNotEmpty()) {
            Text(stringResource(R.string.sessions_open), style = MaterialTheme.typography.labelLarge)
            SessionChipRow(Modifier.padding(vertical = 4.dp)) { OpenSessions.select(it) }
        }
        Button(onClick = {
            if (editing) {
                resetForm(); editing = false
            } else {
                resetForm(); editing = true
            }
        }) { Text(stringResource(if (editing) R.string.common_close else R.string.host_new)) }
        if (editing) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hostname, { hostname = it }, label = { Text(stringResource(R.string.host_address)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text(stringResource(R.string.host_port)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.host_user)) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.host_logo), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    FilterChip(selected = distro.isEmpty(), onClick = { distro = "" }, label = { Text(stringResource(R.string.host_logo_none)) })
                    Spacer(Modifier.width(8.dp))
                    Distros.forEach { d ->
                        FilterChip(
                            selected = distro == d.id,
                            onClick = { distro = d.id },
                            label = { Text(labelText(d.labelRes, d.label)) },
                            leadingIcon = { DistroBadge(d, size = 18) },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                OutlinedTextField(pass, { pass = it }, label = { Text(stringResource(R.string.common_password)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    key,
                    { key = it; keyError = null },
                    label = { Text(stringResource(R.string.host_private_key)) },
                    isError = keyError != null,
                    supportingText = { keyError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    lock.expectExternalActivity()
                    keyFileLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.host_import_key)) }
                OutlinedTextField(
                    passphrase,
                    { passphrase = it },
                    label = { Text(stringResource(R.string.host_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (key.isNotBlank()) {
                            validatePrivateKey(key)?.let {
                                keyError = context.getString(it)
                                return@Button
                            }
                        }
                        val saved = Host(
                            id = editingId ?: java.util.UUID.randomUUID().toString(),
                            name = name.ifBlank { hostname }, hostname = hostname,
                            port = port.toIntOrNull() ?: 22, username = user, password = pass,
                            privateKey = key, keyPassphrase = passphrase, distro = distro,
                        )
                        onChange(
                            if (editingId == null) hosts + saved
                            else hosts.map { if (it.id == saved.id) saved else it },
                        )
                        resetForm(); editing = false
                    },
                    enabled = hostname.isNotBlank() && user.isNotBlank(),
                ) { Text(stringResource(if (editingId == null) R.string.common_save else R.string.host_save_changes)) }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(hosts, key = { it.id }) { h ->
                val logo = distroById(h.distro)
                ListItem(
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
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { onConnect(h) }) { Text(stringResource(R.string.host_connect)) }
                                TextButton(onClick = { onSftp(h) }) { Text("SFTP") }
                                TextButton(onClick = {
                                    editingId = h.id
                                    name = h.name; hostname = h.hostname; port = h.port.toString()
                                    user = h.username; pass = h.password; key = h.privateKey
                                    passphrase = h.keyPassphrase; keyError = null; distro = h.distro
                                    editing = true
                                }) { Text(stringResource(R.string.host_edit)) }
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

@Composable
fun SnippetsScreen(snippets: List<Snippet>, onChange: (List<Snippet>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cmd, { cmd = it }, label = { Text(stringResource(R.string.snippets_command_hint)) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (name.isNotBlank() && cmd.isNotBlank()) {
                onChange(snippets + Snippet(name = name, command = cmd))
                name = ""; cmd = ""
            }
        }) { Text(stringResource(R.string.snippets_add)) }
        LazyColumn(Modifier.weight(1f)) {
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
