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

/** Una scheda aperta su un host: una shell SSH oppure una sessione SFTP. */
sealed class OpenTab(val host: Host) {
    val id: String = UUID.randomUUID().toString()
    val state = mutableStateOf<SessionState>(SessionState.Connecting)
}

/** Una connessione SSH aperta con il suo terminale e lo stato della barra di input. */
class OpenSession(host: Host, var ssh: SshSession) : OpenTab(host) {
    /** Dimensione del terminale, da usare anche alla riconnessione. */
    var cols = 80
    var rows = 24
    val term = AnsiTerminal(80, 24)
    val version = mutableIntStateOf(0)
    val input = mutableStateOf(TextFieldValue())
    val ctrl = mutableStateOf(false)
}

/** Sessione SFTP. Il client e la cartella restano qui, così cambiare scheda non li perde. */
class OpenSftp(host: Host) : OpenTab(host) {
    var client by mutableStateOf<SftpClient?>(null)
    var homeDir by mutableStateOf("/")
    var path by mutableStateOf("")
    var entries by mutableStateOf<List<RemoteEntry>>(emptyList())
    var busy by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    suspend fun load(c: SftpClient, target: String) {
        entries = c.list(target)
        path = target
    }
}

/**
 * Sessioni SSH aperte. Vivono a livello di processo e non di schermata: tornare alla lista host
 * non le chiude, e così si può saltare da una connessione all'altra.
 */
object OpenSessions {
    val list = mutableStateListOf<OpenTab>()
    var activeId by mutableStateOf<String?>(null)
    var showTerminal by mutableStateOf(false)
    val hostKeys = HostKeyPrompter()

    private lateinit var store: Store
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val active: OpenTab?
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

    /** Apre una scheda SFTP in parallelo alle altre: non sostituisce la shell già aperta sull'host. */
    fun openSftp(host: Host) {
        val tab = OpenSftp(host)
        list.add(tab)
        activeId = tab.id
        showTerminal = true
        connectSftp(tab)
    }

    private fun connectSftp(tab: OpenSftp) {
        tab.state.value = SessionState.Connecting
        tab.busy = true
        tab.error = null
        scope.launch {
            try {
                val c = SftpClient.open(context, tab.host, store) { fingerprint ->
                    hostKeys.confirm(tab.host.hostname, fingerprint)
                }
                tab.client = c
                tab.homeDir = c.home()
                val landing = tab.host.landingDir.trim()
                val target = if (landing.isEmpty()) tab.homeDir else c.resolveDir(expandHome(landing, tab.homeDir))
                if (target != null) {
                    tab.load(c, target)
                } else {
                    tab.load(c, tab.homeDir)
                    tab.error = context.getString(R.string.sftp_landing_missing, landing)
                }
                tab.state.value = SessionState.Connected
            } catch (e: HostKeyRejectedException) {
                close(tab)
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                tab.state.value = SessionState.Failed(message)
                tab.error = message
            } finally {
                tab.busy = false
            }
        }
    }

    /** Esegue un'operazione SFTP sulla scheda; resta attiva anche se si cambia scheda durante l'attesa. */
    fun runSftp(tab: OpenSftp, block: suspend (SftpClient) -> Unit) {
        val c = tab.client ?: return
        scope.launch {
            tab.busy = true
            tab.error = null
            try {
                block(c)
            } catch (e: Exception) {
                tab.error = e.message ?: e.javaClass.simpleName
            } finally {
                tab.busy = false
            }
        }
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

    fun select(tab: OpenTab) {
        activeId = tab.id
        showTerminal = true
    }

    /** Chiude una scheda. Se era quella mostrata, passa a un'altra aperta o alla lista host. */
    fun close(tab: OpenTab) {
        val index = list.indexOf(tab)
        if (index < 0) return
        list.removeAt(index)
        if (activeId == tab.id) {
            activeId = list.getOrNull(index.coerceAtMost(list.lastIndex))?.id
        }
        if (list.isEmpty()) showTerminal = false
        scope.launch(Dispatchers.IO) {
            when (tab) {
                is OpenSession -> tab.ssh.close()
                is OpenSftp -> tab.client?.close()
            }
        }
    }
}
