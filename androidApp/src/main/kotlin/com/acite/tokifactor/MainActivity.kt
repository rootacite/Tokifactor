package com.acite.tokifactor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.zacsweers.metro.createGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installAppContext(applicationContext)
        val appGraph = createGraph<AppGraph>()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            App(appGraph.metroViewModelFactory)
        }
    }
}
