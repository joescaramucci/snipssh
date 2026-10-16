package com.example.sshsnippets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PendingHostKey(val hostname: String, val fingerprint: String, val answer: CompletableDeferred<Boolean>)

/** Chiede all'utente di accettare una chiave host sconosciuta. Va creato con [rememberHostKeyPrompter]. */
class HostKeyPrompter {
    var pending by mutableStateOf<PendingHostKey?>(null)
        private set

    /** Chiamata da una coroutine in background: sospende finché l'utente non risponde. */
    suspend fun confirm(hostname: String, fingerprint: String): Boolean {
        val answer = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) { pending = PendingHostKey(hostname, fingerprint, answer) }
        return answer.await()
    }

    fun answer(accepted: Boolean) {
        pending?.answer?.complete(accepted)
        pending = null
    }
}

@Composable
fun rememberHostKeyPrompter(): HostKeyPrompter = remember { HostKeyPrompter() }

@Composable
fun HostKeyDialog(prompter: HostKeyPrompter) {
    val p = prompter.pending ?: return
    AlertDialog(
        onDismissRequest = { prompter.answer(false) },
        title = { Text(stringResource(R.string.host_new)) },
        text = { Text(stringResource(R.string.hostkey_message, p.hostname, p.fingerprint)) },
        confirmButton = { TextButton(onClick = { prompter.answer(true) }) { Text(stringResource(R.string.hostkey_accept)) } },
        dismissButton = { TextButton(onClick = { prompter.answer(false) }) { Text(stringResource(R.string.hostkey_reject)) } },
    )
}
