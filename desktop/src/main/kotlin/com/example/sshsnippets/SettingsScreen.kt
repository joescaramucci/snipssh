package com.example.sshsnippets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val MIN_BACKUP_PASSWORD = 8
private const val KEEPALIVE_STEP = 15
private const val BACKUP_FILE_NAME = "snipssh-backup.ssb"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    store: Store,
    appThemeId: String,
    onAppThemeChange: (String) -> Unit,
    onRestored: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var exportFile by remember { mutableStateOf<File?>(null) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fontSize by remember { mutableStateOf(store.terminalFontSize()) }
    var keepAlive by remember { mutableStateOf(store.keepAliveSeconds()) }
    var fontId by remember { mutableStateOf(store.terminalFontId()) }
    var themeId by remember { mutableStateOf(store.terminalThemeId()) }
    var appLang by remember { mutableStateOf(store.appLanguage()) }

    fun chooseExport() {
        scope.launch {
            val file = withContext(Dispatchers.IO) { pickFile(Strings.get("settings_export"), save = true, suggestedName = BACKUP_FILE_NAME) }
            if (file != null) exportFile = file
        }
    }

    fun chooseImport() {
        scope.launch {
            val file = withContext(Dispatchers.IO) { pickFile(Strings.get("settings_import"), save = false) } ?: return@launch
            status = try {
                importBytes = withContext(Dispatchers.IO) { file.readBytes() }
                null
            } catch (e: Exception) {
                Strings.get("backup_read_failed", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    val theme = themeById(themeId)
    val previewStyle = terminalTextStyle(fontById(fontId), fontSize)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguages.forEach { lang ->
                FilterChip(
                    selected = appLang == lang.tag,
                    onClick = {
                        appLang = lang.tag
                        store.setAppLanguage(lang.tag)
                        Strings.language = lang.tag
                    },
                    label = { Text(stringResource(lang.labelRes)) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.settings_app_theme), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemes.forEach { t ->
                AppThemeSwatch(t, selected = appThemeId == t.id) { onAppThemeChange(t.id) }
            }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Text(stringResource(R.string.settings_backup_desc), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = ::chooseExport) { Text(stringResource(R.string.settings_export)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = ::chooseImport) { Text(stringResource(R.string.settings_import)) }
        }

        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it)
        }
    }

    exportFile?.let { file ->
        PasswordDialog(
            title = stringResource(R.string.backup_password),
            confirmLabel = stringResource(R.string.settings_export),
            requireConfirm = true,
            onConfirm = { password ->
                exportFile = null
                scope.launch {
                    status = try {
                        withContext(Dispatchers.IO) { file.writeBytes(encryptBackup(store.exportPayload(), password)) }
                        Strings.get("backup_exported")
                    } catch (e: Exception) {
                        Strings.get("backup_export_failed", e.message ?: e.javaClass.simpleName)
                    }
                }
            },
            onDismiss = { exportFile = null },
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
                        Strings.get("backup_import_done")
                    } catch (e: IllegalArgumentException) {
                        Strings.get("backup_invalid")
                    } catch (e: Exception) {
                        Strings.get("backup_wrong_password")
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
