package com.acite.tokifactor.pages

import androidx.lifecycle.ViewModel
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.services.ChatEngine
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.vinceglb.filekit.core.PlatformFile

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class MainPageViewModel(
    private val chatEngine: ChatEngine,
) : ViewModel() {

    val messages: List<ChatMessage>
        get() = chatEngine.messages

    val avatarBytes get() = chatEngine.avatarBytes

    val connected get() = chatEngine.connected

    val peerAvatars get() = chatEngine.peerAvatars

    init {
        chatEngine.start()
    }

    fun refreshSettings() = chatEngine.refreshSettings()

    fun addMessage(content: String, isError: Boolean = false) =
        chatEngine.addMessage(content, isError)

    fun removeMessage(id: String) = chatEngine.removeMessage(id)

    fun deleteMessages(ids: Collection<String>) = chatEngine.deleteMessages(ids)

    fun saveMessageFile(message: ChatMessage, dest: PlatformFile) =
        chatEngine.saveMessageFile(message, dest)

    fun cancelOutgoing(transferId: String) = chatEngine.cancelOutgoing(transferId)

    fun sendMessage(content: String) = chatEngine.sendMessage(content)

    fun sendPicture(file: PlatformFile) = chatEngine.sendPicture(file)

    fun sendFile(file: PlatformFile) = chatEngine.sendFile(file)
}
