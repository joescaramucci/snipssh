package com.example.sshsnippets

import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stato pubblicato alla UI: byte copiati, dimensione totale (-1 se ignota) e velocità media mobile in byte al secondo. */
data class TransferSnapshot(val done: Long, val total: Long, val bytesPerSecond: Double)

/**
 * Avanzamento di un upload o download SFTP. Lo stato viene pubblicato al massimo ogni [PUBLISH_INTERVAL_S] secondi,
 * così la UI non viene ridisegnata a ogni blocco di dati.
 */
class TransferProgress(private val total: Long) {
    private val state = MutableStateFlow(TransferSnapshot(0L, total, 0.0))
    val snapshot: StateFlow<TransferSnapshot> = state.asStateFlow()

    private var done = 0L
    private var windowBytes = 0L
    private var windowStartNs = System.nanoTime()
    private var speed = 0.0

    internal fun monitor(): SftpProgressMonitor = object : SftpProgressMonitor {
        override fun init(op: Int, src: String?, dest: String?, max: Long) {}

        override fun count(count: Long): Boolean {
            add(count)
            return true
        }

        override fun end() {}
    }

    private fun add(bytes: Long) {
        done += bytes
        windowBytes += bytes
        val now = System.nanoTime()
        val elapsed = (now - windowStartNs) / NS_PER_SECOND
        if (elapsed < PUBLISH_INTERVAL_S) return
        val instant = windowBytes / elapsed
        speed = if (speed == 0.0) instant else SPEED_SMOOTHING * instant + (1 - SPEED_SMOOTHING) * speed
        windowBytes = 0L
        windowStartNs = now
        state.value = TransferSnapshot(done, total, speed)
    }

    private companion object {
        const val PUBLISH_INTERVAL_S = 0.1
        const val SPEED_SMOOTHING = 0.3
        const val NS_PER_SECOND = 1_000_000_000.0
    }
}

/** Velocità in unità leggibili, per esempio "512 KB/s" o "1.4 MB/s". */
fun formatTransferSpeed(bytesPerSecond: Double): String = when {
    bytesPerSecond >= MIB -> "%.1f MB/s".format(bytesPerSecond / MIB)
    bytesPerSecond >= KIB -> "%.0f KB/s".format(bytesPerSecond / KIB)
    else -> "%.0f B/s".format(bytesPerSecond)
}

private const val KIB = 1024.0
private const val MIB = 1024.0 * 1024.0
