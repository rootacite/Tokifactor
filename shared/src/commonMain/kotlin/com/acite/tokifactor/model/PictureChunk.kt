package com.acite.tokifactor.model

/**
 * On-wire picture fragment: `(id, hash, encrypted_data)` plus optional `sender`.
 *
 * `id` is `{transferId}:{index}:{total}` so a receiver can reassemble without extra fields.
 * `hash` is SHA-256 hex of the full plaintext image (same on every chunk).
 * `encrypted_data` is Base64 of one slice of the full-image ciphertext.
 * `captionEncrypted` is Base64 of the encrypted caption, when the sender attached one.
 */
data class PictureChunk(
    val transferId: String,
    val index: Int,
    val total: Int,
    val hash: String,
    val encryptedData: String,
    val sender: String = "Unknown",
    val senderId: String = "",
    val avatarHash: String = "",
    val sentAt: Long = 0L,
    val abort: Boolean = false,
    val reason: String = "",
    val size: Long = 0,
    val captionEncrypted: String = "",
    val messageId: String = "",
    val attachmentIndex: Int = 0,
    val attachmentCount: Int = 1,
    val textCommit: Boolean = false,
) {
    fun encode(): String {
        val id = "$transferId:$index:$total"
        return if (abort) {
            WireJson.encodeObject(
                "id" to id,
                "hash" to hash,
                "encrypted_data" to encryptedData,
                "sender" to sender,
                "senderId" to senderId,
                "status" to "abort",
                "reason" to reason.ifBlank { "cancel" },
            )
        } else {
            val pairs = mutableListOf(
                "id" to id,
                "hash" to hash,
                "encrypted_data" to encryptedData,
                "sender" to sender,
                "senderId" to senderId,
                "avatarHash" to avatarHash,
                "size" to size.toString(),
            )
            if (sentAt > 0L) pairs += "sentAt" to sentAt.toString()
            if (messageId.isNotEmpty()) pairs += "messageId" to messageId
            pairs += "attachmentIndex" to attachmentIndex.toString()
            pairs += "attachmentCount" to attachmentCount.toString()
            if (textCommit) pairs += "textCommit" to "true"
            if (captionEncrypted.isNotEmpty()) {
                pairs += "caption" to captionEncrypted
            }
            WireJson.encodeObject(*pairs.toTypedArray())
        }
    }

    companion object {
        fun abort(
            transferId: String,
            sender: String,
            senderId: String = "",
            reason: String = "cancel",
        ): PictureChunk = PictureChunk(
            transferId = transferId,
            index = 0,
            total = 1,
            hash = "",
            encryptedData = "",
            sender = sender,
            senderId = senderId,
            abort = true,
            reason = reason.ifBlank { "cancel" },
        )

        fun decode(payload: String): PictureChunk? {
            val id = WireJson.extractString(payload, "id") ?: return null
            val sender = WireJson.extractString(payload, "sender")?.ifBlank { null } ?: "Unknown"
            val senderId = WireJson.extractString(payload, "senderId") ?: ""
            val avatarHash = WireJson.extractString(payload, "avatarHash") ?: ""
            val sentAt = WireJson.extractString(payload, "sentAt")?.toLongOrNull() ?: 0L
            val status = WireJson.extractString(payload, "status")
            val transferId = id.substringBefore(':')
            if (transferId.isEmpty()) return null
            if (status.equals("abort", ignoreCase = true)) {
                val reason = WireJson.extractString(payload, "reason") ?: "cancel"
                return abort(transferId, sender, senderId, reason)
            }
            val hash = WireJson.extractString(payload, "hash") ?: return null
            val encryptedData = WireJson.extractString(payload, "encrypted_data") ?: return null
            val parts = id.split(':')
            if (parts.size != 3) return null
            val index = parts[1].toIntOrNull() ?: return null
            val total = parts[2].toIntOrNull() ?: return null
            if (index < 0 || total <= 0 || index >= total) return null
            if (hash.isEmpty() || encryptedData.isEmpty()) return null
            val messageId = WireJson.extractString(payload, "messageId") ?: ""
            val attachmentIndex = WireJson.extractString(payload, "attachmentIndex")?.toIntOrNull()
                ?.coerceAtLeast(0) ?: 0
            val attachmentCount = WireJson.extractString(payload, "attachmentCount")?.toIntOrNull()
                ?.coerceAtLeast(1) ?: 1
            val textCommit = WireJson.extractString(payload, "textCommit")
                ?.equals("true", ignoreCase = true) == true
            return PictureChunk(
                transferId = transferId,
                index = index,
                total = total,
                hash = hash,
                encryptedData = encryptedData,
                sender = sender,
                senderId = senderId,
                avatarHash = avatarHash,
                sentAt = sentAt,
                size = WireJson.extractString(payload, "size")?.toLongOrNull()?.coerceAtLeast(0) ?: 0,
                captionEncrypted = WireJson.extractString(payload, "caption") ?: "",
                messageId = messageId,
                attachmentIndex = attachmentIndex,
                attachmentCount = attachmentCount,
                textCommit = textCommit,
            )
        }
    }
}
