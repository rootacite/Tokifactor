package com.acite.tokifactor.services

import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.ConversationSummary
import com.acite.tokifactor.model.InboundNotice
import com.acite.tokifactor.model.SoloState
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class OutgoingAttachment(
    val file: PlatformFile,
    val name: String,
)

interface ChatSession {
    val messages: List<ChatMessage>
    val visibleMessages: List<ChatMessage>
    val currentConversationId: String
    val conversations: List<ConversationSummary>
    val soloState: SoloState
    val avatarBytes: ByteArray?
    val connected: Boolean
    val agentStatus: AgentStatus
    val members: List<AgentStatus>
    val selectedAgentId: String?
    val deviceId: String
    val memberId: String
    val displayName: String
    val bio: String
    val peerAvatars: Map<String, ByteArray>
    val connectionStatus: StateFlow<String>
    val inboundNotices: SharedFlow<InboundNotice>

    fun start()
    fun refreshSettings()
    fun findMember(name: String): AgentStatus?
    fun selectAgent(agentId: String)
    fun selectConversation(conversationId: String)
    fun startSolo(memberId: String, memberName: String)
    fun endSolo()
    fun addMessage(content: String, isError: Boolean = false)
    fun removeMessage(id: String)
    fun deleteMessages(ids: Collection<String>)
    fun clearHistory()
    fun saveMessageFile(message: ChatMessage, dest: PlatformFile)
    fun cancelOutgoing(transferId: String)
    fun respondToApproval(
        approvalId: String,
        allow: Boolean,
        approvalMessage: String = "",
    )
    fun sendMessage(content: String, attachments: List<OutgoingAttachment> = emptyList())
    fun sendPicture(file: PlatformFile)
    fun sendFile(file: PlatformFile)
}
