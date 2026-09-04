package com.acite.tokifactor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        startChatService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appGraph = (application as TokiFactorApp).graph
        setContent {
            App(appGraph.metroViewModelFactory)
        }
        ensureBackgroundChat()
    }

    override fun onStart() {
        super.onStart()
        (application as TokiFactorApp).chatUiVisible = true
    }

    override fun onStop() {
        (application as TokiFactorApp).chatUiVisible = false
        super.onStop()
    }

    private fun ensureBackgroundChat() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startChatService()
        }
    }

    private fun startChatService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, ChatForegroundService::class.java),
        )
    }
}
