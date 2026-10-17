package com.example.sshsnippets

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MIN_BACKUP_PASSWORD = 8
private const val KEEPALIVE_STEP = 15
private const val BACKUP_FILE_NAME = "snipssh-backup.ssb"

@Composable
fun SettingsScreen(
    store: Store,
    lock: AppLockViewModel,
    appThemeId: String,
    onAppThemeChange: (String) -> Unit,
    onRestored: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var biometric by remember { mutableStateOf(store.biometricLock()) }
    var status by remember { mutableStateOf<String?>(null) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var askExportPassword by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(store.terminalFontSize()) }
    var keepAlive by remember { mutableStateOf(store.keepAliveSeconds()) }
    var fontId by remember { mutableStateOf(store.terminalFontId()) }
    var themeId by remember { mutableStateOf(store.terminalThemeId()) }
    var appLang by remember { mutableStateOf(store.appLanguage()) }

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
                            ?: throw IllegalStateException(context.getString(R.string.file_write_failed))
                    }
                    context.getString(R.string.backup_exported)
                } catch (e: Exception) {
                    context.getString(R.string.backup_export_failed, e.message ?: e.javaClass.simpleName)
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
                            ?: throw IllegalStateException(context.getString(R.string.file_unreadable))
                    }
                    null
                } catch (e: Exception) {
                    context.getString(R.string.backup_read_failed, e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    val theme = themeById(themeId)
    val previewStyle = terminalTextStyle(fontById(fontId), fontSize)

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguages.forEach { lang ->
                FilterChip(
                    selected = appLang == lang.tag,
                    onClick = {
                        if (appLang != lang.tag) {
                            appLang = lang.tag
                            store.setAppLanguage(lang.tag)
                            (context as? Activity)?.recreate()
                        }
                    },
                    label = { Text(stringResource(lang.labelRes)) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_app_theme), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppThemes.forEach { t ->
                AppThemeSwatch(t, selected = appThemeId == t.id) { onAppThemeChange(t.id) }
            }
        }
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_biometric))
                Text(
                    stringResource(R.string.settings_biometric_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = biometric,
                onCheckedChange = { wanted ->
                    if (wanted && !canAuthenticate(context)) {
                        status = context.getString(R.string.settings_biometric_setup)
                    } else {
                        biometric = wanted
                        lock.setEnabled(wanted)
                    }
                },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_connection), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (keepAlive == 0) stringResource(R.string.settings_keepalive_off)
            else stringResource(R.string.settings_keepalive, keepAlive),
        )
        Slider(
            value = keepAlive.toFloat(),
            onValueChange = { keepAlive = (it / KEEPALIVE_STEP).roundToInt() * KEEPALIVE_STEP },
            onValueChangeFinished = { store.setKeepAliveSeconds(keepAlive) },
            valueRange = 0f..MAX_KEEPALIVE_SECONDS.toFloat(),
            steps = MAX_KEEPALIVE_SECONDS / KEEPALIVE_STEP - 1,
        )

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_terminal), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_font_size, fontSize))
        Slider(
            value = fontSize.toFloat(),
            onValueChange = { fontSize = it.roundToInt() },
            onValueChangeFinished = { store.setTerminalFontSize(fontSize) },
            valueRange = MIN_TERMINAL_FONT_SIZE.toFloat()..MAX_TERMINAL_FONT_SIZE.toFloat(),
            steps = MAX_TERMINAL_FONT_SIZE - MIN_TERMINAL_FONT_SIZE - 1,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_font))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalFonts.forEach { f ->
                FilterChip(
                    selected = fontId == f.id,
                    onClick = { fontId = f.id; store.setTerminalFontId(f.id) },
                    label = { Text(labelText(f.labelRes, f.label)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_theme))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalThemes.forEach { t ->
                FilterChip(
                    selected = themeId == t.id,
                    onClick = { themeId = t.id; store.setTerminalThemeId(t.id) },
                    label = { Text(stringResource(t.labelRes)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().background(theme.bg).padding(8.dp)) {
            Text("user@server:~$ ls -la", color = theme.fg, style = previewStyle)
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_backup_desc),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { askExportPassword = true }) { Text(stringResource(R.string.settings_export)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                lock.expectExternalActivity()
                importLauncher.launch(arrayOf("*/*"))
            }) { Text(stringResource(R.string.settings_import)) }
        }

        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it)
        }
    }

    if (askExportPassword) {
        PasswordDialog(
            title = stringResource(R.string.backup_password),
            confirmLabel = stringResource(R.string.settings_export),
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
            title = stringResource(R.string.backup_password),
            confirmLabel = stringResource(R.string.settings_import),
            requireConfirm = false,
            onConfirm = { password ->
                importBytes = null
                scope.launch {
                    status = try {
                        val json = withContext(Dispatchers.IO) { decryptBackup(data, password) }
                        store.importPayload(json)
                        onRestored()
                        context.getString(R.string.backup_import_done)
                    } catch (e: IllegalArgumentException) {
                        context.getString(R.string.backup_invalid)
                    } catch (e: Exception) {
                        context.getString(R.string.backup_wrong_password)
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
                    label = { Text(stringResource(R.string.common_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.backup_repeat_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.backup_password_min, MIN_BACKUP_PASSWORD),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(password) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun AppThemeSwatch(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(shape)
                .background(theme.brush)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape,
                )
                .clickable(role = Role.RadioButton, onClick = onClick),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(theme.labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
