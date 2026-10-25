package com.acite.tokifactor.services

import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.SoloState

sealed interface ChatEngineEvent {
    data class MessageUpsert(val message: ChatMessage) : ChatEngineEvent
    data class MessagesRemoved(val ids: Set<String>) : ChatEngineEvent
    data object HistoryCleared : ChatEngineEvent
    data class ConnectionChanged(val connected: Boolean, val status: String) : ChatEngineEvent
    data class MembersChanged(val members: List<AgentStatus>) : ChatEngineEvent
    data class PeerAvatarChanged(val peerId: String, val bytes: ByteArray?) : ChatEngineEvent
    data class StateChanged(
        val currentConversationId: String,
        val soloState: SoloState,
    ) : ChatEngineEvent
}
