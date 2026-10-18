package com.example.sshsnippets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar

// Keep in sync with versionName in app/build.gradle.kts and packageVersion in desktop/build.gradle.kts.
const val APP_VERSION = "0.3.3"

@Composable
fun CreditsBar(modifier: Modifier = Modifier) {
    val year = remember { Calendar.getInstance().get(Calendar.YEAR) }
    Text(
        text = "© $year flipall srl · Joe Scaramucci · info@flipall.it · v.$APP_VERSION",
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
    )
}
