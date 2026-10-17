package com.example.sshsnippets

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() {
    val store = Store()
    Strings.language = store.appLanguage()
    OpenSessions.attach(store, DesktopPlatform)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "SnipSSH",
            icon = painterResource("icon.png"),
            state = rememberWindowState(size = DpSize(1180.dp, 760.dp)),
        ) {
            window.minimumSize = Dimension(820, 520)
            DesktopApp(store)
        }
    }
}
