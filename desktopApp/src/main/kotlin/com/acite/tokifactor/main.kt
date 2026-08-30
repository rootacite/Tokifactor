package com.acite.tokifactor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraph

fun main() {
    val appGraph = createGraph<AppGraph>()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "tokifactor",
            state = rememberWindowState(size = DpSize(450.dp, 800.dp))
        ) {
            App(appGraph.metroViewModelFactory)
        }
    }
}