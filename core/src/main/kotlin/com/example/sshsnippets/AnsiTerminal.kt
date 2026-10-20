package com.example.sshsnippets

const val ANSI_DEFAULT = -1

data class AnsiCell(
    val ch: Char = ' ',
    val fg: Int = ANSI_DEFAULT,
    val bg: Int = ANSI_DEFAULT,
    val bold: Boolean = false,
)

/**
 * Minimal VT100/xterm screen with scrollback: SGR colours, cursor movement and erase.
 * Not thread-safe: callers must synchronize on the instance.
 */
class AnsiTerminal(private var cols: Int, private var rows: Int, private val maxLines: Int = 2000) {

    class Snapshot(val lines: List<Array<AnsiCell>>, val cursorLine: Int, val cursorCol: Int)

    private enum class State { TEXT, ESC, ESC_ARG, CSI, OSC, OSC_ESC }

    private val lines = ArrayList<Array<AnsiCell>>()
    private var cursorX = 0
    private var cursorY = 0
    private var fg = ANSI_DEFAULT
    private var bg = ANSI_DEFAULT
    private var bold = false
    private var state = State.TEXT
    private val params = StringBuilder()

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
    }

    /** Copies the buffer up to the cursor line, so it can be read safely after the lock is released. */
    fun snapshot(): Snapshot {
        val end = top + cursorY
        return Snapshot(
            lines.subList(0, end + 1).map { it.copyOf() },
            end,
            cursorX.coerceAtMost(cols - 1),
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
            State.ESC -> state = when (c) {
                '[' -> {
                    params.clear()
                    State.CSI
                }
                ']' -> State.OSC
                '(', ')', '#', '%' -> State.ESC_ARG
                else -> State.TEXT
            }
            State.ESC_ARG -> state = State.TEXT
            State.CSI -> if (c in '\u0040'..'\u007e') {
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

    private fun lineFeed() {
        if (cursorY < rows - 1) {
            cursorY++
        } else {
            lines.add(blankLine())
            if (lines.size > maxLines + rows) lines.removeAt(0)
        }
    }

    private fun csi(final: Char) {
        val priv = params.startsWith("?")
        val raw = if (priv) params.substring(1) else params.toString()
        val args: List<Int?> = if (raw.isEmpty()) emptyList() else raw.split(';').map { it.toIntOrNull() }
        fun arg(i: Int, def: Int) = args.getOrNull(i) ?: def
        fun count(i: Int) = (args.getOrNull(i) ?: 1).coerceAtLeast(1)

        if (priv) return
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
        val (from, to) = when (mode) {
            0 -> cursorX to cols - 1
            1 -> 0 to cursorX
            else -> 0 to cols - 1
        }
        for (x in from..to) line[x] = AnsiCell()
    }
}
