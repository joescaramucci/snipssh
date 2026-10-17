package com.example.sshsnippets

import android.content.Context

/** Messaggi di errore SSH letti dalle risorse Android, nella lingua scelta dall'utente. */
class AndroidSshMessages(private val context: Context) : SshMessages {
    override fun connectFailed(hostname: String, detail: String): String =
        context.getString(R.string.ssh_connect_failed, hostname, detail)

    override fun keyRejected(): String = context.getString(R.string.ssh_key_rejected)

    override fun keyChanged(hostname: String, fingerprint: String): String =
        context.getString(R.string.ssh_key_changed, hostname, fingerprint)
}
