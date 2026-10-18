package com.example.sshsnippets

import com.jcraft.jsch.SftpProgressMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Stato pubblicato alla UI: byte copiati, dimensione totale (-1 se ignota), velocità media mobile in byte al secondo,
 * e se il trasferimento è in pausa o annullato.
 */
data class TransferSnapshot(
    val done: Long,
    val total: Long,
    val bytesPerSecond: Double,
    val paused: Boolean = false,
    val cancelled: Boolean = false,
)

/**
 * Avanzamento di un upload o download SFTP, con pausa, ripresa e annullamento. Lo stato viene pubblicato al massimo
 * ogni [PUBLISH_INTERVAL_S] secondi, così la UI non viene ridisegnata a ogni blocco di dati.
 *
 * La pausa blocca il thread di trasferimento nel prossimo blocco di dati. L'annullamento fa restituire `false` al
 * monitor, e JSch chiude il trasferimento senza eccezioni: il chiamante controlla [isCancelled] per rimuovere il file parziale.
 */
class TransferProgress(private val total: Long) {
    private val state = MutableStateFlow(TransferSnapshot(0L, total, 0.0))
    val snapshot: StateFlow<TransferSnapshot> = state.asStateFlow()

    private val lock = ReentrantLock()
    private val resumed = lock.newCondition()
    private var done = 0L
    private var windowBytes = 0L
    private var windowStartNs = System.nanoTime()
    private var speed = 0.0
    private var paused = false
    private var cancelled = false

    val isCancelled: Boolean
        get() = lock.withLock { cancelled }

    fun pause() {
        lock.withLock {
            if (paused || cancelled) return
            paused = true
            speed = 0.0
            publish()
        }
    }

    fun resume() {
        lock.withLock {
            if (!paused || cancelled) return
            paused = false
            windowBytes = 0L
            windowStartNs = System.nanoTime()
            publish()
            resumed.signalAll()
        }
    }

    fun cancel() {
        lock.withLock {
            if (cancelled) return
            cancelled = true
            paused = false
            speed = 0.0
            publish()
            resumed.signalAll()
        }
    }

    internal fun monitor(): SftpProgressMonitor = object : SftpProgressMonitor {
        override fun init(op: Int, src: String?, dest: String?, max: Long) {}

        override fun count(count: Long): Boolean {
            lock.withLock {
                while (paused && !cancelled) resumed.await()
                if (cancelled) return false
                add(count)
            }
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
        publish()
    }

    private fun publish() {
        state.value = TransferSnapshot(done, total, speed, paused, cancelled)
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
