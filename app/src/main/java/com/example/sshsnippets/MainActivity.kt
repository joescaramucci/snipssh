package com.example.sshsnippets

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private val lock: AppLockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
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
    var active by remember { mutableStateOf<Host?>(null) }
    var sftpHost by remember { mutableStateOf<Host?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    Surface(Modifier.fillMaxSize()) {
        // Il contenuto resta composto sotto la schermata di blocco, così il terminale non viene perso.
        Box(Modifier.fillMaxSize()) {
            val h = active
            val s = sftpHost
            when {
                h != null -> TerminalScreen(h, snippets, store) { active = null }
                s != null -> SftpScreen(s, store, lock) { sftpHost = null }
                else -> Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    TabRow(tab) {
                        Tab(tab == 0, { tab = 0 }, text = { Text("Host") })
                        Tab(tab == 1, { tab = 1 }, text = { Text("Snippet") })
                        Tab(tab == 2, { tab = 2 }, text = { Text("Impostazioni") })
                    }
                    when (tab) {
                        0 -> HostsScreen(
                            hosts,
                            { hosts = it; store.saveHosts(it) },
                            { active = it },
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
                    val error = validatePrivateKey(text)
                    if (error == null) {
                        key = text!!.trim()
                        keyError = null
                    } else {
                        keyError = error
                    }
                } catch (e: Exception) {
                    keyError = "Impossibile leggere il file: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Button(onClick = {
            if (editing) {
                resetForm(); editing = false
            } else {
                resetForm(); editing = true
            }
        }) { Text(if (editing) "Chiudi" else "Nuovo host") }
        if (editing) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(hostname, { hostname = it }, label = { Text("Indirizzo") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text("Porta") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("Utente") }, modifier = Modifier.fillMaxWidth())
                Text("Logo sistema", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                    FilterChip(selected = distro.isEmpty(), onClick = { distro = "" }, label = { Text("Nessuno") })
                    Spacer(Modifier.width(8.dp))
                    Distros.forEach { d ->
                        FilterChip(
                            selected = distro == d.id,
                            onClick = { distro = d.id },
                            label = { Text(d.label) },
                            leadingIcon = { DistroBadge(d, size = 18) },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    key,
                    { key = it; keyError = null },
                    label = { Text("Chiave privata (PEM)") },
                    isError = keyError != null,
                    supportingText = { keyError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    lock.expectExternalActivity()
                    keyFileLauncher.launch(arrayOf("*/*"))
                }) { Text("Importa file chiave") }
                OutlinedTextField(
                    passphrase,
                    { passphrase = it },
                    label = { Text("Passphrase della chiave (se presente)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (key.isNotBlank()) {
                            validatePrivateKey(key)?.let {
                                keyError = it
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
                ) { Text(if (editingId == null) "Salva" else "Salva modifiche") }
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
                                TextButton(onClick = { onConnect(h) }) { Text("Connetti") }
                                TextButton(onClick = { onSftp(h) }) { Text("SFTP") }
                                TextButton(onClick = {
                                    editingId = h.id
                                    name = h.name; hostname = h.hostname; port = h.port.toString()
                                    user = h.username; pass = h.password; key = h.privateKey
                                    passphrase = h.keyPassphrase; keyError = null; distro = h.distro
                                    editing = true
                                }) { Text("Modifica") }
                                TextButton(onClick = { onForgetKey(h) }) { Text("Dimentica chiave") }
                                if (h.privateKey.isNotBlank()) {
                                    TextButton(onClick = {
                                        onChange(hosts.map { if (it.id == h.id) it.copy(privateKey = "", keyPassphrase = "") else it })
                                    }) { Text("Rimuovi chiave") }
                                }
                                TextButton(onClick = { onChange(hosts - h) }) { Text("Elimina") }
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
        OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cmd, { cmd = it }, label = { Text("Comando, variabili: {{nome}}") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (name.isNotBlank() && cmd.isNotBlank()) {
                onChange(snippets + Snippet(name = name, command = cmd))
                name = ""; cmd = ""
            }
        }) { Text("Aggiungi snippet") }
        LazyColumn(Modifier.weight(1f)) {
            items(snippets, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text(s.name) },
                    supportingContent = { Text(s.command, fontFamily = FontFamily.Monospace) },
                    trailingContent = { TextButton(onClick = { onChange(snippets - s) }) { Text("Elimina") } },
                )
                HorizontalDivider()
            }
        }
    }
}
