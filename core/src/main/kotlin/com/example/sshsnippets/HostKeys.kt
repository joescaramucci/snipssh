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

enum class PrivateKeyProblem { Empty, Ppk, Format }

/** Il motivo per cui il testo non è una chiave privata utilizzabile, oppure null se è valido. */
fun privateKeyProblem(text: String?): PrivateKeyProblem? = when {
    text.isNullOrBlank() -> PrivateKeyProblem.Empty
    text.contains("PuTTY-User-Key-File") -> PrivateKeyProblem.Ppk
    !text.contains("-----BEGIN") || !text.contains("PRIVATE KEY") -> PrivateKeyProblem.Format
    else -> null
}

/** Impronta nel formato OpenSSH, ad esempio SHA256:abc... */
fun hostKeyFingerprint(key: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(key)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}
