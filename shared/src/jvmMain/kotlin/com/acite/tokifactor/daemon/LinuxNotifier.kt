package com.acite.tokifactor.daemon

import com.acite.tokifactor.model.InboundNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LinuxNotifier(
    private val scope: CoroutineScope,
    private val avatarProvider: suspend (String) -> ByteArray? = { null },
) {
    fun notify(notice: InboundNotice) {
        scope.launch(Dispatchers.IO) {
            try {
                val avatar = resolveAvatar(notice.senderId)
                val icon = avatar
                    ?.let { NotificationAvatarStore.iconPath(notice.senderId, it) }
                    ?: "tokifactor"
                val process = ProcessBuilder(
                    "notify-send",
                    "--app-name=TokiFactor",
                    "--icon=$icon",
                    "--urgency=normal",
                    notice.sender,
                    notice.preview,
                ).start()
                process.waitFor()
            } catch (e: Exception) {
                System.err.println(
                    "tokifactord: notify-send unavailable or failed: ${e.message ?: e::class.simpleName}"
                )
            }
        }
    }

    private suspend fun resolveAvatar(senderId: String): ByteArray? {
        if (senderId.isBlank()) return null
        avatarProvider(senderId)?.let { return it }
        repeat(5) {
            delay(120L)
            avatarProvider(senderId)?.let { return it }
        }
        return null
    }
}
