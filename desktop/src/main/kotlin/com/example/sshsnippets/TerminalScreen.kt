package com.example.sshsnippets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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

private fun styleOf(cell: AnsiCell, inverse: Boolean, selected: Boolean, theme: TerminalTheme): SpanStyle {
    val fg = ansiColor(cell.fg)
    val bg = ansiColor(cell.bg)
    val textColor = if (inverse) (bg ?: theme.bg) else (fg ?: theme.fg)
    val bgColor = when {
        inverse -> fg ?: theme.fg
        selected -> theme.fg.copy(alpha = 0.35f)
        else -> bg
    }
    return SpanStyle(
        color = textColor,
        background = bgColor ?: Color.Unspecified,
        fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
    )
}

/** Raggruppa le celle adiacenti con lo stesso stile. La cella del cursore è invertita, quelle selezionate evidenziate. */
private fun renderLine(cells: Array<AnsiCell>, cursorCol: Int, selection: IntRange, theme: TerminalTheme): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < cells.size) {
            val first = cells[i]
            val atCursor = i == cursorCol
            val selected = i in selection
            var j = i + 1
            while (j < cells.size && !atCursor && j != cursorCol && (j in selection) == selected &&
                cells[j].fg == first.fg && cells[j].bg == first.bg && cells[j].bold == first.bold
            ) j++
            pushStyle(styleOf(first, atCursor, selected, theme))
            // Il motore di testo non disegna lo sfondo degli spazi a fine riga: uno spazio non separabile sì.
            append(buildString { for (k in i until j) append(if (cells[k].ch == ' ' && (atCursor || selected)) '\u00A0' else cells[k].ch) })
            pop()
            i = j
        }
    }

private data class Cell(val line: Int, val col: Int) : Comparable<Cell> {
    override fun compareTo(other: Cell) = compareValuesBy(this, other, { it.line }, { it.col })
}

/** Intervallo di colonne selezionate nella riga [line], vuoto se la riga è fuori dalla selezione. */
private fun selectedCols(selection: Pair<Cell, Cell>?, line: Int): IntRange {
    val (a, b) = selection ?: return IntRange.EMPTY
    val from = minOf(a, b)
    val to = maxOf(a, b)
    if (line < from.line || line > to.line) return IntRange.EMPTY
    return (if (line == from.line) from.col else 0)..(if (line == to.line) to.col else Int.MAX_VALUE)
}

private fun selectedText(lines: List<Array<AnsiCell>>, selection: Pair<Cell, Cell>?): String {
    val (a, b) = selection ?: return ""
    val from = minOf(a, b)
    val to = maxOf(a, b)
    return (from.line..to.line).filter { it in lines.indices }.joinToString("\n") { line ->
        val range = selectedCols(selection, line)
        val cells = lines[line]
        buildString { for (c in range.first..minOf(range.last, cells.lastIndex)) append(cells[c].ch) }.trimEnd()
    }
}

/** Tasti che da soli non producono nulla: non devono né essere inviati né annullare la selezione. */
private val ModifierKeys = setOf(
    Key.ShiftLeft, Key.ShiftRight, Key.CtrlLeft, Key.CtrlRight, Key.AltLeft, Key.AltRight,
    Key.MetaLeft, Key.MetaRight, Key.CapsLock, Key.NumLock, Key.ScrollLock,
)

private val FunctionKeys = listOf(
    "\u001bOP", "\u001bOQ", "\u001bOR", "\u001bOS", "\u001b[15~", "\u001b[17~",
    "\u001b[18~", "\u001b[19~", "\u001b[20~", "\u001b[21~", "\u001b[23~", "\u001b[24~",
)

/** Sequenza di byte per un tasto speciale, o null se il tasto non ne ha una. */
private fun specialKey(e: KeyEvent): String? = when (e.key) {
    Key.Enter, Key.NumPadEnter -> "\r"
    Key.Backspace -> "\u007f"
    Key.Tab -> if (e.isShiftPressed) "\u001b[Z" else "\t"
    Key.Escape -> "\u001b"
    Key.DirectionUp -> "\u001b[A"
    Key.DirectionDown -> "\u001b[B"
    Key.DirectionRight -> "\u001b[C"
    Key.DirectionLeft -> "\u001b[D"
    Key.MoveHome -> "\u001b[H"
    Key.MoveEnd -> "\u001b[F"
    Key.PageUp -> "\u001b[5~"
    Key.PageDown -> "\u001b[6~"
    Key.Delete -> "\u001b[3~"
    Key.Insert -> "\u001b[2~"
    Key.F1 -> FunctionKeys[0]
    Key.F2 -> FunctionKeys[1]
    Key.F3 -> FunctionKeys[2]
    Key.F4 -> FunctionKeys[3]
    Key.F5 -> FunctionKeys[4]
    Key.F6 -> FunctionKeys[5]
    Key.F7 -> FunctionKeys[6]
    Key.F8 -> FunctionKeys[7]
    Key.F9 -> FunctionKeys[8]
    Key.F10 -> FunctionKeys[9]
    Key.F11 -> FunctionKeys[10]
    Key.F12 -> FunctionKeys[11]
    else -> null
}

/** Sequenza per Ctrl+lettera (da 1 a 26) o Ctrl+Spazio, altrimenti null. */
private fun controlKey(e: KeyEvent): String? {
    val vk = e.key.nativeKeyCode
    return when {
        vk in 'A'.code..'Z'.code -> (vk - 'A'.code + 1).toChar().toString()
        e.key == Key.Spacebar -> "\u0000"
        else -> null
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TerminalScreen(session: OpenSession, snippets: List<Snippet>, store: Store, autoFocus: Boolean = true) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val host = session.host
    val term = session.term
    val version by session.version
    val state = session.state.value
    val status = when (state) {
        SessionState.Connecting -> stringResource(R.string.terminal_connecting, host.hostname)
        SessionState.Connected -> stringResource(R.string.terminal_connected, host.hostname)
        SessionState.Disconnected -> stringResource(R.string.terminal_disconnected, host.hostname)
        is SessionState.Failed -> stringResource(R.string.terminal_error, state.message)
    }
    var pending by remember { mutableStateOf<Snippet?>(null) }
    val values = remember { mutableStateMapOf<String, String>() }
    var history by remember(host.id) { mutableStateOf(store.loadHistory(host.id)) }
    // Riga digitata finora, ricostruita dai tasti premuti: serve solo per i suggerimenti dalla cronologia.
    var typed by remember { mutableStateOf("") }
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var cols by remember { mutableIntStateOf(80) }
    var visibleRows by remember { mutableIntStateOf(24) }
    var scrollBack by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf<Pair<Cell, Cell>?>(null) }
    var snippetsWidth by remember { mutableFloatStateOf(store.snippetsWidthDp().toFloat()) }
    val focus = remember { FocusRequester() }

    val theme = remember { themeById(store.terminalThemeId()) }
    var fontSp by remember { mutableFloatStateOf(store.terminalFontSize().toFloat()) }
    val sizeSp = fontSp.roundToInt()
    val textStyle = remember(sizeSp) { terminalTextStyle(fontById(store.terminalFontId()), sizeSp) }
    fun zoom(delta: Int) {
        fontSp = (fontSp + delta).coerceIn(MIN_TERMINAL_FONT_SIZE.toFloat(), MAX_TERMINAL_FONT_SIZE.toFloat())
        store.setTerminalFontSize(fontSp.roundToInt())
    }
    val measurer = rememberTextMeasurer()
    val probe = remember(measurer, textStyle) { measurer.measure(text = "M".repeat(10), style = textStyle).size }
    val cellW = (probe.width / 10).coerceAtLeast(1)
    val cellH = probe.height.coerceAtLeast(1)
    val density = LocalDensity.current
    val padPx = with(density) { 16.dp.roundToPx() }
    val rowHeight = with(density) { cellH.toDp() }
    val snap = remember(version) { synchronized(term) { term.snapshot() } }
    val maxScrollBack = (snap.lines.size - visibleRows).coerceAtLeast(0)
    val firstVisible = (snap.lines.size - visibleRows - scrollBack.coerceIn(0, maxScrollBack)).coerceAtLeast(0)
    // I gestori di tasti e rotella vivono più a lungo di una ricomposizione: leggono questi valori sempre aggiornati.
    val snapNow by rememberUpdatedState(snap)
    val maxScrollBackNow by rememberUpdatedState(maxScrollBack)

    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }

    LaunchedEffect(areaSize, cellW, cellH) {
        if (areaSize.width <= padPx || areaSize.height <= padPx) return@LaunchedEffect
        val c = ((areaSize.width - padPx) / cellW).coerceAtLeast(20)
        val r = ((areaSize.height - padPx) / cellH).coerceAtLeast(5)
        synchronized(term) { term.resize(c, r) }
        cols = c
        visibleRows = r
        session.cols = c
        session.rows = r
        session.version.intValue++
        session.ssh.resize(c, r)
    }

    // Nuovo output: torna in fondo, come un terminale normale.
    LaunchedEffect(version) { scrollBack = 0 }

    fun send(s: String) {
        scope.launch { session.ssh.send(s) }
    }

    fun copySelection() {
        val text = selectedText(snapNow.lines, selection)
        if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text))
    }

    fun paste() {
        val text = clipboard.getText()?.text?.replace("\r\n", "\r")?.replace('\n', '\r') ?: return
        typed = if (text.contains('\r')) "" else typed + text
        send(text)
    }

    /** Registra il comando completo e azzera la riga digitata. */
    fun submitLine() {
        val command = typed.trim()
        if (command.isNotEmpty()) {
            store.recordCommand(host.id, command)
            history = store.loadHistory(host.id)
        }
        typed = ""
    }

    fun onKey(e: KeyEvent): Boolean {
        if (e.key in ModifierKeys) return false
        if (e.type != KeyEventType.KeyDown) return e.type == KeyEventType.KeyUp && specialKey(e) != null
        val ctrl = e.isCtrlPressed
        val alt = e.isAltPressed
        val shift = e.isShiftPressed
        val code = e.utf16CodePoint
        val printable = code >= 32 && code != 127
        // Con AltGr Windows segnala Ctrl+Alt insieme al carattere: va trattato come testo normale.
        val altGr = ctrl && alt && printable

        if (ctrl && shift && e.key == Key.C) { copySelection(); return true }
        if (ctrl && shift && e.key == Key.V) { paste(); return true }
        if (ctrl && !shift && e.key == Key.Insert) { copySelection(); return true }
        if (shift && !ctrl && e.key == Key.Insert) { paste(); return true }
        if (ctrl && !shift && e.key == Key.C && selection != null) { copySelection(); selection = null; return true }
        if (ctrl && !alt) {
            when (e.key) {
                Key.Equals, Key.Plus, Key.NumPadAdd -> { zoom(+1); return true }
                Key.Minus, Key.NumPadSubtract -> { zoom(-1); return true }
                Key.Zero, Key.NumPad0 -> { fontSp = DEFAULT_TERMINAL_FONT_SIZE.toFloat(); store.setTerminalFontSize(DEFAULT_TERMINAL_FONT_SIZE); return true }
            }
        }
        if (shift && e.key == Key.PageUp) { scrollBack = (scrollBack + visibleRows / 2).coerceAtMost(maxScrollBackNow); return true }
        if (shift && e.key == Key.PageDown) { scrollBack = (scrollBack - visibleRows / 2).coerceAtLeast(0); return true }

        selection = null
        scrollBack = 0

        if (!altGr) {
            specialKey(e)?.let {
                when (e.key) {
                    Key.Enter, Key.NumPadEnter -> submitLine()
                    Key.Backspace -> typed = typed.dropLast(1)
                    else -> typed = ""
                }
                send(it)
                return true
            }
            if (ctrl) {
                controlKey(e)?.let {
                    typed = ""
                    send(it)
                    return true
                }
                return false
            }
        }
        if (printable) {
            val ch = String(Character.toChars(code))
            typed += ch
            send(if (alt && !altGr) "\u001b$ch" else ch)
            return true
        }
        return false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            if (state == SessionState.Disconnected || state is SessionState.Failed) {
                TextButton({ OpenSessions.reconnect(session) }) {
                    Text(stringResource(R.string.terminal_reconnect), maxLines = 1, softWrap = false)
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            val geometry by rememberUpdatedState(Triple(firstVisible, cols, visibleRows))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(theme.bg)
                    .onSizeChanged { areaSize = it }
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent(::onKey)
                    .onPointerEvent(PointerEventType.Scroll) { ev ->
                        val dy = ev.changes.first().scrollDelta.y
                        if (ev.keyboardModifiers.isCtrlPressed) {
                            if (dy != 0f) zoom(if (dy < 0) 1 else -1)
                        } else {
                            scrollBack = (scrollBack - (dy * 3).roundToInt()).coerceIn(0, maxScrollBackNow)
                        }
                    }
                    .pointerInput(cellW, cellH) {
                        fun cellAt(p: Offset): Cell {
                            val (first, columns, rows) = geometry
                            val pad = padPx / 2f
                            val col = ((p.x - pad) / cellW).toInt().coerceIn(0, columns - 1)
                            val row = ((p.y - pad) / cellH).toInt().coerceIn(0, rows - 1)
                            return Cell(first + row, col)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            focus.requestFocus()
                            val start = cellAt(down.position)
                            selection = null
                            var moved = false
                            while (true) {
                                val change = awaitPointerEvent().changes.first()
                                if (!change.pressed) break
                                val p = cellAt(change.position)
                                if (moved || p != start) {
                                    moved = true
                                    selection = start to p
                                }
                            }
                        }
                    },
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    for (row in 0 until visibleRows) {
                        val idx = firstVisible + row
                        if (idx >= snap.lines.size) break
                        val cursor = if (idx == snap.cursorLine) snap.cursorCol else -1
                        Text(
                            renderLine(snap.lines[idx], cursor, selectedCols(selection, idx), theme),
                            style = textStyle,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.height(rowHeight),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            snippetsWidth = (snippetsWidth - delta / density.density).coerceIn(160f, 480f)
                        },
                        onDragStopped = { store.setSnippetsWidthDp(snippetsWidth.roundToInt()) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            }
            Column(
                Modifier.width(snippetsWidth.dp).fillMaxHeight().padding(8.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.tab_snippets), style = MaterialTheme.typography.titleSmall)
                if (snippets.isEmpty()) {
                    Text(stringResource(R.string.terminal_no_snippets), style = MaterialTheme.typography.labelSmall)
                }
                snippets.forEach { s ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (s.variables().isEmpty()) {
                                send(s.render(emptyMap()) + if (s.sendEnter) "\r" else "")
                                focus.requestFocus()
                            } else {
                                values.clear()
                                pending = s
                            }
                        },
                    ) { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        val suggestions = if (typed.isBlank()) emptyList() else history.suggest(typed)
        if (suggestions.isNotEmpty()) {
            HorizontalDivider()
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { c ->
                    AssistChip(
                        onClick = {
                            send(c.substring(typed.length))
                            typed = c
                            focus.requestFocus()
                        },
                        label = { Text(c, maxLines = 1) },
                    )
                }
            }
        }
    }

    pending?.let { s ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(s.name) },
            text = {
                Column {
                    s.variables().forEach { v ->
                        OutlinedTextField(values[v].orEmpty(), { values[v] = it }, label = { Text(v) }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    send(s.render(values) + if (s.sendEnter) "\r" else "")
                    pending = null
                    focus.requestFocus()
                }) { Text(stringResource(R.string.terminal_run)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
