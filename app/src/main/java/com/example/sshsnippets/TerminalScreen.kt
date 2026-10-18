package com.example.sshsnippets

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


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

private fun styleOf(cell: AnsiCell, inverse: Boolean, theme: TerminalTheme): SpanStyle {
    val fg = ansiColor(cell.fg)
    val bg = ansiColor(cell.bg)
    val textColor = if (inverse) (bg ?: theme.bg) else (fg ?: theme.fg)
    val bgColor = if (inverse) (fg ?: theme.fg) else bg
    return SpanStyle(
        color = textColor,
        background = bgColor ?: Color.Unspecified,
        fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
    )
}

/** Groups adjacent cells with the same style into spans. The cursor cell is drawn inverted. */
private fun renderLine(cells: Array<AnsiCell>, cursorCol: Int, theme: TerminalTheme): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < cells.size) {
        val first = cells[i]
        val atCursor = i == cursorCol
        var j = i + 1
        while (j < cells.size && !atCursor && j != cursorCol &&
            cells[j].fg == first.fg && cells[j].bg == first.bg && cells[j].bold == first.bold
        ) j++
        val text = buildString { for (k in i until j) append(cells[k].ch) }
        pushStyle(styleOf(first, atCursor, theme))
        append(text)
        pop()
        i = j
    }
}

/** Pinch a due dita: chiama [onZoom] con il rapporto tra la distanza attuale e quella precedente. Con un dito non consuma nulla, così lo scroll resta attivo. */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        var prevSpan = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.filter { it.pressed }
            if (down.size >= 2) {
                val span = (down[0].position - down[1].position).getDistance()
                if (prevSpan > 0f && span > 0f) onZoom(span / prevSpan)
                prevSpan = span
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            } else {
                prevSpan = 0f
            }
        }
    }
}

@Composable
fun TerminalScreen(session: OpenSession, snippets: List<Snippet>, store: Store, onHosts: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val host = session.host
    val term = session.term
    var version by session.version
    val state = session.state.value
    val status = when (state) {
        SessionState.Connecting -> context.getString(R.string.terminal_connecting, host.hostname)
        SessionState.Connected -> context.getString(R.string.terminal_connected, host.hostname)
        SessionState.Disconnected -> context.getString(R.string.terminal_disconnected, host.hostname)
        is SessionState.Failed -> context.getString(R.string.terminal_error, state.message)
    }
    var input by session.input
    var pending by remember { mutableStateOf<Snippet?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var ctrl by session.ctrl
    var history by remember(host.id) { mutableStateOf(store.loadHistory(host.id)) }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }

    val theme = remember { themeById(store.terminalThemeId()) }
    var fontSp by remember { mutableFloatStateOf(store.terminalFontSize().toFloat()) }
    val sizeSp = fontSp.roundToInt()
    val textStyle = remember(sizeSp) { terminalTextStyle(fontById(store.terminalFontId()), sizeSp) }
    val onZoom: (Float) -> Unit = { factor ->
        fontSp = (fontSp * factor).coerceIn(MIN_TERMINAL_FONT_SIZE.toFloat(), MAX_TERMINAL_FONT_SIZE.toFloat())
        val rounded = fontSp.roundToInt()
        if (rounded != store.terminalFontSize()) store.setTerminalFontSize(rounded)
    }
    val measurer = rememberTextMeasurer()
    val probe = remember(measurer, textStyle) { measurer.measure(text = "M".repeat(10), style = textStyle).size }
    val cellW = (probe.width / 10).coerceAtLeast(1)
    val cellH = probe.height.coerceAtLeast(1)
    val padPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val listState = rememberLazyListState()
    val snap = remember(version) { synchronized(term) { term.snapshot() } }

    LaunchedEffect(areaSize, cellW, cellH) {
        if (areaSize.width <= padPx || areaSize.height <= padPx) return@LaunchedEffect
        val c = ((areaSize.width - padPx) / cellW).coerceAtLeast(20)
        val r = ((areaSize.height - padPx) / cellH).coerceAtLeast(5)
        synchronized(term) { term.resize(c, r) }
        version++
        session.cols = c
        session.rows = r
        session.ssh.resize(c, r)
    }

    LaunchedEffect(version) { listState.scrollToItem(0) }

    // Back torna alla lista host: la sessione resta aperta nella barra delle sessioni.
    BackHandler(onBack = onHosts)

    fun send(s: String) {
        scope.launch { session.ssh.send(s) }
    }

    // Pulisce subito lo schermo locale, poi invia ^L alla shell per ridisegnare il prompt.
    fun clearScreen() {
        synchronized(term) { term.feed("\u001b[H\u001b[2J") }
        scope.launch { session.version.intValue++ }
        send("\u000c")
    }

    @Composable
    fun StatusLine() {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            if (state == SessionState.Disconnected || state is SessionState.Failed) {
                TextButton({ OpenSessions.reconnect(session) }) {
                    Text(stringResource(R.string.terminal_reconnect), maxLines = 1, softWrap = false)
                }
            }
        }
    }

    @Composable
    fun Header(stacked: Boolean) {
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OpenSessions.list.forEach { s -> SessionChip(s) { OpenSessions.select(s) } }
                TextButton(onHosts) { Text(stringResource(R.string.session_new)) }
                StatusLine()
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SessionChipRow(Modifier.weight(1f)) { OpenSessions.select(it) }
                    TextButton(onHosts) { Text(stringResource(R.string.session_new)) }
                }
                StatusLine()
            }
        }
    }


    @Composable
    fun TerminalArea(modifier: Modifier) {
        Box(modifier.background(theme.bg).pinchToZoom(onZoom).onSizeChanged { areaSize = it }) {
            LazyColumn(
                Modifier.fillMaxSize().padding(8.dp),
                state = listState,
                reverseLayout = true,
            ) {
                items(snap.lines.size) { i ->
                    val idx = snap.lines.size - 1 - i
                    val cursor = if (idx == snap.cursorLine) snap.cursorCol else -1
                    Text(
                        renderLine(snap.lines[idx], cursor, theme),
                        style = textStyle,
                        softWrap = false,
                    )
                }
            }
        }
    }

    @Composable
    fun ChipLine(vertical: Boolean, content: @Composable () -> Unit) {
        if (vertical) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { content() }
        }
    }

    @Composable
    fun SnippetsLine(vertical: Boolean) {
        ChipLine(vertical) {
            if (snippets.isEmpty()) {
                    Text(
                        stringResource(R.string.terminal_no_snippets),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
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
            }
        }
    }

    @Composable
    fun KeysLine(vertical: Boolean) {
        ChipLine(vertical) {
            FilterChip(selected = ctrl, onClick = { ctrl = !ctrl }, label = { Text(stringResource(R.string.terminal_ctrl)) })
            listOf(
                "Esc" to "\u001b", "Tab" to "\t", "↑" to "\u001b[A", "↓" to "\u001b[B",
                "←" to "\u001b[D", "→" to "\u001b[C", "^C" to "\u0003", "^D" to "\u0004",
            ).forEach { (label, value) ->
                OutlinedButton(onClick = { send(value) }, contentPadding = PaddingValues(8.dp)) { Text(label) }
            }
            OutlinedButton(onClick = { clearScreen() }, contentPadding = PaddingValues(8.dp)) { Text("^l") }
        }
    }

    @Composable
    fun SuggestionsLine(vertical: Boolean) {
        val suggestions = if (ctrl || input.text.isBlank()) emptyList() else history.suggest(input.text)
        if (suggestions.isNotEmpty()) {
            ChipLine(vertical) {
                suggestions.forEach { c ->
                    AssistChip(
                        onClick = { input = TextFieldValue(c, TextRange(c.length)) },
                        label = { Text(c, maxLines = 1) },
                    )
                }
            }
        }
    }

    @Composable
    fun InputRow(compact: Boolean = false) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true)
            }
            Button(
                modifier = if (compact) Modifier.height(40.dp) else Modifier,
                contentPadding = if (compact) PaddingValues(horizontal = 12.dp, vertical = 0.dp) else ButtonDefaults.ContentPadding,
                onClick = {
                if (ctrl && input.text.length == 1) {
                    ctrl = false
                    send((input.text[0].uppercaseChar().code and 0x1f).toChar().toString())
                } else {
                    val command = input.text.trim()
                    if (command.isNotEmpty()) {
                        store.recordCommand(host.id, command)
                        history = store.loadHistory(host.id)
                    }
                    send(input.text + "\n")
                }
                input = TextFieldValue()
            }) { Text(stringResource(R.string.terminal_send)) }
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (landscape) {
        Row(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TerminalArea(Modifier.weight(1f).fillMaxWidth())
                InputRow(compact = true)
            }
            Column(
                Modifier.width(200.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Header(stacked = true)
                SnippetsLine(vertical = true)
                KeysLine(vertical = true)
                SuggestionsLine(vertical = true)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Header(stacked = false)
            TerminalArea(Modifier.weight(1f).fillMaxWidth())
            SnippetsLine(vertical = false)
            KeysLine(vertical = false)
            SuggestionsLine(vertical = false)
            InputRow()
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
                }) { Text(stringResource(R.string.terminal_run)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

}
