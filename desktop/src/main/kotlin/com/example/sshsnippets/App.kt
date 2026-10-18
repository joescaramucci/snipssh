package com.example.sshsnippets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun DesktopApp(store: Store) {
    var hosts by remember { mutableStateOf(store.loadHosts()) }
    var snippets by remember { mutableStateOf(store.loadSnippets()) }
    var tab by remember { mutableStateOf(0) }
    var appThemeId by remember { mutableStateOf(store.appThemeId()) }
    val appTheme = appThemeById(appThemeId)
    val active = OpenSessions.active.takeIf { OpenSessions.showTerminal }
    // Vista affiancata: `panes` sono gli id delle schede a vista, da sinistra a destra.
    var split by remember { mutableStateOf(false) }
    val panes = remember { mutableStateListOf<String>() }
    val paneWeights = remember { mutableStateMapOf<String, Float>() }
    var focusedPane by remember { mutableStateOf<String?>(null) }
    val tabIds = OpenSessions.list.map { it.id }
    LaunchedEffect(split, OpenSessions.activeId, tabIds) {
        if (!split) return@LaunchedEffect
        panes.removeAll { it !in tabIds }
        val current = OpenSessions.activeId ?: return@LaunchedEffect
        if (current in panes) {
            focusedPane = current
            return@LaunchedEffect
        }
        // Una scheda non ancora a vista entra se c'è spazio, altrimenti prende il posto di quella con il fuoco.
        val slot = panes.indexOf(focusedPane)
        if (panes.size < SPLIT_MAX_PANES) panes.add(current)
        else panes[if (slot >= 0) slot else panes.lastIndex] = current
        focusedPane = current
    }
    fun toggleSplit() {
        if (split) {
            split = false
            return
        }
        panes.clear()
        OpenSessions.active?.let { panes.add(it.id) }
        OpenSessions.list.filter { it.id !in panes }.take(SPLIT_OPEN_PANES - panes.size).forEach { panes.add(it.id) }
        focusedPane = OpenSessions.activeId
        split = true
    }
    val paneTabs = panes.mapNotNull { id -> OpenSessions.list.firstOrNull { it.id == id } }

    MaterialTheme(colorScheme = appTheme.colors) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(appTheme.brush)) {
                Column(Modifier.fillMaxSize()) {
                    CreditsBar()
                    SessionBar(
                        split = split,
                        canSplit = OpenSessions.list.size >= 2,
                        onToggleSplit = { toggleSplit() },
                        onSelect = { OpenSessions.select(it) },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (split && active != null && paneTabs.isNotEmpty()) {
                            SplitView(paneTabs, OpenSessions.activeId, snippets, store, { snippets = it; store.saveSnippets(it) }, paneWeights) { OpenSessions.select(it) }
                        } else when (active) {
                            is OpenSession -> key(active.id) { TerminalScreen(active, snippets, store, { snippets = it; store.saveSnippets(it) }) }
                            is OpenSftp -> key(active.id) { SftpScreen(active) }
                            else -> Column(Modifier.fillMaxSize()) {
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
                                        { OpenSessions.openSftp(it) },
                                        { store.removeHostKey("${it.hostname}:${it.port}") },
                                    )
                                    1 -> SnippetsScreen(snippets) { snippets = it; store.saveSnippets(it) }
                                    else -> SettingsScreen(
                                        store,
                                        appThemeId,
                                        onAppThemeChange = { appThemeId = it; store.setAppThemeId(it) },
                                        onRestored = {
                                            hosts = store.loadHosts()
                                            snippets = store.loadSnippets()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                HostKeyDialog(OpenSessions.hostKeys)
                DesktopPlatform.notice?.let { text ->
                    AlertDialog(
                        onDismissRequest = { DesktopPlatform.notice = null },
                        text = { Text(text) },
                        confirmButton = {
                            TextButton(onClick = { DesktopPlatform.notice = null }) { Text(stringResource(R.string.common_close)) }
                        },
                    )
                }
            }
        }
    }
}

/** Barra in alto, sempre visibile: porta alla lista host, mostra le schede aperte e la vista affiancata. */
@Composable
private fun SessionBar(split: Boolean, canSplit: Boolean, onToggleSplit: () -> Unit, onSelect: (OpenTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { OpenSessions.showTerminal = false }) { Text(stringResource(R.string.session_new)) }
        SessionChipRow(Modifier.weight(1f)) { onSelect(it) }
        if (canSplit) {
            TextButton(onClick = onToggleSplit) {
                Text(stringResource(if (split) R.string.split_off else R.string.split_toggle))
            }
        }
    }
}

