package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextEnvelopeTest {
    @Test
    fun messageIdIsStableAndScopedToSenderTimeAndContent() {
        val id = TextEnvelope.messageId("alice", 1_700_000_000_000L, "hello")

        assertEquals(id, TextEnvelope.messageId("alice", 1_700_000_000_000L, "hello"))
        assertNotEquals(id, TextEnvelope.messageId("alice", 1_700_000_000_001L, "hello"))
        assertNotEquals(id, TextEnvelope.messageId("alice", 1_700_000_000_000L, "hello!"))
        assertNotEquals(id, TextEnvelope.messageId("bob", 1_700_000_000_000L, "hello"))
        assertTrue(id.matches(Regex("[0-9a-f-]{36}")))
    }

    @Test
    fun messageIdentityRoundTripsThroughWireFormat() {
        val envelope = TextEnvelope(
            sender = "Alice",
            text = "hello",
            senderId = "alice",
            avatarHash = "avatar",
            messageId = "00000000-0000-0000-0000-000000000001",
            sentAt = 1_700_000_000_000L,
            metadata = mapOf("kind" to "chat"),
        )

        val decoded = TextEnvelope.decode(envelope.encode())

        assertEquals(envelope, decoded)
    }

    @Test
    fun legacyEnvelopeWithoutIdentityStillDecodes() {
        val legacy = """{"sender":"Alice","text":"hello","id":"alice","avatarHash":""}"""

        val decoded = TextEnvelope.decode(legacy)

        assertEquals("", decoded?.messageId)
        assertEquals(0L, decoded?.sentAt)
    }

    @Test
    fun longTextChunkCarriesIdentityAndTimestamp() {
        val chunk = TextChunk(
            transferId = "00000000-0000-0000-0000-000000000001",
            index = 2,
            total = 5,
            hash = "hash",
            encryptedData = "ciphertext",
            sender = "Alice",
            senderId = "alice",
            messageId = "00000000-0000-0000-0000-000000000001",
            sentAt = 1_700_000_000_000L,
        )

        val decoded = TextChunk.decode(chunk.encode())

        assertEquals(chunk.messageId, decoded?.messageId)
        assertEquals(chunk.sentAt, decoded?.sentAt)
        assertEquals(chunk.transferId, decoded?.transferId)
    }
}
