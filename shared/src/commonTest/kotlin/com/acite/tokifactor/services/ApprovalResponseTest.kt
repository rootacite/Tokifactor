package com.acite.tokifactor.services

import com.acite.tokifactor.model.GROUP_CONVERSATION
import com.acite.tokifactor.model.soloConversationId
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
