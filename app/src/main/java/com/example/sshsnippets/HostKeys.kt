package com.example.sshsnippets

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64

/** Confronta la chiave del server con quella salvata: OK se coincide, CHANGED se diversa, NOT_INCLUDED se mai vista. */
class StoredHostKeys(private val known: ByteArray?) : HostKeyRepository {
    /** Ultima chiave ricevuta dal server, letta dalla probe. */
    var lastSeen: ByteArray? = null
        private set

    override fun check(host: String, key: ByteArray): Int {
        lastSeen = key
        val stored = known ?: return HostKeyRepository.NOT_INCLUDED
        return if (stored.contentEquals(key)) HostKeyRepository.OK else HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "sshsnippets"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    /** JSch passa null per host e tipo quando elenca tutte le chiavi. */
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

/** Restituisce il motivo per cui il testo non è una chiave privata utilizzabile, oppure null se è valido. */
fun validatePrivateKey(text: String?): String? = when {
    text.isNullOrBlank() -> "Il file è vuoto o non leggibile"
    text.contains("PuTTY-User-Key-File") -> "Le chiavi .ppk non sono supportate: esportale in formato OpenSSH"
    !text.contains("-----BEGIN") || !text.contains("PRIVATE KEY") ->
        "Formato non riconosciuto: serve una chiave privata PEM/OpenSSH (es. id_ed25519)"
    else -> null
}

/** Impronta nel formato OpenSSH, ad esempio SHA256:abc... */
fun hostKeyFingerprint(key: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(key)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}
