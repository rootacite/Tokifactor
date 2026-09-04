package com.acite.tokifactor.model

/**
 * On-wire fragment for a long text message (UTF-8 body over 4 KiB).
 *
 * Same envelope as [PictureChunk]: `id` is `{transferId}:{index}:{total}`,
 * `hash` is SHA-256 hex of the full plaintext, `encrypted_data` is Base64 of
 * one slice. Published on `tokifactor/text` so short [TextEnvelope] payloads
 * stay Base64 blobs and these stay JSON.
 */
data class TextChunk(
    val transferId: String,
    val index: Int,
    val total: Int,
    val hash: String,
    val encryptedData: String,
    val sender: String = "Unknown",
    val senderId: String = "",
    val avatarHash: String = "",
    val abort: Boolean = false,
    val reason: String = "",
    val size: Long = 0,
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
            WireJson.encodeObject(
                "id" to id,
                "hash" to hash,
                "encrypted_data" to encryptedData,
                "sender" to sender,
                "senderId" to senderId,
                "avatarHash" to avatarHash,
                "size" to size.toString(),
            )
        }
    }

    companion object {
        fun abort(
            transferId: String,
            sender: String,
            senderId: String = "",
            reason: String = "cancel",
        ): TextChunk = TextChunk(
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

        fun decode(payload: String): TextChunk? {
            val id = WireJson.extractString(payload, "id") ?: return null
            val sender = WireJson.extractString(payload, "sender")?.ifBlank { null } ?: "Unknown"
            val senderId = WireJson.extractString(payload, "senderId") ?: ""
            val avatarHash = WireJson.extractString(payload, "avatarHash") ?: ""
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
            return TextChunk(
                transferId = transferId,
                index = index,
                total = total,
                hash = hash,
                encryptedData = encryptedData,
                sender = sender,
                senderId = senderId,
                avatarHash = avatarHash,
                size = WireJson.extractString(payload, "size")?.toLongOrNull()?.coerceAtLeast(0) ?: 0,
            )
        }
    }
}
