package com.acite.tokifactor.pages

import androidx.lifecycle.ViewModel
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.ConversationSummary
import com.acite.tokifactor.model.SoloState
import com.acite.tokifactor.services.ChatSession
import com.acite.tokifactor.services.OutgoingAttachment
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.vinceglb.filekit.core.PlatformFile

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class MainPageViewModel(
    private val chatEngine: ChatSession,
) : ViewModel() {

    val messages: List<ChatMessage>
        get() = chatEngine.visibleMessages

    val allMessages: List<ChatMessage>
        get() = chatEngine.messages

    val currentConversationId: String
        get() = chatEngine.currentConversationId

    val conversations: List<ConversationSummary>
        get() = chatEngine.conversations

    val soloState: SoloState
        get() = chatEngine.soloState

    val avatarBytes get() = chatEngine.avatarBytes

    val connected get() = chatEngine.connected

    val agentStatus get() = chatEngine.agentStatus

    val members get() = chatEngine.members

    val selectedAgentId get() = chatEngine.selectedAgentId

    val deviceId get() = chatEngine.deviceId

    val memberId get() = chatEngine.memberId

    val displayName get() = chatEngine.displayName

    val bio get() = chatEngine.bio

    val peerAvatars get() = chatEngine.peerAvatars

    init {
        chatEngine.start()
    }

    fun refreshSettings() = chatEngine.refreshSettings()

    fun selectAgent(agentId: String) = chatEngine.selectAgent(agentId)

    fun selectConversation(conversationId: String) =
        chatEngine.selectConversation(conversationId)

    fun startSolo(memberId: String, memberName: String) =
        chatEngine.startSolo(memberId, memberName)

    fun endSolo() = chatEngine.endSolo()

    fun addMessage(content: String, isError: Boolean = false) =
        chatEngine.addMessage(content, isError)

    fun removeMessage(id: String) = chatEngine.removeMessage(id)

    fun deleteMessages(ids: Collection<String>) = chatEngine.deleteMessages(ids)

    fun saveMessageFile(message: ChatMessage, dest: PlatformFile) =
        chatEngine.saveMessageFile(message, dest)

    fun cancelOutgoing(transferId: String) = chatEngine.cancelOutgoing(transferId)

    fun respondToApproval(approvalId: String, allow: Boolean) =
        chatEngine.respondToApproval(approvalId, allow)

    fun sendMessage(content: String, attachments: List<OutgoingAttachment> = emptyList()) =
        chatEngine.sendMessage(content, attachments)

    fun sendPicture(file: PlatformFile) = chatEngine.sendPicture(file)

    fun sendFile(file: PlatformFile) = chatEngine.sendFile(file)
}
