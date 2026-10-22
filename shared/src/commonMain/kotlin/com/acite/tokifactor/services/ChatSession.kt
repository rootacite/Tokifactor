package com.acite.tokifactor.services

import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.InboundNotice
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatSession {
    val messages: List<ChatMessage>
    val avatarBytes: ByteArray?
    val connected: Boolean
    val agentStatus: AgentStatus
    val members: List<AgentStatus>
    val selectedAgentId: String?
    val deviceId: String
    val peerAvatars: Map<String, ByteArray>
    val connectionStatus: StateFlow<String>
    val inboundNotices: SharedFlow<InboundNotice>

    fun start()
    fun refreshSettings()
    fun selectAgent(agentId: String)
    fun addMessage(content: String, isError: Boolean = false)
    fun removeMessage(id: String)
    fun deleteMessages(ids: Collection<String>)
    fun clearHistory()
    fun saveMessageFile(message: ChatMessage, dest: PlatformFile)
    fun cancelOutgoing(transferId: String)
    fun respondToApproval(approvalId: String, allow: Boolean)
    fun sendMessage(content: String)
    fun sendPicture(file: PlatformFile)
    fun sendFile(file: PlatformFile)
}
