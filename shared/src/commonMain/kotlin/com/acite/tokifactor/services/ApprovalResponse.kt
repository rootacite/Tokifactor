package com.acite.tokifactor.services

import com.acite.tokifactor.model.GROUP_CONVERSATION
import com.acite.tokifactor.model.soloConversationId

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
