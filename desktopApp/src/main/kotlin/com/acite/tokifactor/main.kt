package com.acite.tokifactor

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.acite.tokifactor.daemon.TokiFactorDaemon
import dev.zacsweers.metro.createGraph

fun main(args: Array<String>) {
    if (args.contains("--daemon")) {
        TokiFactorDaemon.run()
        return
    }
    val appGraph = createGraph<AppGraph>()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "tokifactor",
            icon = painterResource("icon.png"),
            state = rememberWindowState(size = DpSize(450.dp, 800.dp))
        ) {
            App(appGraph.metroViewModelFactory, appGraph.settingsStore)
        }
    }
}
