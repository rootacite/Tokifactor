package com.acite.tokifactor.daemon

import com.acite.tokifactor.services.ChatEngine
import com.acite.tokifactor.services.SettingsStore
import com.acite.tokifactor.services.OutgoingAttachment
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DaemonCommandResult(val ok: Boolean, val error: String? = null)

fun interface DaemonCommandExecutor {
    suspend fun handle(frame: DaemonClientFrame): DaemonCommandResult
}

class DaemonCommandHandler(
    private val engine: ChatEngine,
    private val settingsStore: SettingsStore,
) : DaemonCommandExecutor {
    override suspend fun handle(frame: DaemonClientFrame): DaemonCommandResult = try {
        when (frame.command) {
            DaemonProtocol.COMMAND_SEND_TEXT -> withContext(Dispatchers.Main) {
                engine.sendMessage(
                    frame.text,
                    frame.attachmentPaths.mapIndexed { index, path ->
                        OutgoingAttachment(
                            File(path).let { PlatformFile(it) },
                            frame.attachmentNames.getOrNull(index)?.ifBlank { null }
                                ?: File(path).name,
                        )
                    },
                )
            }
            DaemonProtocol.COMMAND_SEND_FILE -> withContext(Dispatchers.Main) {
                engine.sendFile(PlatformFile(File(frame.path)))
            }
            DaemonProtocol.COMMAND_SEND_PICTURE -> withContext(Dispatchers.Main) {
                engine.sendPicture(PlatformFile(File(frame.path)))
            }
            DaemonProtocol.COMMAND_CANCEL_TRANSFER -> withContext(Dispatchers.Main) {
                engine.cancelOutgoing(frame.transferId)
            }
            DaemonProtocol.COMMAND_DELETE_MESSAGES -> withContext(Dispatchers.Main) {
                engine.deleteMessages(frame.ids)
            }
            DaemonProtocol.COMMAND_CLEAR_HISTORY -> withContext(Dispatchers.Main) {
                engine.clearHistory()
            }
            DaemonProtocol.COMMAND_RESPOND_APPROVAL -> withContext(Dispatchers.Main) {
                engine.respondToApproval(frame.approvalId, frame.allow)
            }
            DaemonProtocol.COMMAND_RELOAD_SETTINGS -> {
                withContext(Dispatchers.IO) { settingsStore.reload() }
                withContext(Dispatchers.Main) { engine.refreshSettings() }
            }
            DaemonProtocol.COMMAND_ADD_SYSTEM_MESSAGE -> withContext(Dispatchers.Main) {
                engine.addMessage(frame.content, frame.isError)
            }
            DaemonProtocol.COMMAND_PING -> Unit
            else -> return DaemonCommandResult(false, "unknown command: ${frame.command}")
        }
        DaemonCommandResult(true)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        DaemonCommandResult(false, t.message ?: t::class.simpleName ?: "command failed")
    }
}
