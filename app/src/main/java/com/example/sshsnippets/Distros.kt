package com.example.sshsnippets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistema operativo associabile a un host. Il logo è un badge con il colore del marchio e le iniziali,
 * non il logo ufficiale, che è un marchio registrato.
 */
class Distro(val id: String, val label: String, val color: Color, val initials: String, val labelRes: Int? = null)

val Distros = listOf(
    Distro("debian", "Debian", Color(0xFFA80030), "D"),
    Distro("ubuntu", "Ubuntu", Color(0xFFE95420), "U"),
    Distro("mint", "Linux Mint", Color(0xFF87CF3E), "M"),
    Distro("fedora", "Fedora", Color(0xFF51A2DA), "F"),
    Distro("redhat", "Red Hat", Color(0xFFEE0000), "RH"),
    Distro("arch", "Arch Linux", Color(0xFF1793D1), "A"),
    Distro("alpine", "Alpine", Color(0xFF0D597F), "Al"),
    Distro("windows", "Microsoft Windows", Color(0xFF0078D4), "W"),
    Distro("macos", "macOS", Color(0xFF6E6E73), "mac"),
    Distro("freebsd", "FreeBSD", Color(0xFFAB2B28), "BSD"),
    Distro("linux", "Linux", Color(0xFF5F6368), "L", R.string.distro_generic),
)

fun distroById(id: String): Distro? = Distros.firstOrNull { it.id == id }

@Composable
fun DistroBadge(distro: Distro, size: Int = 36) {
    Box(
        Modifier.size(size.dp).background(distro.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            distro.initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 3).sp,
            maxLines = 1,
        )
    }
}
