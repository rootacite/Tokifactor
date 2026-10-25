package com.acite.tokifactor.model

data class ChatMessage(
    val id: String,
    val content: String,
    val pic: ByteArray?,
    val conversationId: String = GROUP_CONVERSATION,
    val isError: Boolean = false,
    val chunksReceived: Int = 0,
    val chunksTotal: Int = 0,
    val sender: String = "",
    val senderId: String = "",
    val isMine: Boolean = false,
    val isSystem: Boolean = false,
    val fileName: String? = null,
    val fileBytes: ByteArray? = null,
    val filePath: String? = null,
    val fileSize: Long = 0,
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
    val attachments: List<ChatAttachment> = emptyList(),
) {
    val isAgent: Boolean
        get() = agentKind.isNotEmpty()

    val isTransferring: Boolean
        get() = (chunksTotal > 0 && chunksReceived < chunksTotal) ||
            attachments.any { it.isTransferring }

    val transferProgress: Float
        get() {
            if (attachments.isNotEmpty()) {
                return attachments.map { it.progress }.average().toFloat()
            }
            return if (chunksTotal <= 0) 1f else chunksReceived.toFloat() / chunksTotal.toFloat()
        }

    val isFile: Boolean
        get() = fileName != null

    val isImage: Boolean
        get() = attachments.isEmpty() && !isFile && !isChunkedText && (pic != null || filePath != null || chunksTotal > 0)

    val hasAttachments: Boolean
        get() = attachments.isNotEmpty()

    val shouldPersist: Boolean
        get() {
            if (isTransferring) return false
            if (isAgent) {
                return when (agentKind) {
                    "turn" -> phase == "final" || phase == "completed"
                    "tool" -> status == "success" || status == "error"
                    "approval" -> status == "allow" || status == "deny" || status == "timeout"
                    "error" -> true
                    else -> false
                }
            }
            if (isSystem && !isError) {
                if (content.startsWith("Welcome to TokiFactor")) return false
                if (content.startsWith("Connected to ")) return false
                if (content.startsWith("Connection failed")) return false
            }
            return true
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChatMessage

        if (isError != other.isError) return false
        if (isMine != other.isMine) return false
        if (isSystem != other.isSystem) return false
        if (chunksReceived != other.chunksReceived) return false
        if (chunksTotal != other.chunksTotal) return false
        if (id != other.id) return false
        if (conversationId != other.conversationId) return false
        if (content != other.content) return false
        if (sender != other.sender) return false
        if (senderId != other.senderId) return false
        if (fileName != other.fileName) return false
        if (filePath != other.filePath) return false
        if (fileSize != other.fileSize) return false
        if (isChunkedText != other.isChunkedText) return false
        if (agentKind != other.agentKind) return false
        if (turnId != other.turnId) return false
        if (segment != other.segment) return false
        if (seq != other.seq) return false
        if (phase != other.phase) return false
        if (markdown != other.markdown) return false
        if (toolCallId != other.toolCallId) return false
        if (toolName != other.toolName) return false
        if (approvalId != other.approvalId) return false
        if (requesterId != other.requesterId) return false
        if (timeoutS != other.timeoutS) return false
        if (details != other.details) return false
        if (status != other.status) return false
        if (sentAt != other.sentAt) return false
        if (attachments != other.attachments) return false
        if (!pic.contentEquals(other.pic)) return false
        if (!fileBytes.contentEquals(other.fileBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isError.hashCode()
        result = 31 * result + isMine.hashCode()
        result = 31 * result + isSystem.hashCode()
        result = 31 * result + chunksReceived
        result = 31 * result + chunksTotal
        result = 31 * result + id.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + isChunkedText.hashCode()
        result = 31 * result + agentKind.hashCode()
        result = 31 * result + turnId.hashCode()
        result = 31 * result + segment
        result = 31 * result + seq
        result = 31 * result + phase.hashCode()
        result = 31 * result + markdown.hashCode()
        result = 31 * result + toolCallId.hashCode()
        result = 31 * result + toolName.hashCode()
        result = 31 * result + approvalId.hashCode()
        result = 31 * result + requesterId.hashCode()
        result = 31 * result + timeoutS
        result = 31 * result + details.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + sentAt.hashCode()
        result = 31 * result + attachments.hashCode()
        result = 31 * result + (pic?.contentHashCode() ?: 0)
        result = 31 * result + (fileBytes?.contentHashCode() ?: 0)
        return result
    }
}
