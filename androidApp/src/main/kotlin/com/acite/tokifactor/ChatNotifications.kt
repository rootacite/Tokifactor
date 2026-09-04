package com.acite.tokifactor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.acite.tokifactor.model.InboundNotice

object ChatNotifications {
    const val CONNECTION_ID = 1
    const val CHANNEL_CONNECTION = "connection"
    const val CHANNEL_MESSAGES = "messages"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps tokifactor connected in the background"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming chat messages"
            }
        )
    }

    fun connection(context: Context, status: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("tokifactor")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun notifyMessage(context: Context, notice: InboundNotice) {
        val id = messageId(notice.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notice.sender)
            .setContentText(notice.preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.preview))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, notice.id.hashCode()))
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun updateConnection(context: Context, status: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(CONNECTION_ID, connection(context, status))
    }

    private fun openApp(context: Context, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun messageId(messageId: String): Int {
        val hashed = messageId.hashCode()
        return if (hashed == CONNECTION_ID) CONNECTION_ID + 1 else hashed
    }
}
