package com.acite.tokifactor

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChatForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val app = application as TokiFactorApp
        val engine = app.graph.chatEngine
        ChatNotifications.ensureChannels(this)
        startInForeground(engine.connectionStatus.value)
        engine.start()
        scope.launch {
            engine.connectionStatus.collect { status ->
                ChatNotifications.updateConnection(this@ChatForegroundService, status)
            }
        }
        scope.launch {
            engine.inboundNotices.collect { notice ->
                if (!app.chatUiVisible) {
                    ChatNotifications.notifyMessage(this@ChatForegroundService, notice)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(status: String) {
        val notification = ChatNotifications.connection(this, status)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                ChatNotifications.CONNECTION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(ChatNotifications.CONNECTION_ID, notification)
        }
    }
}
