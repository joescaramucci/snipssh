package com.example.sshsnippets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Sistema operativo associabile a un host. Il logo è un vector drawable bianco (res/drawable/ic_distro_*.xml)
 * su un cerchio con il colore del marchio. I loghi provengono da Simple Icons (CC0); i marchi restano
 * dei rispettivi titolari, vedi README. Windows non ha un logo libero: usa un'icona di schermo.
 */
class Distro(val id: String, val label: String, val color: Color, val iconRes: Int, val labelRes: Int? = null)

val Distros = listOf(
    Distro("debian", "Debian", Color(0xFFA80030), R.drawable.ic_distro_debian),
    Distro("ubuntu", "Ubuntu", Color(0xFFE95420), R.drawable.ic_distro_ubuntu),
    Distro("mint", "Linux Mint", Color(0xFF87CF3E), R.drawable.ic_distro_mint),
    Distro("fedora", "Fedora", Color(0xFF51A2DA), R.drawable.ic_distro_fedora),
    Distro("redhat", "Red Hat", Color(0xFFEE0000), R.drawable.ic_distro_redhat),
    Distro("arch", "Arch Linux", Color(0xFF1793D1), R.drawable.ic_distro_arch),
    Distro("alpine", "Alpine", Color(0xFF0D597F), R.drawable.ic_distro_alpine),
    Distro("windows", "Microsoft Windows", Color(0xFF0078D4), R.drawable.ic_distro_windows),
    Distro("macos", "macOS", Color(0xFF6E6E73), R.drawable.ic_distro_macos),
    Distro("freebsd", "FreeBSD", Color(0xFFAB2B28), R.drawable.ic_distro_freebsd),
    Distro("linux", "Linux", Color(0xFF5F6368), R.drawable.ic_distro_linux, R.string.distro_generic),
)

fun distroById(id: String): Distro? = Distros.firstOrNull { it.id == id }

@Composable
fun DistroBadge(distro: Distro, size: Int = 36) {
    Box(
        Modifier.size(size.dp).background(distro.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(distro.iconRes),
            contentDescription = distro.label,
            modifier = Modifier.size((size * 0.66f).dp),
        )
    }
}
