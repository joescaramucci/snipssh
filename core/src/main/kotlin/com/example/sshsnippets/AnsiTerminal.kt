package com.example.sshsnippets

const val ANSI_DEFAULT = -1

data class AnsiCell(
    val ch: Char = ' ',
    val fg: Int = ANSI_DEFAULT,
    val bg: Int = ANSI_DEFAULT,
    val bold: Boolean = false,
)

/**
 * Minimal VT100/xterm screen with scrollback: SGR colours, cursor movement, erase,
 * scroll regions (DECSTBM), insert/delete lines and characters, saved cursor and the alternate screen.
 * Not thread-safe: callers must synchronize on the instance.
 */
class AnsiTerminal(private var cols: Int, private var rows: Int, private val maxLines: Int = 2000) {

    /** [altScreen]: è attivo il buffer alternativo (nano, less). [appCursorKeys]: l'app vuole le frecce come ESC O x. */
    class Snapshot(
        val lines: List<Array<AnsiCell>>,
        val cursorLine: Int,
        val cursorCol: Int,
        val altScreen: Boolean = false,
        val appCursorKeys: Boolean = false,
    )

    private enum class State { TEXT, ESC, ESC_ARG, CSI, OSC, OSC_ESC }

    private class Cursor(val x: Int, val y: Int, val fg: Int, val bg: Int, val bold: Boolean)

    private val lines = ArrayList<Array<AnsiCell>>()
    private var cursorX = 0
    private var cursorY = 0
    private var fg = ANSI_DEFAULT
    private var bg = ANSI_DEFAULT
    private var bold = false
    private var state = State.TEXT
    private val params = StringBuilder()

    // Area di scorrimento (DECSTBM), in righe dello schermo.
    private var scrollTop = 0
    private var scrollBottom = rows - 1

    // Cursore salvato (ESC 7 / CSI s) e schermo principale mentre è attivo il buffer alternativo (CSI ?1049h).
    private var savedCursor: Cursor? = null
    private var mainBuffer: ArrayList<Array<AnsiCell>>? = null
    private var mainCursor: Cursor? = null

    // Modalità tasti cursore dell'applicazione (DECCKM, ESC[?1h): cambia le sequenze delle frecce.
    private var appCursorKeys = false

    init {
        repeat(rows) { lines.add(blankLine()) }
    }

    private val top get() = lines.size - rows

    private fun blankLine() = Array(cols) { AnsiCell() }

    private fun lineAt(y: Int) = lines[top + y]

    fun feed(text: String) {
        for (c in text) process(c)
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        val absCursor = top + cursorY
        val oldTop = top
        cols = newCols.coerceAtLeast(1)
        for (i in lines.indices) {
            val old = lines[i]
            lines[i] = Array(cols) { x -> if (x < old.size) old[x] else AnsiCell() }
        }
        val newRowCount = newRows.coerceAtLeast(1)
        val wasFullScreen = scrollTop == 0 && scrollBottom == rows - 1
        // Growing pulls scrollback back into view; shrinking keeps the cursor line visible.
        cursorY = if (newRowCount >= rows) {
            cursorY + minOf(newRowCount - rows, oldTop)
        } else {
            minOf(cursorY, newRowCount - 1)
        }
        rows = newRowCount
        val newTop = absCursor - cursorY
        while (lines.size > newTop + rows) lines.removeAt(lines.lastIndex)
        while (lines.size < newTop + rows) lines.add(blankLine())
        cursorX = cursorX.coerceAtMost(cols - 1)
        // L'area di scorrimento resta com'era (es. nano la imposta per il testo): con la tastiera a schermo
        // il terminale cambia altezza, e senza area la barra in basso verrebbe scorsa insieme al testo.
        if (wasFullScreen) {
            scrollTop = 0
            scrollBottom = rows - 1
        } else {
            scrollBottom = minOf(scrollBottom, rows - 1)
            scrollTop = minOf(scrollTop, rows - 1)
            if (scrollTop >= scrollBottom) {
                scrollTop = 0
                scrollBottom = rows - 1
            }
        }
    }

    /**
     * Copies the buffer up to the last non-blank screen line or the cursor line, whichever is lower,
     * so it can be read safely after the lock is released. Full-screen apps (nano, less) put the cursor
     * in the middle of the screen and draw a status bar below it: those rows must stay visible.
     */
    fun snapshot(): Snapshot {
        val cursorLine = top + cursorY
        var end = cursorLine
        for (i in lines.lastIndex downTo top) {
            if (lines[i].any { it.ch != ' ' || it.bg != ANSI_DEFAULT }) {
                end = maxOf(end, i)
                break
            }
        }
        return Snapshot(
            lines.subList(0, end + 1).map { it.copyOf() },
            cursorLine,
            cursorX.coerceAtMost(cols - 1),
            altScreen = mainBuffer != null,
            appCursorKeys = appCursorKeys,
        )
    }

    private fun process(c: Char) {
        when (state) {
            State.TEXT -> when (c) {
                '\u001b' -> state = State.ESC
                '\r' -> cursorX = 0
                '\n', '\u000b', '\u000c' -> lineFeed()
                '\b' -> if (cursorX > 0) cursorX--
                '\t' -> cursorX = minOf(cols - 1, (cursorX / 8 + 1) * 8)
                else -> if (c >= ' ' && c != '\u007f') put(c)
            }
            State.ESC -> {
                state = State.TEXT
                when (c) {
                    '[' -> {
                        params.clear()
                        state = State.CSI
                    }
                    ']' -> state = State.OSC
                    '(', ')', '#', '%' -> state = State.ESC_ARG
                    'D' -> lineFeed()                                  // IND
                    'E' -> { cursorX = 0; lineFeed() }                 // NEL
                    'M' -> reverseIndex()                              // RI
                    '7' -> savedCursor = cursorState()                 // DECSC
                    '8' -> savedCursor?.let { restoreCursor(it) }      // DECRC
                    'c' -> resetTerminal()                             // RIS
                }
            }
            State.ESC_ARG -> state = State.TEXT
            State.CSI -> if (c in '@'..'~') {
                csi(c)
                state = State.TEXT
            } else {
                params.append(c)
            }
            State.OSC -> when (c) {
                '\u0007' -> state = State.TEXT
                '\u001b' -> state = State.OSC_ESC
            }
            State.OSC_ESC -> state = if (c == '\\') State.TEXT else State.OSC
        }
    }

    private fun put(c: Char) {
        if (cursorX >= cols) {
            cursorX = 0
            lineFeed()
        }
        lineAt(cursorY)[cursorX] = AnsiCell(c, fg, bg, bold)
        cursorX++
    }

    /** Un a capo: scorre solo se il cursore è sul bordo inferiore dell'area di scorrimento. */
    private fun lineFeed() {
        when {
            cursorY == scrollBottom -> scrollUp(1)
            cursorY < rows - 1 -> cursorY++
        }
    }

    private fun reverseIndex() {
        when {
            cursorY == scrollTop -> scrollDown(1, scrollTop, scrollBottom)
            cursorY > 0 -> cursorY--
        }
    }

    /** Scorrimento su tutto lo schermo: le righe uscite finiscono nello scrollback. Dentro un'area, solo quella area. */
    private fun scrollUp(count: Int) {
        if (scrollTop == 0 && scrollBottom == rows - 1) {
            repeat(minOf(count, rows)) {
                lines.add(blankLine())
                if (lines.size > maxLines + rows) lines.removeAt(0)
            }
        } else {
            scrollRegionUp(count, scrollTop, scrollBottom)
        }
    }

    /** Sposta in su le righe da [from] a [to] (indici di schermo) senza toccare lo scrollback. */
    private fun scrollRegionUp(count: Int, from: Int, to: Int) {
        repeat(minOf(count, to - from + 1)) {
            for (y in from until to) lines[top + y] = lines[top + y + 1]
            lines[top + to] = blankLine()
        }
    }

    private fun scrollDown(count: Int, from: Int, to: Int) {
        repeat(minOf(count, to - from + 1)) {
            for (y in to downTo from + 1) lines[top + y] = lines[top + y - 1]
            lines[top + from] = blankLine()
        }
    }

    private fun cursorState() = Cursor(cursorX, cursorY, fg, bg, bold)

    private fun restoreCursor(c: Cursor) {
        cursorX = c.x.coerceIn(0, cols - 1)
        cursorY = c.y.coerceIn(0, rows - 1)
        fg = c.fg
        bg = c.bg
        bold = c.bold
    }

    private fun resetTerminal() {
        while (lines.size > rows) lines.removeAt(0)
        for (y in 0 until rows) lines[top + y] = blankLine()
        cursorX = 0
        cursorY = 0
        fg = ANSI_DEFAULT
        bg = ANSI_DEFAULT
        bold = false
        appCursorKeys = false
        scrollTop = 0
        scrollBottom = rows - 1
    }

    /** Buffer alternativo (nano, less, vim): schermo vuoto senza scrollback, il principale viene conservato. */
    private fun enterAltScreen() {
        if (mainBuffer != null) return
        mainBuffer = ArrayList(lines)
        mainCursor = cursorState()
        lines.clear()
        repeat(rows) { lines.add(blankLine()) }
        cursorX = 0
        cursorY = 0
        scrollTop = 0
        scrollBottom = rows - 1
    }

    private fun leaveAltScreen() {
        val saved = mainBuffer ?: return
        lines.clear()
        lines.addAll(saved)
        mainBuffer = null
        // La finestra può essere cambiata mentre era attivo il buffer alternativo: adatta le righe salvate.
        for (i in lines.indices) {
            val old = lines[i]
            if (old.size != cols) lines[i] = Array(cols) { x -> if (x < old.size) old[x] else AnsiCell() }
        }
        while (lines.size < rows) lines.add(blankLine())
        mainCursor?.let { restoreCursor(it) }
        mainCursor = null
        scrollTop = 0
        scrollBottom = rows - 1
    }

    private fun csi(final: Char) {
        val priv = params.startsWith("?")
        val raw = if (priv) params.substring(1) else params.toString()
        val args: List<Int?> = if (raw.isEmpty()) emptyList() else raw.split(';').map { it.toIntOrNull() }
        fun arg(i: Int, def: Int) = args.getOrNull(i) ?: def
        fun count(i: Int) = (args.getOrNull(i) ?: 1).coerceAtLeast(1)

        if (priv) {
            if (1 in args && (final == 'h' || final == 'l')) appCursorKeys = final == 'h'
            if (final == 'h' && 1049 in args) enterAltScreen()
            if (final == 'l' && 1049 in args) leaveAltScreen()
            return
        }
        when (final) {
            'm' -> sgr(if (args.isEmpty()) listOf(0) else args.map { it ?: 0 })
            'A' -> cursorY = (cursorY - count(0)).coerceAtLeast(0)
            'B' -> cursorY = (cursorY + count(0)).coerceAtMost(rows - 1)
            'C' -> cursorX = (cursorX + count(0)).coerceAtMost(cols - 1)
            'D' -> cursorX = (cursorX - count(0)).coerceAtLeast(0)
            'E' -> {
                cursorX = 0
                cursorY = (cursorY + count(0)).coerceAtMost(rows - 1)
            }
            'F' -> {
                cursorX = 0
                cursorY = (cursorY - count(0)).coerceAtLeast(0)
            }
            'G' -> cursorX = (count(0) - 1).coerceIn(0, cols - 1)
            'H', 'f' -> {
                cursorY = (count(0) - 1).coerceIn(0, rows - 1)
                cursorX = (count(1) - 1).coerceIn(0, cols - 1)
            }
            'a' -> cursorX = (cursorX + count(0)).coerceAtMost(cols - 1)
            'd' -> cursorY = (count(0) - 1).coerceIn(0, rows - 1)
            'e' -> cursorY = (cursorY + count(0)).coerceAtMost(rows - 1)
            'J' -> eraseDisplay(arg(0, 0))
            'K' -> eraseLine(arg(0, 0))
            'L' -> if (cursorY in scrollTop..scrollBottom) {
                // IL: inserisce righe vuote sopra il cursore, nell'area di scorrimento.
                scrollDown(count(0), cursorY, scrollBottom)
                cursorX = 0
            }
            'M' -> if (cursorY in scrollTop..scrollBottom) {
                // DL: elimina righe a partire dal cursore, nell'area di scorrimento.
                scrollRegionUp(count(0), cursorY, scrollBottom)
                cursorX = 0
            }
            'S' -> scrollUp(count(0))
            'T' -> scrollDown(count(0), scrollTop, scrollBottom)
            'r' -> {
                // DECSTBM: imposta l'area di scorrimento e riporta il cursore in alto a sinistra.
                val t = arg(0, 1) - 1
                val b = arg(1, rows) - 1
                if (t in 0 until b && b < rows) {
                    scrollTop = t
                    scrollBottom = b
                    cursorX = 0
                    cursorY = 0
                }
            }
            '@' -> {
                // ICH: inserisce spazi nella riga, spostando a destra il testo dal cursore.
                val line = lineAt(cursorY)
                cursorX = cursorX.coerceAtMost(cols - 1)
                val n = minOf(count(0), cols - cursorX)
                for (x in cols - 1 downTo cursorX + n) line[x] = line[x - n]
                for (x in cursorX until cursorX + n) line[x] = AnsiCell(bg = bg)
            }
            'P' -> {
                // DCH: elimina caratteri dal cursore, spostando a sinistra il resto della riga.
                val line = lineAt(cursorY)
                cursorX = cursorX.coerceAtMost(cols - 1)
                val n = minOf(count(0), cols - cursorX)
                for (x in cursorX until cols - n) line[x] = line[x + n]
                for (x in cols - n until cols) line[x] = AnsiCell(bg = bg)
            }
            's' -> savedCursor = cursorState()
            'u' -> savedCursor?.let { restoreCursor(it) }
            'X' -> {
                // ECH: cancella N caratteri a partire dal cursore, senza spostarlo.
                val line = lineAt(cursorY)
                for (x in cursorX until minOf(cols, cursorX + count(0))) line[x] = AnsiCell(bg = bg)
            }
        }
    }

    private fun sgr(list: List<Int>) {
        var i = 0
        while (i < list.size) {
            val v = list[i]
            when (v) {
                0 -> {
                    fg = ANSI_DEFAULT
                    bg = ANSI_DEFAULT
                    bold = false
                }
                1 -> bold = true
                22 -> bold = false
                in 30..37 -> fg = v - 30
                39 -> fg = ANSI_DEFAULT
                in 40..47 -> bg = v - 40
                49 -> bg = ANSI_DEFAULT
                in 90..97 -> fg = v - 90 + 8
                in 100..107 -> bg = v - 100 + 8
                38, 48 -> when (list.getOrNull(i + 1)) {
                    5 -> {
                        val color = list.getOrElse(i + 2) { 0 }
                        if (v == 38) fg = color else bg = color
                        i += 2
                    }
                    // 24-bit colours are not rendered; skip their arguments.
                    2 -> i += 4
                }
            }
            i++
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (y in cursorY + 1 until rows) lines[top + y] = blankLine()
            }
            1 -> {
                for (y in 0 until cursorY) lines[top + y] = blankLine()
                eraseLine(1)
            }
            else -> {
                // Drop scrollback too: the view shows everything up to the cursor, so old output would stay visible above it.
                while (lines.size > rows) lines.removeAt(0)
                for (y in 0 until rows) lines[top + y] = blankLine()
            }
        }
    }

    private fun eraseLine(mode: Int) {
        val line = lineAt(cursorY)
        // Dopo aver scritto nell'ultima colonna il cursore può stare a "cols": in xterm resta sull'ultima colonna.
        val x = cursorX.coerceAtMost(cols - 1)
        val (from, to) = when (mode) {
            0 -> x to cols - 1
            1 -> 0 to x
            else -> 0 to cols - 1
        }
        for (x in from..to) line[x] = AnsiCell()
    }
}
