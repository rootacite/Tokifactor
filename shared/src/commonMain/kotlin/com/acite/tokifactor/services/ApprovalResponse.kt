package com.acite.tokifactor.services

import com.acite.tokifactor.model.GROUP_CONVERSATION
import com.acite.tokifactor.model.soloConversationId

internal const val MAX_APPROVAL_MESSAGE_CHARS = 2000

internal fun normalizeApprovalMessage(message: String): String =
    message.trim().take(MAX_APPROVAL_MESSAGE_CHARS)

internal fun approvalResponseCommand(
    approvalId: String,
    allow: Boolean,
    message: String = "",
): String {
    val command = if (allow) "akid-approve $approvalId" else "akid-deny $approvalId"
    if (allow) return command
    val normalized = normalizeApprovalMessage(message)
    return if (normalized.isEmpty()) command else "$command $normalized"
}

internal fun approvalResponseMetadata(
    allow: Boolean,
    message: String,
): Map<String, String> {
    if (allow) return emptyMap()
    val normalized = normalizeApprovalMessage(message)
    return if (normalized.isEmpty()) emptyMap() else mapOf("approvalMessage" to normalized)
}

/**
 * Approval replies must use the magic of the conversation that produced the
 * approval card. A solo approval is encrypted with the target member's solo
 * magic; group approvals stay on the group magic.
 */
internal fun approvalResponseConversationId(
    messageConversationId: String,
    senderId: String,
    soloActive: Boolean,
    soloTargetId: String,
): String {
    if (messageConversationId.startsWith("solo:")) {
        return messageConversationId
    }
    if (
        soloActive &&
        soloTargetId.isNotBlank() &&
        senderId == soloTargetId
    ) {
        return soloConversationId(soloTargetId)
    }
    return GROUP_CONVERSATION
}
