package com.example.sshsnippets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colori di `ls` nel terminale: stessa palette ANSI di TerminalScreen. */
val SftpDirColor = Color(0xFF5C5CFF)
val SftpFileColor = Color(0xFF00CD00)
private val SftpExecuteColor = Color(0xFFFF0000)

private const val LED_COUNT = 40

/** Stringa dei permessi (per esempio drwxr-xr-x) con le x in rosso, seguita da [rest]. */
fun styledPerms(perms: String, rest: String): AnnotatedString = buildAnnotatedString {
    perms.forEach { ch ->
        if (ch == 'x') {
            withStyle(SpanStyle(color = SftpExecuteColor)) { append(ch) }
        } else {
            append(ch)
        }
    }
    append(rest)
}

/**
 * Barra a LED dell'avanzamento di un trasferimento, con velocità e KB trasferiti in piccolo sulla stessa riga.
 * Il primo LED si accende subito all'avvio. Se la dimensione non è nota la barra scorre senza riempirsi.
 * Sotto la barra ci sono i pulsanti per arrestare, continuare o eliminare il trasferimento.
 */
@Composable
fun TransferLedBar(progress: TransferProgress, modifier: Modifier = Modifier) {
    val snap by progress.snapshot.collectAsState()
    val lit = if (snap.total > 0) {
        ((snap.done.toDouble() / snap.total) * LED_COUNT).toInt().coerceIn(1, LED_COUNT)
    } else {
        1 + ((snap.done shr 16) % LED_COUNT).toInt()
    }
    val doneKb = snap.done / 1024
    val amount = if (snap.total > 0) "%,d / %,d KB".format(doneKb, snap.total / 1024) else "%,d KB".format(doneKb)
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(LED_COUNT) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(if (i < lit) SftpFileColor else off, RoundedCornerShape(1.dp)),
                    )
                }
            }
            Text(
                "${formatTransferSpeed(snap.bytesPerSecond)}  $amount",
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp),
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            val buttonText = TextStyle(fontSize = 12.sp)
            TextButton(onClick = { progress.pause() }, enabled = !snap.paused && !snap.cancelled) {
                Text(stringResource(R.string.sftp_transfer_pause), style = buttonText)
            }
            TextButton(onClick = { progress.resume() }, enabled = snap.paused && !snap.cancelled) {
                Text(stringResource(R.string.sftp_transfer_resume), style = buttonText)
            }
            TextButton(onClick = { progress.cancel() }, enabled = !snap.cancelled) {
                Text(stringResource(R.string.sftp_transfer_delete), style = buttonText, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
