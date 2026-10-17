package com.example.sshsnippets

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface SessionState {
    data object Connecting : SessionState
    data object Connected : SessionState
    data object Disconnected : SessionState
    class Failed(val message: String) : SessionState
}

/** Una connessione SSH aperta con il suo terminale e lo stato della barra di input. */
class OpenSession(val host: Host, var ssh: SshSession) {
    val id: String = UUID.randomUUID().toString()
    /** Dimensione del terminale, da usare anche alla riconnessione. */
    var cols = 80
    var rows = 24
    val term = AnsiTerminal(80, 24)
    val version = mutableIntStateOf(0)
    val state = mutableStateOf<SessionState>(SessionState.Connecting)
    val input = mutableStateOf(TextFieldValue())
    val ctrl = mutableStateOf(false)
}

/**
 * Sessioni SSH aperte. Vivono a livello di processo e non di schermata: tornare alla lista host
 * non le chiude, e così si può saltare da una connessione all'altra.
 */
object OpenSessions {
    val list = mutableStateListOf<OpenSession>()
    var activeId by mutableStateOf<String?>(null)
    var showTerminal by mutableStateOf(false)
    val hostKeys = HostKeyPrompter()

    private lateinit var store: Store
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val active: OpenSession?
        get() = list.firstOrNull { it.id == activeId }

    /** Va chiamata da MainActivity con il contesto nella lingua scelta dall'utente. */
    fun attach(store: Store, context: Context) {
        this.store = store
        this.context = context
    }

    fun open(host: Host) {
        val session = OpenSession(host, SshSession(context, host, store))
        list.add(session)
        activeId = session.id
        showTerminal = true
        connect(session)
    }

    /** Riapre la connessione di una sessione caduta o fallita, nella stessa scheda. */
    fun reconnect(session: OpenSession) {
        if (session.state.value == SessionState.Connecting) return
        session.ssh = SshSession(context, session.host, store, session.cols, session.rows)
        connect(session)
    }

    private fun connect(session: OpenSession) {
        session.state.value = SessionState.Connecting
        val ssh = session.ssh
        scope.launch {
            try {
                ssh.connect(
                    confirmHostKey = { fingerprint -> hostKeys.confirm(session.host.hostname, fingerprint) },
                    onOutput = { chunk ->
                        synchronized(session.term) { session.term.feed(chunk) }
                        scope.launch { session.version.intValue++ }
                    },
                    onClosed = { clean -> onSessionClosed(session, ssh, clean) },
                )
                session.state.value = SessionState.Connected
            } catch (e: HostKeyRejectedException) {
                // Chiave rifiutata o cambiata: la scheda non serve più, quindi si chiude con un avviso.
                close(session)
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                session.state.value = SessionState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * [clean] è true quando la shell è terminata da sola (exit): la sessione si chiude.
     * Altrimenti la connessione è caduta e la sessione resta aperta, con il pulsante Riconnetti.
     */
    private fun onSessionClosed(session: OpenSession, ssh: SshSession, clean: Boolean) {
        // Una riconnessione ha già sostituito questa connessione: ignorare.
        if (session.ssh !== ssh) return
        scope.launch {
            if (clean) {
                close(session)
            } else {
                session.state.value = SessionState.Disconnected
                withContext(Dispatchers.IO) { ssh.close() }
            }
        }
    }

    fun select(session: OpenSession) {
        activeId = session.id
        showTerminal = true
    }

    /** Chiude una sessione. Se era quella mostrata, passa a un'altra aperta o alla lista host. */
    fun close(session: OpenSession) {
        val index = list.indexOf(session)
        if (index < 0) return
        list.removeAt(index)
        if (activeId == session.id) {
            activeId = list.getOrNull(index.coerceAtMost(list.lastIndex))?.id
        }
        if (list.isEmpty()) showTerminal = false
        scope.launch(Dispatchers.IO) { session.ssh.close() }
    }
}
