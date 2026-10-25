package com.acite.tokifactor.services

import com.acite.tokifactor.model.ChatAttachment
import com.acite.tokifactor.model.ChatMessage
import java.nio.file.Files
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatHistoryAttachmentTest {
    @Test
    fun mixedMessageAttachmentsSurvivePersistenceWithLocalBlobs() {
        val oldHome = System.getProperty("user.home")
        val home = Files.createTempDirectory("tokifactor-history-attachments").toFile()
        val source = Files.createTempFile("tokifactor-source-", ".png").toFile()
        source.writeBytes(byteArrayOf(1, 2, 3, 4))
        try {
            System.setProperty("user.home", home.absolutePath)
            ChatHistoryStore.clear()
            ChatHistoryStore.save(
                listOf(
                    ChatMessage(
                        id = "message-1",
                        content = "look at 图片1",
                        pic = null,
                        sender = "Alice",
                        senderId = "alice",
                        sentAt = 123L,
                        conversationId = "solo:legacy_aki",
                        attachments = listOf(
                            ChatAttachment(
                                id = "attachment-1",
                                name = "图片1",
                                mime = "image/png",
                                filePath = source.absolutePath,
                                size = 4,
                            )
                        ),
                    )
                )
            )

            val loaded = ChatHistoryStore.load().single()
            assertEquals("look at 图片1", loaded.content)
            assertEquals("solo:legacy_aki", loaded.conversationId)
            assertEquals("图片1", loaded.attachments.single().name)
            val path = loaded.attachments.single().filePath
            assertTrue(path != null && java.io.File(path).isFile)
            assertEquals(listOf<Byte>(1, 2, 3, 4), java.io.File(path).readBytes().toList())
        } finally {
            check(System.getProperty("user.home") == home.absolutePath) {
                "refusing to clear history outside the test home"
            }
            ChatHistoryStore.clear()
            System.setProperty("user.home", oldHome)
            home.deleteRecursively()
            source.delete()
        }
    }
}
