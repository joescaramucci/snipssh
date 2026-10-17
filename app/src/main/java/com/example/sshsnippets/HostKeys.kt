package com.example.sshsnippets

/** Restituisce l'id della stringa con il motivo per cui il testo non è una chiave privata utilizzabile, oppure null se è valido. */
fun validatePrivateKey(text: String?): Int? = when (privateKeyProblem(text)) {
    PrivateKeyProblem.Empty -> R.string.host_key_empty
    PrivateKeyProblem.Ppk -> R.string.host_key_ppk
    PrivateKeyProblem.Format -> R.string.host_key_format
    null -> null
}
