package com.example.sshsnippets

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val TerminalTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 15.sp)
private val DefaultFg = Color(0xFFE6E6E6)
private val DefaultBg = Color(0xFF121212)

private val Ansi16 = listOf(
    0xFF000000, 0xFFCD0000, 0xFF00CD00, 0xFFCDCD00, 0xFF0000EE, 0xFFCD00CD, 0xFF00CDCD, 0xFFE5E5E5,
    0xFF7F7F7F, 0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF5C5CFF, 0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF,
).map { Color(it) }

private fun ansiColor(idx: Int): Color? = when {
    idx == ANSI_DEFAULT -> null
    idx < 16 -> Ansi16[idx]
    idx < 232 -> {
        val n = idx - 16
        fun level(v: Int) = if (v == 0) 0 else 55 + v * 40
        Color(level(n / 36), level((n / 6) % 6), level(n % 6))
    }
    else -> {
        val g = 8 + (idx - 232) * 10
        Color(g, g, g)
    }
}

private fun styleOf(cell: AnsiCell, inverse: Boolean): SpanStyle {
    val fg = ansiColor(cell.fg)
    val bg = ansiColor(cell.bg)
    val textColor = if (inverse) (bg ?: DefaultBg) else (fg ?: DefaultFg)
    val bgColor = if (inverse) (fg ?: DefaultFg) else bg
    return SpanStyle(
        color = textColor,
        background = bgColor ?: Color.Unspecified,
        fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
    )
}

/** Groups adjacent cells with the same style into spans. The cursor cell is drawn inverted. */
private fun renderLine(cells: Array<AnsiCell>, cursorCol: Int): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < cells.size) {
        val first = cells[i]
        val atCursor = i == cursorCol
        var j = i + 1
        while (j < cells.size && !atCursor && j != cursorCol &&
            cells[j].fg == first.fg && cells[j].bg == first.bg && cells[j].bold == first.bold
        ) j++
        val text = buildString { for (k in i until j) append(cells[k].ch) }
        pushStyle(styleOf(first, atCursor))
        append(text)
        pop()
        i = j
    }
}

@Composable
fun TerminalScreen(host: Host, snippets: List<Snippet>, store: Store, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ssh = remember { SshSession(host, store) }
    val term = remember { AnsiTerminal(80, 24) }
    var version by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Connessione a ${host.hostname}...") }
    var input by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Snippet?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var ctrl by remember { mutableStateOf(false) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    val hostKeys = rememberHostKeyPrompter()

    val measurer = rememberTextMeasurer()
    val probe = remember(measurer) { measurer.measure(text = "M".repeat(10), style = TerminalTextStyle).size }
    val cellW = (probe.width / 10).coerceAtLeast(1)
    val cellH = probe.height.coerceAtLeast(1)
    val padPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val listState = rememberLazyListState()
    val snap = remember(version) { synchronized(term) { term.snapshot() } }

    LaunchedEffect(Unit) {
        try {
            ssh.connect(
                confirmHostKey = { fingerprint -> hostKeys.confirm(host.hostname, fingerprint) },
                onOutput = { chunk ->
                    synchronized(term) { term.feed(chunk) }
                    scope.launch(Dispatchers.Main) { version++ }
                },
                onClosed = {
                    synchronized(term) { term.feed("\r\n[disconnesso]\r\n") }
                    scope.launch(Dispatchers.Main) { status = "Disconnesso"; version++ }
                },
            )
            status = "Connesso a ${host.hostname}"
        } catch (e: Exception) {
            status = "Errore: ${e.message}"
        }
    }

    LaunchedEffect(areaSize, cellW, cellH) {
        if (areaSize.width <= padPx || areaSize.height <= padPx) return@LaunchedEffect
        val c = ((areaSize.width - padPx) / cellW).coerceAtLeast(20)
        val r = ((areaSize.height - padPx) / cellH).coerceAtLeast(5)
        synchronized(term) { term.resize(c, r) }
        version++
        ssh.resize(c, r)
    }

    LaunchedEffect(version) { listState.scrollToItem(0) }

    DisposableEffect(Unit) {
        onDispose { CoroutineScope(Dispatchers.IO).launch { ssh.close() } }
    }

    fun send(s: String) {
        scope.launch { ssh.send(s) }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onExit) { Text("< ${host.name}") }
            Spacer(Modifier.weight(1f))
            Text(status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(DefaultBg).onSizeChanged { areaSize = it }) {
            LazyColumn(
                Modifier.fillMaxSize().padding(8.dp),
                state = listState,
                reverseLayout = true,
            ) {
                items(snap.lines.size) { i ->
                    val idx = snap.lines.size - 1 - i
                    val cursor = if (idx == snap.cursorLine) snap.cursorCol else -1
                    Text(
                        renderLine(snap.lines[idx], cursor),
                        style = TerminalTextStyle,
                        softWrap = false,
                    )
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            snippets.forEach { s ->
                AssistChip(
                    onClick = {
                        if (s.variables().isEmpty()) send(s.render(emptyMap()) + if (s.sendEnter) "\n" else "")
                        else {
                            values.clear()
                            pending = s
                        }
                    },
                    label = { Text(s.name) },
                )
                Spacer(Modifier.width(4.dp))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            FilterChip(selected = ctrl, onClick = { ctrl = !ctrl }, label = { Text("Ctrl") })
            listOf(
                "Esc" to "\u001b", "Tab" to "\t", "↑" to "\u001b[A", "↓" to "\u001b[B",
                "←" to "\u001b[D", "→" to "\u001b[C", "^C" to "\u0003", "^D" to "\u0004",
            ).forEach { (label, value) ->
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { send(value) }, contentPadding = PaddingValues(8.dp)) { Text(label) }
            }
        }
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true)
            Button(onClick = {
                val text = if (ctrl && input.length == 1) {
                    ctrl = false
                    (input[0].uppercaseChar().code and 0x1f).toChar().toString()
                } else {
                    input + "\n"
                }
                send(text)
                input = ""
            }) { Text("Invia") }
        }
    }

    pending?.let { s ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(s.name) },
            text = {
                Column {
                    s.variables().forEach { v ->
                        OutlinedTextField(values[v].orEmpty(), { values[v] = it }, label = { Text(v) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    send(s.render(values) + if (s.sendEnter) "\n" else "")
                    pending = null
                }) { Text("Esegui") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annulla") } },
        )
    }

    HostKeyDialog(hostKeys)
}
