package com.acite.tokifactor.daemon

import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.services.ChatEngineEvent
import com.acite.tokifactor.services.ChatSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

object DaemonProtocol {
    const val VERSION = 1

    const val TYPE_HELLO = "hello"
    const val TYPE_SNAPSHOT = "snapshot"
    const val TYPE_EVENT = "event"
    const val TYPE_COMMAND = "command"
    const val TYPE_ACK = "ack"
    const val TYPE_ERROR = "error"

    const val COMMAND_SEND_TEXT = "send_text"
    const val COMMAND_SEND_FILE = "send_file"
    const val COMMAND_SEND_PICTURE = "send_picture"
    const val COMMAND_CANCEL_TRANSFER = "cancel_transfer"
    const val COMMAND_DELETE_MESSAGES = "delete_messages"
    const val COMMAND_CLEAR_HISTORY = "clear_history"
    const val COMMAND_RESPOND_APPROVAL = "respond_approval"
    const val COMMAND_RELOAD_SETTINGS = "reload_settings"
    const val COMMAND_ADD_SYSTEM_MESSAGE = "add_system_message"
    const val COMMAND_PING = "ping"

    const val EVENT_MESSAGE_UPSERT = "message_upsert"
    const val EVENT_MESSAGES_REMOVED = "messages_removed"
    const val EVENT_HISTORY_CLEARED = "history_cleared"
    const val EVENT_CONNECTION = "connection"
    const val EVENT_MEMBERS = "members"
    const val EVENT_PEER_AVATAR = "peer_avatar"
}

@Serializable
data class DaemonClientFrame(
    val type: String = DaemonProtocol.TYPE_COMMAND,
    val version: Int = DaemonProtocol.VERSION,
    val requestId: String = "",
    val command: String = "",
    val text: String = "",
    val path: String = "",
    val transferId: String = "",
    val approvalId: String = "",
    val allow: Boolean = false,
    val ids: List<String> = emptyList(),
    val content: String = "",
    val isError: Boolean = false,
)

@Serializable
data class DaemonServerFrame(
    val type: String,
    val revision: Long = 0L,
    val snapshot: DaemonSnapshot? = null,
    val event: DaemonEvent? = null,
    val version: Int = DaemonProtocol.VERSION,
    val requestId: String = "",
    val ok: Boolean = true,
    val error: String? = null,
)

@Serializable
data class DaemonSnapshot(
    val messages: List<WireChatMessage> = emptyList(),
    val connected: Boolean = false,
    val status: String = "Connecting…",
    val members: List<WireAgentStatus> = emptyList(),
    val peerAvatars: Map<String, String> = emptyMap(),
)

@Serializable
data class DaemonEvent(
    val kind: String,
    val message: WireChatMessage? = null,
    val ids: List<String> = emptyList(),
    val connected: Boolean = false,
    val status: String = "",
    val members: List<WireAgentStatus> = emptyList(),
    val peerId: String = "",
    val peerAvatar: String = "",
)

@Serializable
data class WireChatMessage(
    val id: String,
    val content: String = "",
    val pic: String = "",
    val isError: Boolean = false,
    val chunksReceived: Int = 0,
    val chunksTotal: Int = 0,
    val sender: String = "",
    val senderId: String = "",
    val isMine: Boolean = false,
    val isSystem: Boolean = false,
    val fileName: String? = null,
    val fileBytes: String = "",
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val isChunkedText: Boolean = false,
    val agentKind: String = "",
    val turnId: String = "",
    val segment: Int = 0,
    val seq: Int = 0,
    val phase: String = "",
    val markdown: Boolean = false,
    val toolCallId: String = "",
    val toolName: String = "",
    val approvalId: String = "",
    val requesterId: String = "",
    val timeoutS: Int = 0,
    val details: String = "",
    val status: String = "",
    val sentAt: Long = 0L,
)

@Serializable
data class WireAgentStatus(
    val present: Boolean = false,
    val lastSeenAt: Long = 0L,
    val agentId: String = "",
    val agentName: String = "",
    val description: String = "",
    val aliases: String = "",
    val model: String = "",
    val reasoning: String = "",
    val contextLimit: Int = 0,
    val contextTokens: Int = 0,
    val backendReady: Boolean = false,
    val channelReady: Boolean = false,
    val sandboxReady: Boolean = false,
    val queueDepth: Int = 0,
    val activeTurn: Boolean = false,
    val pendingApprovals: Int = 0,
    val uptimeS: Long = 0L,
    val memoryTokens: Int = 0,
)

val DaemonJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun ChatSession.snapshot(): DaemonSnapshot = DaemonSnapshot(
    messages = messages.map { it.toWire() },
    connected = connected,
    status = connectionStatus.value,
    members = members.map { it.toWire() },
    peerAvatars = peerAvatars.mapValues { (_, bytes) -> encodeBytes(bytes) },
)

fun ChatEngineEvent.toDaemonEvent(): DaemonEvent? = when (this) {
    is ChatEngineEvent.MessageUpsert -> DaemonEvent(
        kind = DaemonProtocol.EVENT_MESSAGE_UPSERT,
        message = message.toWire(),
    )
    is ChatEngineEvent.MessagesRemoved -> DaemonEvent(
        kind = DaemonProtocol.EVENT_MESSAGES_REMOVED,
        ids = ids.toList(),
    )
    ChatEngineEvent.HistoryCleared -> DaemonEvent(
        kind = DaemonProtocol.EVENT_HISTORY_CLEARED,
    )
    is ChatEngineEvent.ConnectionChanged -> DaemonEvent(
        kind = DaemonProtocol.EVENT_CONNECTION,
        connected = connected,
        status = status,
    )
    is ChatEngineEvent.MembersChanged -> DaemonEvent(
        kind = DaemonProtocol.EVENT_MEMBERS,
        members = members.map { it.toWire() },
    )
    is ChatEngineEvent.PeerAvatarChanged -> DaemonEvent(
        kind = DaemonProtocol.EVENT_PEER_AVATAR,
        peerId = peerId,
        peerAvatar = bytes?.let(::encodeBytes).orEmpty(),
    )
}

fun ChatMessage.toWire(): WireChatMessage = WireChatMessage(
    id = id,
    content = content,
    pic = pic?.let(::encodeBytes).orEmpty(),
    isError = isError,
    chunksReceived = chunksReceived,
    chunksTotal = chunksTotal,
    sender = sender,
    senderId = senderId,
    isMine = isMine,
    isSystem = isSystem,
    fileName = fileName,
    fileBytes = fileBytes?.let(::encodeBytes).orEmpty(),
    filePath = filePath,
    fileSize = fileSize,
    isChunkedText = isChunkedText,
    agentKind = agentKind,
    turnId = turnId,
    segment = segment,
    seq = seq,
    phase = phase,
    markdown = markdown,
    toolCallId = toolCallId,
    toolName = toolName,
    approvalId = approvalId,
    requesterId = requesterId,
    timeoutS = timeoutS,
    details = details,
    status = status,
    sentAt = sentAt,
)

fun WireChatMessage.toChatMessage(): ChatMessage = ChatMessage(
    id = id,
    content = content,
    pic = decodeBytes(pic),
    isError = isError,
    chunksReceived = chunksReceived,
    chunksTotal = chunksTotal,
    sender = sender,
    senderId = senderId,
    isMine = isMine,
    isSystem = isSystem,
    fileName = fileName,
    fileBytes = decodeBytes(fileBytes),
    filePath = filePath,
    fileSize = fileSize,
    isChunkedText = isChunkedText,
    agentKind = agentKind,
    turnId = turnId,
    segment = segment,
    seq = seq,
    phase = phase,
    markdown = markdown,
    toolCallId = toolCallId,
    toolName = toolName,
    approvalId = approvalId,
    requesterId = requesterId,
    timeoutS = timeoutS,
    details = details,
    status = status,
    sentAt = sentAt,
)

fun AgentStatus.toWire(): WireAgentStatus = WireAgentStatus(
    present = present,
    lastSeenAt = lastSeenAt,
    agentId = agentId,
    agentName = agentName,
    description = description,
    aliases = aliases,
    model = model,
    reasoning = reasoning,
    contextLimit = contextLimit,
    contextTokens = contextTokens,
    backendReady = backendReady,
    channelReady = channelReady,
    sandboxReady = sandboxReady,
    queueDepth = queueDepth,
    activeTurn = activeTurn,
    pendingApprovals = pendingApprovals,
    uptimeS = uptimeS,
    memoryTokens = memoryTokens,
)

fun WireAgentStatus.toAgentStatus(): AgentStatus = AgentStatus(
    present = present,
    lastSeenAt = lastSeenAt,
    agentId = agentId,
    agentName = agentName,
    description = description,
    aliases = aliases,
    model = model,
    reasoning = reasoning,
    contextLimit = contextLimit,
    contextTokens = contextTokens,
    backendReady = backendReady,
    channelReady = channelReady,
    sandboxReady = sandboxReady,
    queueDepth = queueDepth,
    activeTurn = activeTurn,
    pendingApprovals = pendingApprovals,
    uptimeS = uptimeS,
    memoryTokens = memoryTokens,
)

private fun encodeBytes(bytes: ByteArray): String = Base64.encode(bytes)

private fun decodeBytes(value: String): ByteArray? {
    if (value.isEmpty()) return null
    return try {
        Base64.decode(value)
    } catch (_: Exception) {
        null
    }
}
