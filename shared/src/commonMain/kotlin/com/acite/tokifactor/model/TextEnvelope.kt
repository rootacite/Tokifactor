package com.acite.tokifactor.model

data class TextEnvelope(
    val sender: String,
    val text: String,
    val senderId: String = "",
    val avatarHash: String = "",
) {
    fun encode(): String = WireJson.encodeObject(
        "sender" to sender,
        "text" to text,
        "id" to senderId,
        "avatarHash" to avatarHash,
    )

    companion object {
        fun decode(payload: String): TextEnvelope? {
            val sender = WireJson.extractString(payload, "sender") ?: return null
            val text = WireJson.extractString(payload, "text") ?: return null
            if (sender.isEmpty()) return null
            val senderId = WireJson.extractString(payload, "id") ?: ""
            val avatarHash = WireJson.extractString(payload, "avatarHash") ?: ""
            return TextEnvelope(
                sender = sender,
                text = text,
                senderId = senderId,
                avatarHash = avatarHash,
            )
        }
    }
}
