package com.example.sshsnippets

import android.content.Context
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream

/** Chiave host rifiutata dall'utente o cambiata: la connessione è bloccata e la sessione va chiusa. */
class HostKeyRejectedException(message: String) : Exception(message)

const val DEFAULT_KEEPALIVE_SECONDS = 30
const val MAX_KEEPALIVE_SECONDS = 120

/** Dopo questo numero di keepalive senza risposta la connessione viene considerata caduta. */
private const val KEEPALIVE_COUNT_MAX = 3

/**
 * L'implementazione Ed25519 predefinita di JSch usa l'EdDSA del JDK (Java 15+), assente su Android sotto API 33.
 * Il provider BouncyCastle incluso nell'app funziona su tutte le versioni. La configurazione JSch è globale.
 */
private fun useBouncyCastleEd25519() {
    JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
    JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
}

/**
 * Apre una sessione SSH verificando la chiave host (trust-on-first-use) prima di inviare credenziali.
 * [confirmHostKey] riceve l'impronta di una chiave mai vista e restituisce true se l'utente la accetta.
 */
suspend fun openVerifiedSession(
    context: Context,
    host: Host,
    store: Store,
    confirmHostKey: suspend (fingerprint: String) -> Boolean,
): Session = withContext(Dispatchers.IO) {
    useBouncyCastleEd25519()
    val hostId = "${host.hostname}:${host.port}"
    val known = store.loadHostKey(hostId)

    // Probe senza credenziali: auth "none" viene sempre rifiutata, ma la chiave del server è già stata ricevuta.
    val probeRepo = StoredHostKeys(known)
    val probe = JSch().apply { setHostKeyRepository(probeRepo) }.getSession(host.username, host.hostname, host.port)
    probe.setConfig("StrictHostKeyChecking", "no")
    probe.setConfig("PreferredAuthentications", "none")
    val probeError = try {
        probe.connect(15_000)
        null
    } catch (e: JSchException) {
        e
    } finally {
        probe.disconnect()
    }
    val seen = probeRepo.lastSeen
        ?: throw IOException(
            context.getString(R.string.ssh_connect_failed, host.hostname, probeError?.message.orEmpty()),
        )

    when {
        known == null -> {
            if (!confirmHostKey(hostKeyFingerprint(seen))) {
                throw HostKeyRejectedException(context.getString(R.string.ssh_key_rejected))
            }
            store.saveHostKey(hostId, seen)
        }
        !known.contentEquals(seen) -> throw HostKeyRejectedException(
            context.getString(R.string.ssh_key_changed, host.hostname, hostKeyFingerprint(seen)),
        )
    }

    val jsch = JSch().apply { setHostKeyRepository(StoredHostKeys(seen)) }
    if (host.privateKey.isNotBlank()) {
        val passphrase = host.keyPassphrase.takeIf { it.isNotEmpty() }?.toByteArray()
        jsch.addIdentity("key", host.privateKey.toByteArray(), null, passphrase)
    }
    val s = jsch.getSession(host.username, host.hostname, host.port)
    if (host.password.isNotBlank()) s.setPassword(host.password)
    s.setConfig("StrictHostKeyChecking", "yes")
    val keepAlive = store.keepAliveSeconds()
    if (keepAlive > 0) {
        s.setServerAliveInterval(keepAlive * 1000)
        s.setServerAliveCountMax(KEEPALIVE_COUNT_MAX)
    }
    s.connect(15_000)
    s
}

class SshSession(
    private val context: Context,
    private val host: Host,
    private val store: Store,
    initialCols: Int = 80,
    initialRows: Int = 24,
) {
    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var out: OutputStream? = null
    @Volatile private var cols = initialCols
    @Volatile private var rows = initialRows

    /**
     * Verifica la chiave host prima di inviare credenziali. [confirmHostKey] riceve l'impronta
     * di una chiave mai vista e restituisce true se l'utente la accetta.
     * [onClosed] riceve true se la shell è terminata normalmente, false se la connessione è caduta.
     */
    suspend fun connect(
        confirmHostKey: suspend (fingerprint: String) -> Boolean,
        onOutput: (String) -> Unit,
        onClosed: (clean: Boolean) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val s = openVerifiedSession(context, host, store, confirmHostKey)
        val ch = s.openChannel("shell") as ChannelShell
        ch.setPtyType("xterm-256color", cols, rows, 0, 0)
        val input = InputStreamReader(ch.inputStream, Charsets.UTF_8)
        out = ch.outputStream
        ch.connect(10_000)
        session = s
        channel = ch
        CoroutineScope(Dispatchers.IO).launch {
            val buf = CharArray(4096)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    onOutput(String(buf, 0, n))
                }
            } catch (e: IOException) {
                // Il server ha chiuso il canale: lo gestisce onClosed.
            } finally {
                // Il server invia exit-status prima di chiudere una shell terminata con exit. In una caduta di rete non arriva.
                onClosed(ch.exitStatus >= 0)
            }
        }
    }


    suspend fun send(text: String) = withContext(Dispatchers.IO) {
        out?.apply { write(text.toByteArray(Charsets.UTF_8)); flush() }
    }

    suspend fun resize(newCols: Int, newRows: Int) = withContext(Dispatchers.IO) {
        cols = newCols
        rows = newRows
        channel?.setPtySize(newCols, newRows, 0, 0)
    }

    fun close() {
        channel?.disconnect()
        session?.disconnect()
    }
}
