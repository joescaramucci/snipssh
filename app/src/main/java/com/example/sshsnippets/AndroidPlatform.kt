package com.example.sshsnippets

import android.content.Context
import android.widget.Toast

/** Messaggi di errore SSH letti dalle risorse Android, nella lingua scelta dall'utente. */
class AndroidSshMessages(private val context: Context) : SshMessages {
    override fun connectFailed(hostname: String, detail: String): String =
        context.getString(R.string.ssh_connect_failed, hostname, detail)

    override fun keyRejected(): String = context.getString(R.string.ssh_key_rejected)

    override fun keyChanged(hostname: String, fingerprint: String): String =
        context.getString(R.string.ssh_key_changed, hostname, fingerprint)
}

/** Servizi di piattaforma per [OpenSessions]. [context] deve avere la lingua scelta dall'utente. */
class AndroidPlatform(private val context: Context) : AppPlatform {
    override val messages: SshMessages = AndroidSshMessages(context)

    override fun string(id: ResId, vararg args: Any?): String = context.getString(id, *args)

    override fun toast(text: String?) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
