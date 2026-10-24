package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PictureAttachmentTest {
    @Test
    fun pictureChunkCarriesLogicalMessageAttachmentMetadata() {
        val chunk = PictureChunk(
            transferId = "attachment-1",
            index = 0,
            total = 1,
            hash = "abc",
            encryptedData = "cipher",
            messageId = "message-1",
            attachmentIndex = 2,
            attachmentCount = 4,
            textCommit = true,
            captionEncrypted = "caption",
        )

        val decoded = PictureChunk.decode(chunk.encode())

        assertEquals("message-1", decoded?.messageId)
        assertEquals(2, decoded?.attachmentIndex)
        assertEquals(4, decoded?.attachmentCount)
        assertEquals(true, decoded?.textCommit)
    }

    @Test
    fun attachmentProgressParticipatesInMessageTransferState() {
        val message = ChatMessage(
            id = "m1",
            content = "check this",
            pic = null,
            attachments = listOf(
                ChatAttachment(
                    id = "a1",
                    name = "图片1",
                    isTransferring = true,
                    chunksReceived = 1,
                    chunksTotal = 4,
                )
            ),
        )

        assertTrue(message.isTransferring)
        assertEquals(0.25f, message.transferProgress)
        assertFalse(message.copy(
            attachments = message.attachments.map { it.copy(isTransferring = false, chunksReceived = 4) }
        ).isTransferring)
    }
}
