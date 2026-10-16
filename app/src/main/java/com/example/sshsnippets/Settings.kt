package com.example.sshsnippets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_BACKUP_PASSWORD = 8
private const val BACKUP_FILE_NAME = "snipssh-backup.ssb"

@Composable
fun SettingsScreen(store: Store, lock: AppLockViewModel, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var biometric by remember { mutableStateOf(store.biometricLock()) }
    var status by remember { mutableStateOf<String?>(null) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var askExportPassword by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = exportPassword
        exportPassword = null
        if (uri != null && password != null) {
            scope.launch {
                status = try {
                    val data = withContext(Dispatchers.IO) {
                        encryptBackup(store.exportPayload(), password)
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                            ?: throw IllegalStateException("Impossibile scrivere il file")
                    }
                    "Backup esportato."
                } catch (e: Exception) {
                    "Esportazione fallita: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                status = try {
                    importBytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("File non leggibile")
                    }
                    null
                } catch (e: Exception) {
                    "Lettura fallita: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        Text("Impostazioni", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Blocco biometrico")
                Text(
                    "Richiede impronta o blocco schermo quando torni nell'app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = biometric,
                onCheckedChange = { wanted ->
                    if (wanted && !canAuthenticate(context)) {
                        status = "Configura impronta o blocco schermo nelle impostazioni di Android."
                    } else {
                        biometric = wanted
                        lock.setEnabled(wanted)
                    }
                },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Il file è cifrato con una password che scegli tu. Contiene host (incluse password, chiavi private " +
                "e passphrase), snippet e chiavi host. Conservalo in un posto sicuro.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { askExportPassword = true }) { Text("Esporta") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                lock.expectExternalActivity()
                importLauncher.launch(arrayOf("*/*"))
            }) { Text("Importa") }
        }

        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it)
        }
    }

    if (askExportPassword) {
        PasswordDialog(
            title = "Password del backup",
            confirmLabel = "Esporta",
            requireConfirm = true,
            onConfirm = { password ->
                askExportPassword = false
                exportPassword = password
                lock.expectExternalActivity()
                exportLauncher.launch(BACKUP_FILE_NAME)
            },
            onDismiss = { askExportPassword = false },
        )
    }

    importBytes?.let { data ->
        PasswordDialog(
            title = "Password del backup",
            confirmLabel = "Importa",
            requireConfirm = false,
            onConfirm = { password ->
                importBytes = null
                scope.launch {
                    status = try {
                        val json = withContext(Dispatchers.IO) { decryptBackup(data, password) }
                        store.importPayload(json)
                        onRestored()
                        "Backup importato. I dati precedenti sono stati sostituiti."
                    } catch (e: IllegalArgumentException) {
                        e.message ?: "File non valido"
                    } catch (e: Exception) {
                        "Password errata o file non valido."
                    }
                }
            },
            onDismiss = { importBytes = null },
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    requireConfirm: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val valid = password.length >= MIN_BACKUP_PASSWORD && (!requireConfirm || password == repeat)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text("Ripeti password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Minimo $MIN_BACKUP_PASSWORD caratteri. Se la perdi il backup non può essere recuperato.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(password) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
