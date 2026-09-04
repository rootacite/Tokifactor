package com.acite.tokifactor.model

data class ChatMessage(
    val id: String,
    val content: String,
    val pic: ByteArray?,
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
) {
    val isTransferring: Boolean
        get() = chunksTotal > 0 && chunksReceived < chunksTotal

    val transferProgress: Float
        get() = if (chunksTotal <= 0) 1f else chunksReceived.toFloat() / chunksTotal.toFloat()

    val isFile: Boolean
        get() = fileName != null

    val isImage: Boolean
        get() = !isFile && !isChunkedText && (pic != null || filePath != null || chunksTotal > 0)

    val shouldPersist: Boolean
        get() {
            if (isTransferring) return false
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
        if (content != other.content) return false
        if (sender != other.sender) return false
        if (senderId != other.senderId) return false
        if (fileName != other.fileName) return false
        if (filePath != other.filePath) return false
        if (fileSize != other.fileSize) return false
        if (isChunkedText != other.isChunkedText) return false
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
        result = 31 * result + content.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + isChunkedText.hashCode()
        result = 31 * result + (pic?.contentHashCode() ?: 0)
        result = 31 * result + (fileBytes?.contentHashCode() ?: 0)
        return result
    }
}
