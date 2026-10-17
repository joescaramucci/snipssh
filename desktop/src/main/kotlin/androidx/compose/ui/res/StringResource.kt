package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import com.example.sshsnippets.Strings

// Stessa firma di Android, così i file Compose condivisi con l'app compilano senza modifiche.
@Composable
fun stringResource(id: String, vararg args: Any?): String {
    // Leggere la lingua qui fa ricomporre chi usa il testo quando cambia.
    Strings.language
    return Strings.get(id, *args)
}
