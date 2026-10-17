package com.example.sshsnippets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun SessionChip(tab: OpenTab, onSelect: () -> Unit) {
    val label = if (tab is OpenSftp) "SFTP · ${tab.host.name}" else tab.host.name
    InputChip(
        selected = tab.id == OpenSessions.activeId,
        onClick = onSelect,
        label = { Text(label, maxLines = 1) },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.session_close),
                modifier = Modifier.size(18.dp).clickable { OpenSessions.close(tab) },
            )
        },
    )
}

/** Schede aperte (shell e SFTP) in una riga scorrevole orizzontalmente. */
@Composable
fun SessionChipRow(modifier: Modifier = Modifier, onSelect: (OpenTab) -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OpenSessions.list.forEach { t -> SessionChip(t) { onSelect(t) } }
    }
}
