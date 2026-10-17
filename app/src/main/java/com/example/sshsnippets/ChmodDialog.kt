package com.example.sshsnippets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val MODE_GROUP_LABELS = listOf(R.string.sftp_owner, R.string.sftp_group, R.string.sftp_other)
private val MODE_GROUP_DESCRIPTIONS = listOf(R.string.sftp_owner_desc, R.string.sftp_group_desc, null)
private val MODE_FLAGS = listOf("r", "w", "x")

/** Bit per (gruppo, flag): proprietario = 0o400/0o200/0o100, gruppo = 0o40/0o20/0o10, altri = 0o4/0o2/0o1. */
private fun modeBit(group: Int, flag: Int): Int = 1 shl (8 - (group * 3 + flag))

@Composable
fun ChmodDialog(name: String, initialMode: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var mode by remember { mutableIntStateOf(initialMode) }
    var octal by remember { mutableStateOf("%04o".format(initialMode)) }
    val valid = octal.length == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_chmod_title, name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = octal,
                    onValueChange = { input ->
                        val digits = input.filter { it in '0'..'7' }.take(4)
                        octal = digits
                        if (digits.length == 4) mode = digits.toInt(8)
                    },
                    label = { Text(stringResource(R.string.sftp_mode_octal)) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                MODE_GROUP_LABELS.forEachIndexed { group, labelRes ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(labelRes), Modifier.width(80.dp))
                        MODE_FLAGS.forEachIndexed { flag, letter ->
                            val bit = modeBit(group, flag)
                            Row(Modifier.width(64.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = (mode and bit) != 0,
                                    onCheckedChange = { on ->
                                        mode = if (on) mode or bit else mode and bit.inv()
                                        octal = "%04o".format(mode)
                                    },
                                )
                                Text(letter)
                            }
                        }
                    }
                    MODE_GROUP_DESCRIPTIONS[group]?.let { descRes ->
                        Text(
                            stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(mode) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
