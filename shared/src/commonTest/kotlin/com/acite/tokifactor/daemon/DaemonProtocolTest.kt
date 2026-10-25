package com.acite.tokifactor.daemon

import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.ChatAttachment
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.legacyMemberId
import com.acite.tokifactor.services.ChatEngineEvent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.io.encoding.Base64

class DaemonProtocolTest {
    @Test
    fun snapshotFramesRoundTripMessagesAndAvatars() {
        val message = ChatMessage(
            id = "message-1",
            content = "hello",
            pic = byteArrayOf(1, 2, 3),
            sender = "Alice",
            senderId = "alice",
            fileName = "photo.png",
            fileBytes = byteArrayOf(4, 5),
            fileSize = 2,
            sentAt = 42L,
            attachments = listOf(
                ChatAttachment(
                    id = "attachment-1",
                    name = "图片1",
                    mime = "image/png",
                    filePath = "/tmp/image-1.png",
                    size = 123,
                )
            ),
        )
        val member = AgentStatus(
            present = true,
            memberId = "aki",
            memberName = "Aki",
            description = "Aki bio",
            memberKind = "agent",
            queueDepth = 2,
            waitingUser = true,
        )
        val frame = DaemonServerFrame(
            type = DaemonProtocol.TYPE_SNAPSHOT,
            revision = 7L,
            snapshot = DaemonSnapshot(
                messages = listOf(message.toWire()),
                connected = true,
                status = "Connected",
                members = listOf(member.toWire()),
                peerAvatars = mapOf("alice" to "AQID"),
            ),
        )

        val decoded = DaemonJson.decodeFromString(
            DaemonServerFrame.serializer(),
            DaemonJson.encodeToString(DaemonServerFrame.serializer(), frame),
        )
        val snapshot = assertNotNull(decoded.snapshot)
        val decodedMessage = snapshot.messages.single().toChatMessage()

        assertEquals(7L, decoded.revision)
        assertEquals(message, decodedMessage)
        assertContentEquals(message.pic, decodedMessage.pic)
        assertEquals(message.attachments, decodedMessage.attachments)
        assertEquals(member, snapshot.members.single().toAgentStatus())
        assertEquals("AQID", snapshot.peerAvatars["alice"])
    }

    @Test
    fun commandsCarryRequestIdAndPayload() {
        val command = DaemonClientFrame(
            requestId = "request-1",
            command = DaemonProtocol.COMMAND_DELETE_MESSAGES,
            ids = listOf("a", "b"),
            attachmentPaths = listOf("/tmp/a.png"),
            attachmentNames = listOf("图片1"),
        )

        val decoded = DaemonJson.decodeFromString(
            DaemonClientFrame.serializer(),
            DaemonJson.encodeToString(DaemonClientFrame.serializer(), command),
        )

        assertEquals(command, decoded)
        assertEquals(listOf("a", "b"), decoded.ids)
        assertEquals(listOf("/tmp/a.png"), decoded.attachmentPaths)
        assertEquals(listOf("图片1"), decoded.attachmentNames)
    }

    @Test
    fun engineEventsMapToWireEvents() {
        val message = ChatMessage(
            id = "message-1",
            content = "hello",
            pic = null,
        )

        val upsert = ChatEngineEvent.MessageUpsert(message).toDaemonEvent()
        val removed = ChatEngineEvent.MessagesRemoved(setOf("message-1")).toDaemonEvent()
        val avatar = ChatEngineEvent.PeerAvatarChanged("alice", byteArrayOf(9)).toDaemonEvent()

        assertEquals(DaemonProtocol.EVENT_MESSAGE_UPSERT, upsert?.kind)
        assertEquals(message, upsert?.message?.toChatMessage())
        assertEquals(DaemonProtocol.EVENT_MESSAGES_REMOVED, removed?.kind)
        assertEquals(listOf("message-1"), removed?.ids)
        assertEquals(DaemonProtocol.EVENT_PEER_AVATAR, avatar?.kind)
        assertEquals("alice", avatar?.peerId)
        assertContentEquals(byteArrayOf(9), Base64.decode(avatar?.peerAvatar.orEmpty()))
    }

    @Test
    fun legacyDaemonMemberKeepsAgentIdButMigratesToMemberId() {
        val legacy = WireAgentStatus(
            present = true,
            agentId = "aki",
            agentName = "Aki",
        )

        val member = legacy.toAgentStatus()

        assertEquals(legacyMemberId("Aki"), member.memberId)
        assertEquals("Aki", member.memberName)
    }
}
