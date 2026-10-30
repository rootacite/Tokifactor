package com.acite.tokifactor.model

import java.nio.charset.StandardCharsets
import java.util.UUID

data class TextEnvelope(
    val sender: String,
    val text: String,
    val senderId: String = "",
    val avatarHash: String = "",
    val messageId: String = "",
    val sentAt: Long = 0L,
    val metadata: Map<String, String> = emptyMap(),
) {
    fun encode(): String {
        val pairs = mutableListOf(
            "sender" to sender,
            "text" to text,
            "id" to senderId,
            "avatarHash" to avatarHash,
        )
        if (messageId.isNotEmpty()) pairs += "messageId" to messageId
        if (sentAt > 0L) pairs += "sentAt" to sentAt.toString()
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
            "approvalMessage",
            "attachmentCount",
            "attachmentIds",
            "attachmentNames",
            "attachments",
            "probeId",
            "protocolVersion",
            "agent",
            "agentId",
            "agentName",
            "memberId",
            "memberName",
            "sourceId",
            "memberKind",
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
            "waitingUser",
            "waitingOther",
            "waitingOtherTargets",
            "pendingApprovals",
            "uptimeS",
            "memoryTokens",
            "soloId",
            "soloTargetId",
            "soloTargetName",
            "soloStartedAt",
            "soloError",
        )

        fun decode(payload: String): TextEnvelope? {
            val sender = WireJson.extractString(payload, "sender") ?: return null
            val text = WireJson.extractString(payload, "text") ?: return null
            if (sender.isEmpty()) return null
            val senderId = WireJson.extractString(payload, "id") ?: ""
            val avatarHash = WireJson.extractString(payload, "avatarHash") ?: ""
            val messageId = WireJson.extractString(payload, "messageId") ?: ""
            val sentAt = WireJson.extractString(payload, "sentAt")?.toLongOrNull() ?: 0L
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
                messageId = messageId,
                sentAt = sentAt,
                metadata = metadata,
            )
        }

        fun messageId(senderId: String, sentAt: Long, text: String): String =
            UUID.nameUUIDFromBytes(
                "$senderId\u0000$sentAt\u0000$text".toByteArray(StandardCharsets.UTF_8),
            ).toString()
    }
}
