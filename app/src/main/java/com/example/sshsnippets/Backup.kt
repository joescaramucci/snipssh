package com.example.sshsnippets

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Formato: MAGIC | salt | iv | ciphertext+tag (AES-256-GCM, chiave derivata con PBKDF2-HMAC-SHA256).
private val MAGIC = "SSHSNIP1".toByteArray(Charsets.US_ASCII)
private const val SALT_LEN = 16
private const val IV_LEN = 12
private const val KEY_BITS = 256
private const val TAG_BITS = 128
private const val PBKDF2_ITERATIONS = 310_000

private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
    try {
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    } finally {
        spec.clearPassword()
    }
}

fun encryptBackup(plain: String, password: String): ByteArray {
    val random = SecureRandom()
    val salt = ByteArray(SALT_LEN).also(random::nextBytes)
    val iv = ByteArray(IV_LEN).also(random::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
    cipher.updateAAD(MAGIC)
    val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    return ByteArrayOutputStream().apply {
        write(MAGIC)
        write(salt)
        write(iv)
        write(ciphertext)
    }.toByteArray()
}

/** Lancia [IllegalArgumentException] se il file non è un backup; [javax.crypto.AEADBadTagException] se la password è errata. */
fun decryptBackup(data: ByteArray, password: String): String {
    val headerLen = MAGIC.size + SALT_LEN + IV_LEN
    require(data.size > headerLen && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
        "Not a valid backup file."
    }
    val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
    val iv = data.copyOfRange(MAGIC.size + SALT_LEN, headerLen)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
    cipher.updateAAD(MAGIC)
    return String(cipher.doFinal(data, headerLen, data.size - headerLen), Charsets.UTF_8)
}
