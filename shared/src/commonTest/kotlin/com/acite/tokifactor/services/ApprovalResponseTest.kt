package com.acite.tokifactor.services

import com.acite.tokifactor.model.GROUP_CONVERSATION
import com.acite.tokifactor.model.soloConversationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovalResponseTest {
    @Test
    fun soloApprovalReplyUsesSoloConversationEvenIfCardWasTaggedAsGroup() {
        assertEquals(
            soloConversationId("legacy_aki"),
            approvalResponseConversationId(
                messageConversationId = GROUP_CONVERSATION,
                senderId = "legacy_aki",
                soloActive = true,
                soloTargetId = "legacy_aki",
            ),
        )
    }

    @Test
    fun explicitSoloConversationIsPreserved() {
        assertEquals(
            soloConversationId("legacy_aki"),
            approvalResponseConversationId(
                messageConversationId = soloConversationId("legacy_aki"),
                senderId = "legacy_aki",
                soloActive = true,
                soloTargetId = "legacy_aki",
            ),
        )
    }

    @Test
    fun groupApprovalReplyStaysOnGroupMagic() {
        assertEquals(
            GROUP_CONVERSATION,
            approvalResponseConversationId(
                messageConversationId = GROUP_CONVERSATION,
                senderId = "legacy_aki",
                soloActive = false,
                soloTargetId = "",
            ),
        )
    }

    @Test
    fun denialCommandAndMetadataCarryTrimmedMessage() {
        val message = "  不要执行 shell，改成只读检查  "

        assertEquals(
            "akid-deny approval-1 不要执行 shell，改成只读检查",
            approvalResponseCommand("approval-1", allow = false, message = message),
        )
        assertEquals(
            mapOf("approvalMessage" to "不要执行 shell，改成只读检查"),
            approvalResponseMetadata(allow = false, message = message),
        )
    }

    @Test
    fun allowResponseDoesNotCarryApprovalMessage() {
        assertEquals(
            "akid-approve approval-1",
            approvalResponseCommand("approval-1", allow = true, message = "ignored"),
        )
        assertTrue(approvalResponseMetadata(allow = true, message = "ignored").isEmpty())
    }

    @Test
    fun approvalMessageIsCapped() {
        val normalized = normalizeApprovalMessage("x".repeat(MAX_APPROVAL_MESSAGE_CHARS + 10))

        assertEquals(MAX_APPROVAL_MESSAGE_CHARS, normalized.length)
    }
}
