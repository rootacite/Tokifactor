package com.acite.tokifactor.model

data class TextEnvelope(
    val sender: String,
    val text: String,
    val senderId: String = "",
    val avatarHash: String = "",
    val metadata: Map<String, String> = emptyMap(),
) {
    fun encode(): String {
        val pairs = mutableListOf(
            "sender" to sender,
            "text" to text,
            "id" to senderId,
            "avatarHash" to avatarHash,
        )
        STRUCTURED_KEYS.forEach { key ->
            metadata[key]?.let { pairs += key to it }
        }
        return WireJson.encodeObject(*pairs.toTypedArray())
    }

    companion object {
        val STRUCTURED_KEYS = listOf(
            "schema",
            "kind",
            "turnId",
            "segment",
            "seq",
            "phase",
            "format",
            "toolCallId",
            "toolName",
            "toolStatus",
            "approvalId",
            "requesterId",
            "timeoutS",
            "details",
            "decision",
            "probeId",
            "protocolVersion",
            "agent",
            "agentId",
            "agentName",
            "description",
            "aliases",
            "model",
            "reasoning",
            "capabilities",
            "backendReady",
            "channelReady",
            "sandboxReady",
            "contextLimit",
            "contextTokens",
            "queueDepth",
            "activeTurn",
            "pendingApprovals",
            "uptimeS",
            "memoryTokens",
        )

        fun decode(payload: String): TextEnvelope? {
            val sender = WireJson.extractString(payload, "sender") ?: return null
            val text = WireJson.extractString(payload, "text") ?: return null
            if (sender.isEmpty()) return null
            val senderId = WireJson.extractString(payload, "id") ?: ""
            val avatarHash = WireJson.extractString(payload, "avatarHash") ?: ""
            val metadata = buildMap {
                STRUCTURED_KEYS.forEach { key ->
                    WireJson.extractString(payload, key)?.let { put(key, it) }
                }
            }
            return TextEnvelope(
                sender = sender,
                text = text,
                senderId = senderId,
                avatarHash = avatarHash,
                metadata = metadata,
            )
        }
    }
}
