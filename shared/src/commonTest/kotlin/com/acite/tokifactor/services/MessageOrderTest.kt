package com.acite.tokifactor.services

import com.acite.tokifactor.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageOrderTest {
    @Test
    fun messagesAreInsertedByTimestampAndKeepTiesAfterOlderEntries() {
        val messages = listOf(message("a", 10L), message("b", 30L))

        assertEquals(0, insertionIndexBySentAt(messages, 5L))
        assertEquals(1, insertionIndexBySentAt(messages, 20L))
        assertEquals(2, insertionIndexBySentAt(messages, 30L))
        assertEquals(2, insertionIndexBySentAt(messages, 40L))
    }

    private fun message(id: String, sentAt: Long) = ChatMessage(
        id = id,
        content = id,
        pic = null,
        sentAt = sentAt,
    )
}
