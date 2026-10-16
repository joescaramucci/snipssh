package com.example.sshsnippets

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel

/**
 * Blocca l'app quando torna in primo piano dopo essere stata in background.
 * Vive in un ViewModel: una rotazione dello schermo non la riattiva; un riavvio del processo sì.
 */
class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application)
    var locked by mutableStateOf(store.biometricLock())
        private set

    // Un selettore di sistema (file picker) ferma l'activity: quel singolo stop non deve bloccare l'app.
    private var skipNextStop = false

    /** Da chiamare prima di aprire un selettore di file di sistema. */
    fun expectExternalActivity() {
        skipNextStop = true
    }

    fun onStopped() {
        if (skipNextStop) {
            skipNextStop = false
            return
        }
        if (store.biometricLock()) locked = true
    }

    fun unlock() {
        locked = false
    }

    fun setEnabled(enabled: Boolean) {
        store.setBiometricLock(enabled)
        if (!enabled) locked = false
    }
}

private fun biometricAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= 30) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }

/** True se il dispositivo ha una biometria o un blocco schermo configurato. */
fun canAuthenticate(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(biometricAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(biometricAuthenticators())
        // Sotto Android 11 il prompt con credenziali di sistema non ha il pulsante "Annulla" automatico.
        .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText(activity.getString(R.string.common_cancel)) }
        .build()
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(info)
}

@Composable
fun LockScreen(lock: AppLockViewModel) {
    val activity = LocalContext.current as FragmentActivity
    val usable = remember { canAuthenticate(activity) }
    val title = stringResource(R.string.lock_title)
    LaunchedEffect(Unit) {
        if (usable) promptBiometric(activity, title) { lock.unlock() }
    }
    Surface(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} }) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.lock_locked), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            if (usable) {
                Button(onClick = { promptBiometric(activity, title) { lock.unlock() } }) {
                    Text(stringResource(R.string.lock_unlock))
                }
            } else {
                Text(
                    stringResource(R.string.lock_no_biometric),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { lock.setEnabled(false) }) { Text(stringResource(R.string.lock_disable)) }
            }
        }
    }
}
