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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
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
    // Vista affiancata: la scheda attiva sta a sinistra, `other` a destra.
    var split by remember { mutableStateOf(false) }
    var rightId by remember { mutableStateOf<String?>(null) }
    val other = active?.let { a ->
        OpenSessions.list.firstOrNull { it.id == rightId && it.id != a.id }
            ?: OpenSessions.list.lastOrNull { it.id != a.id }
    }
    fun focusTab(target: OpenTab) {
        // Scegliere la scheda di destra la scambia con quella attiva.
        if (target.id == other?.id) rightId = active?.id
        OpenSessions.select(target)
    }

    MaterialTheme(colorScheme = appTheme.colors) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(appTheme.brush)) {
                Column(Modifier.fillMaxSize()) {
                    CreditsBar()
                    SessionBar(
                        split = split,
                        canSplit = OpenSessions.list.size >= 2,
                        onToggleSplit = { split = !split },
                        onSelect = { focusTab(it) },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (split && active != null) {
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    TabPane(active, snippets, store, autoFocus = true)
                                }
                                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().pointerInput(active.id, other?.id) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (other != null && event.changes.any { it.changedToDown() }) focusTab(other)
                                            }
                                        }
                                    },
                                ) {
                                    if (other != null) TabPane(other, snippets, store, autoFocus = false)
                                    else Text(
                                        stringResource(R.string.split_empty),
                                        Modifier.align(Alignment.Center).padding(16.dp),
                                    )
                                }
                            }
                        } else when (active) {
                            is OpenSession -> key(active.id) { TerminalScreen(active, snippets, store) }
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

@Composable
private fun TabPane(tab: OpenTab, snippets: List<Snippet>, store: Store, autoFocus: Boolean) {
    when (tab) {
        is OpenSession -> key(tab.id) { TerminalScreen(tab, snippets, store, autoFocus) }
        is OpenSftp -> key(tab.id) { SftpScreen(tab) }
    }
}
