package com.example.sshsnippets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/** Massimo di schede affiancate; le altre restano dietro, nella barra delle schede. */
const val SPLIT_MAX_PANES = 4

/** Schede affiancate quando si attiva la vista. */
const val SPLIT_OPEN_PANES = 2

private val MIN_PANE_WIDTH = 200.dp
private val DIVIDER_WIDTH = 6.dp

/**
 * Schede affiancate, nell'ordine di [tabs]. La larghezza di ogni pannello è il suo peso in [weights];
 * trascinando un divisore si scambiano i pesi dei due pannelli vicini.
 */
@Composable
fun SplitView(
    tabs: List<OpenTab>,
    activeId: String?,
    snippets: List<Snippet>,
    store: Store,
    onSnippetsChange: (List<Snippet>) -> Unit,
    weights: SnapshotStateMap<String, Float>,
    onFocus: (OpenTab) -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val minPx = with(LocalDensity.current) { MIN_PANE_WIDTH.toPx() }

    fun resize(left: String, right: String, deltaPx: Float) {
        if (widthPx <= 0) return
        // Il peso per pixel: i pesi sommati corrispondono alla larghezza disponibile.
        val total = tabs.sumOf { (weights[it.id] ?: 1f).toDouble() }.toFloat()
        val unit = total / widthPx
        val dw = deltaPx * unit
        val minW = minPx * unit
        val newLeft = (weights[left] ?: 1f) + dw
        val newRight = (weights[right] ?: 1f) - dw
        if (newLeft >= minW && newRight >= minW) {
            weights[left] = newLeft
            weights[right] = newRight
        }
    }

    Row(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width }) {
        tabs.forEachIndexed { i, tab ->
            key(tab.id) {
                if (i > 0) {
                    val left = tabs[i - 1].id
                    val right = tab.id
                    Box(
                        Modifier
                            .width(DIVIDER_WIDTH)
                            .fillMaxHeight()
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { resize(left, right, it) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Box(
                    Modifier
                        .weight(weights[tab.id] ?: 1f)
                        .fillMaxHeight()
                        .pointerInput(tab.id) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.any { it.changedToDown() }) onFocus(tab)
                                }
                            }
                        },
                ) {
                    when (tab) {
                        is OpenSession -> TerminalScreen(tab, snippets, store, onSnippetsChange, autoFocus = tab.id == activeId, compact = true)
                        is OpenSftp -> SftpScreen(tab)
                    }
                }
            }
        }
    }
}
