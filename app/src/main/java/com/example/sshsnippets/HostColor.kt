package com.example.sshsnippets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

val HostColorPresets = listOf(
    "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00ACC1", "#1E88E5", "#8E24AA", "#6D4C41",
)

private val HexColor = Regex("#?[0-9A-Fa-f]{6}")

fun parseHostColor(hex: String): Color? {
    val t = hex.trim()
    if (!HexColor.matches(t)) return null
    return Color(t.removePrefix("#").toLong(16) or 0xFF000000L)
}

fun normalizeHostColor(hex: String): String =
    if (hex.isBlank()) "" else "#" + hex.trim().removePrefix("#").uppercase()

// Tints a row with the host color, blended over the given opaque base color.
fun hostRowColor(hex: String, base: Color): Color =
    parseHostColor(hex)?.copy(alpha = 0.28f)?.compositeOver(base) ?: base

@Composable
fun HostColorDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var hex by remember { mutableStateOf(current) }
    val valid = hex.isBlank() || parseHostColor(hex) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.host_color_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    HostColorPresets.forEach { preset ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(parseHostColor(preset)!!)
                                .clickable { hex = preset },
                        )
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it },
                    label = { Text(stringResource(R.string.host_color_hex)) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = if (!valid) {
                        { Text(stringResource(R.string.host_color_invalid)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onSave("") }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.host_color_default))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(normalizeHostColor(hex)) }, enabled = valid) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
