package com.acite.tokifactor.daemon

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.acite.tokifactor.copyFileToPlatformFile
import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.aliasList
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.ConversationSummary
import com.acite.tokifactor.model.GROUP_CONVERSATION
import com.acite.tokifactor.model.SoloState
import com.acite.tokifactor.model.InboundNotice
import com.acite.tokifactor.model.canonicalMemberName
import com.acite.tokifactor.model.isMemberId
import com.acite.tokifactor.model.legacyMemberId
import com.acite.tokifactor.model.selectedMember
import com.acite.tokifactor.model.canonicalMemberName
import com.acite.tokifactor.services.ChatHistoryStore
import com.acite.tokifactor.services.ChatSession
import com.acite.tokifactor.services.OutgoingAttachment
import com.acite.tokifactor.services.SettingsStore
import com.acite.tokifactor.services.insertionIndexBySentAt
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.encoding.Base64

@SingleIn(AppScope::class)
class DaemonChatSession @Inject constructor(
    private val settingsStore: SettingsStore,
) : ChatSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1)
    )
    private val started = AtomicBoolean(false)
    private val writeLock = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<DaemonServerFrame>>()

    override val messages: List<ChatMessage>
        field = mutableStateListOf()

    override val visibleMessages: List<ChatMessage>
        get() = messages.filter { it.conversationId == currentConversationId }

    override var currentConversationId by mutableStateOf("group")
        private set

    override var soloState by mutableStateOf(SoloState())
        private set

    override val conversations: List<ConversationSummary>
        get() {
            val out = linkedMapOf<String, ConversationSummary>()
            out[GROUP_CONVERSATION] = ConversationSummary(GROUP_CONVERSATION, "Group chat")
            for (message in messages) {
                val id = message.conversationId
                if (!id.startsWith("solo:")) continue
                val memberId = id.removePrefix("solo:")
                val previous = out[id]
                out[id] = ConversationSummary(
                    id = id,
                    title = if (soloState.active && memberId == soloState.targetId) {
                        "Solo · ${soloState.targetName}"
                    } else {
                        "Solo · ${identityRegistryName(memberId)}"
                    },
                    memberId = memberId,
                    lastMessageAt = maxOf(previous?.lastMessageAt ?: 0L, message.sentAt),
                )
            }
            return out.values.sortedWith(
                compareBy<ConversationSummary> { it.id != GROUP_CONVERSATION }
                    .thenByDescending { it.lastMessageAt }
            )
        }

    private fun identityRegistryName(memberId: String): String = memberId

    override var avatarBytes by mutableStateOf(settingsStore.avatarBytes)
        private set

    override var connected by mutableStateOf(false)
        private set

    private val memberStates = mutableStateMapOf<String, AgentStatus>()

    override var selectedAgentId by mutableStateOf<String?>(null)
        private set

    override val members: List<AgentStatus>
        get() = memberStates.values
            .filter { it.present }
            .filter {
                !currentConversationId.startsWith("solo:") ||
                    it.memberId == currentConversationId.removePrefix("solo:")
            }
            .sortedWith(compareBy<AgentStatus>(
                { it.memberName.lowercase() != "aki" && !it.aliasList().any { alias -> alias.lowercase() == "aki" } },
                { it.memberName.lowercase() },
                { it.memberId },
            ))

    override val agentStatus: AgentStatus
        get() = selectedMember(members, selectedAgentId) ?: AgentStatus()

    override val deviceId: String
        get() = settingsStore.deviceId

    override val memberId: String
        get() = settingsStore.memberId

    override val displayName: String
        get() = settingsStore.displayName

    override val bio: String
        get() = settingsStore.bio

    override val peerAvatars = mutableStateMapOf<String, ByteArray>()

    private val _connectionStatus = MutableStateFlow("Starting tokifactord…")
    override val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _inboundNotices = MutableSharedFlow<InboundNotice>(extraBufferCapacity = 1)
    override val inboundNotices: SharedFlow<InboundNotice> = _inboundNotices.asSharedFlow()

    @Volatile
    private var channel: SocketChannel? = null

    @Volatile
    private var connectionWriter: BufferedWriter? = null

    @Volatile
    private var revision = 0L

    @Volatile
    private var lastServiceStartAttempt = 0L

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val local = try {
                ChatHistoryStore.load()
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                if (messages.isEmpty()) messages.addAll(local)
                if (messages.isNotEmpty()) {
                    val normalized = messages.map(::normalizeMessage)
                    if (normalized != messages) {
                        messages.clear()
                        messages.addAll(normalized)
                    }
                }
            }
            connectLoop()
        }
    }

    private suspend fun connectLoop() {
        var backoff = 1_000L
        while (scope.isActive) {
            setDaIpcStatus(false, "Starting tokifactord…")
            ensureServiceStarted()
            try {
                val socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)
                socketChannel.connect(UnixDomainSocketAddress.of(daemonSocketFile().toPath()))
                channel = socketChannel
                readConnection(socketChannel)
            } catch (e: Exception) {
                if (scope.isActive) {
                    setDaIpcStatus(false, "Daemon unavailable · retrying…")
                }
            } finally {
                channel = null
                connectionWriter = null
                failPending()
            }
            if (!scope.isActive) break
            delay(backoff)
            backoff = when {
                backoff < 2_000L -> 2_000L
                backoff < 5_000L -> 5_000L
                else -> 5_000L
            }
        }
    }

    private fun ensureServiceStarted() {
        val now = System.currentTimeMillis()
        if (now - lastServiceStartAttempt < 10_000L) return
        lastServiceStartAttempt = now
        try {
            val process = ProcessBuilder("systemctl", "--user", "start", "tokifactord.service")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
        } catch (_: Exception) {
        }
    }

    private suspend fun readConnection(socketChannel: SocketChannel) {
        val reader = BufferedReader(InputStreamReader(Channels.newInputStream(socketChannel), Charsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(Channels.newOutputStream(socketChannel), Charsets.UTF_8))
        connectionWriter = writer
        writeFrame(
            writer,
            DaemonClientFrame(
                type = DaemonProtocol.TYPE_HELLO,
                version = DaemonProtocol.VERSION,
            ),
        )

        var receivedSnapshot = false
        while (scope.isActive) {
            val line = reader.readLine() ?: break
            val frame = try {
                DaemonJson.decodeFromString(DaemonServerFrame.serializer(), line)
            } catch (_: Exception) {
                continue
            }
            when (frame.type) {
                DaemonProtocol.TYPE_SNAPSHOT -> {
                    frame.snapshot?.let { applySnapshot(frame.revision, it) }
                    receivedSnapshot = true
                    commandScope.launch { sendReloadSettings() }
                }
                DaemonProtocol.TYPE_EVENT -> {
                    if (receivedSnapshot && frame.revision > revision) {
                        revision = frame.revision
                        frame.event?.let { applyEvent(it) }
                    }
                }
                DaemonProtocol.TYPE_ACK, DaemonProtocol.TYPE_ERROR -> {
                    pending.remove(frame.requestId)?.complete(frame)
                }
            }
        }
    }

    private suspend fun writeFrame(writer: BufferedWriter, frame: Any) {
        writeLock.withLock {
            val line = when (frame) {
                is DaemonClientFrame -> DaemonJson.encodeToString(DaemonClientFrame.serializer(), frame)
                else -> return
            }
            writer.write(line)
            writer.newLine()
            writer.flush()
        }
    }

    private suspend fun sendCommand(frame: DaemonClientFrame): Boolean {
        val writer = connectionWriter ?: return false
        writeFrame(writer, frame)
        return true
    }

    private suspend fun request(frame: DaemonClientFrame): DaemonServerFrame? {
        if (connectionWriter == null) return null
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<DaemonServerFrame>()
        pending[requestId] = deferred
        try {
            if (!sendCommand(frame.copy(requestId = requestId))) return null
            return withTimeoutOrNull(10_000L) { deferred.await() }
        } catch (_: Exception) {
            return null
        } finally {
            pending.remove(requestId)
        }
    }

    private suspend fun sendReloadSettings() {
        request(DaemonClientFrame(command = DaemonProtocol.COMMAND_RELOAD_SETTINGS))
    }

    private suspend fun applySnapshot(snapshotRevision: Long, snapshot: DaemonSnapshot) {
        revision = snapshotRevision
        val messagesSnapshot = snapshot.messages.map { normalizeMessage(it.toChatMessage()) }
        val membersSnapshot = snapshot.members.map { it.toAgentStatus() }
        val avatars = buildMap {
            snapshot.peerAvatars.forEach { (key, value) ->
                val bytes = decodeAvatar(value) ?: return@forEach
                put(normalizeMemberId(key, key), bytes)
            }
        }
        withContext(Dispatchers.Main) {
            messages.clear()
            messages.addAll(messagesSnapshot)
            connected = snapshot.connected
            _connectionStatus.value = snapshot.status
            currentConversationId = snapshot.currentConversationId
            soloState = SoloState(
                active = snapshot.soloActive,
                phase = snapshot.soloPhase,
                sessionId = snapshot.soloSessionId,
                targetId = snapshot.soloTargetId,
                targetName = snapshot.soloTargetName,
                startedAt = snapshot.soloStartedAt,
                error = snapshot.soloError,
            )
            memberStates.clear()
            membersSnapshot.forEach { memberStates[it.memberId] = it }
            peerAvatars.clear()
            peerAvatars.putAll(avatars)
        }
    }

    private suspend fun applyEvent(event: DaemonEvent) {
        withContext(Dispatchers.Main) {
            when (event.kind) {
                DaemonProtocol.EVENT_MESSAGE_UPSERT -> {
                    val message = event.message?.toChatMessage()?.let(::normalizeMessage)
                        ?: return@withContext
                    val existing = messages.indexOfFirst { it.id == message.id }
                    if (existing >= 0) {
                        messages[existing] = message
                    } else {
                        messages.add(insertionIndexBySentAt(messages, message.sentAt), message)
                    }
                }
                DaemonProtocol.EVENT_MESSAGES_REMOVED -> {
                    val ids = event.ids.toSet()
                    messages.removeAll { it.id in ids }
                }
                DaemonProtocol.EVENT_HISTORY_CLEARED -> messages.clear()
                DaemonProtocol.EVENT_CONNECTION -> {
                    connected = event.connected
                    _connectionStatus.value = event.status
                }
                DaemonProtocol.EVENT_MEMBERS -> {
                    memberStates.clear()
                    event.members.map { it.toAgentStatus() }.forEach { memberStates[it.memberId] = it }
                    if (selectedAgentId != null && memberStates[selectedAgentId]?.present != true) {
                        selectedAgentId = null
                    }
                }
                DaemonProtocol.EVENT_PEER_AVATAR -> {
                    val bytes = decodeAvatar(event.peerAvatar)
                    val memberId = normalizeMemberId(event.peerId, event.peerId)
                    if (bytes == null) peerAvatars.remove(memberId) else peerAvatars[memberId] = bytes
                }
                DaemonProtocol.EVENT_STATE -> {
                    if (event.currentConversationId.isNotBlank()) {
                        currentConversationId = event.currentConversationId
                    }
                    soloState = SoloState(
                        active = event.soloActive,
                        phase = event.soloPhase,
                        sessionId = event.soloSessionId,
                        targetId = event.soloTargetId,
                        targetName = event.soloTargetName,
                        startedAt = event.soloStartedAt,
                        error = event.soloError,
                    )
                }
            }
        }
    }

    private suspend fun setDaIpcStatus(value: Boolean, status: String) {
        withContext(Dispatchers.Main) {
            connected = value
            _connectionStatus.value = status
        }
    }

    private fun failPending() {
        val error = DaemonServerFrame(
            type = DaemonProtocol.TYPE_ERROR,
            ok = false,
            error = "daemon disconnected",
        )
        pending.values.forEach { it.complete(error) }
        pending.clear()
    }

    private fun decodeAvatar(value: String): ByteArray? {
        if (value.isEmpty()) return null
        return try {
            Base64.decode(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeMessage(message: ChatMessage): ChatMessage {
        val memberId = normalizeMemberId(message.sender, message.senderId)
        val senderName = if (memberId == settingsStore.memberId) {
            settingsStore.displayName
        } else {
            message.sender
        }
        return message.copy(
            sender = senderName,
            senderId = memberId,
            isMine = memberId == settingsStore.memberId,
            requesterId = when {
                message.requesterId.isBlank() -> ""
                message.requesterId == settingsStore.deviceId -> settingsStore.memberId
                canonicalMemberName(message.requesterId) ==
                    canonicalMemberName(settingsStore.displayName) -> settingsStore.memberId
                isMemberId(message.requesterId) -> message.requesterId
                else -> legacyMemberId(message.requesterId)
            },
        )
    }

    private fun normalizeMemberId(sender: String, senderId: String): String {
        val key = senderId.trim()
        return when {
            isMemberId(key) -> key
            key == settingsStore.deviceId -> settingsStore.memberId
            canonicalMemberName(sender) == canonicalMemberName(settingsStore.displayName) ->
                settingsStore.memberId
            else -> legacyMemberId(sender.ifBlank { key })
        }
    }

    override fun refreshSettings() {
        avatarBytes = settingsStore.avatarBytes
        commandScope.launch { sendReloadSettings() }
    }

    override fun selectAgent(agentId: String) {
        if (agentId.isNotBlank()) selectedAgentId = agentId
    }

    override fun selectConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        currentConversationId = conversationId
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_SELECT_CONVERSATION,
                    conversationId = conversationId,
                )
            )
        }
    }

    override fun startSolo(memberId: String, memberName: String) {
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_START_SOLO,
                    soloTargetId = memberId,
                    soloTargetName = memberName,
                )
            )
        }
    }

    override fun endSolo() {
        commandScope.launch {
            request(DaemonClientFrame(command = DaemonProtocol.COMMAND_END_SOLO))
        }
    }

    override fun findMember(name: String): AgentStatus? =
        memberStates.values.firstOrNull { member ->
            canonicalMemberName(member.memberName) == canonicalMemberName(name) ||
                member.memberId == name ||
                member.aliasList().any { alias -> canonicalMemberName(alias) == canonicalMemberName(name) }
        }

    override fun addMessage(content: String, isError: Boolean) {
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_ADD_SYSTEM_MESSAGE,
                    content = content,
                    isError = isError,
                )
            )
        }
    }

    override fun removeMessage(id: String) {
        deleteMessages(listOf(id))
    }

    override fun deleteMessages(ids: Collection<String>) {
        if (ids.isEmpty()) return
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_DELETE_MESSAGES,
                    ids = ids.toList(),
                )
            )
        }
    }

    override fun clearHistory() {
        commandScope.launch {
            request(DaemonClientFrame(command = DaemonProtocol.COMMAND_CLEAR_HISTORY))
        }
    }

    override fun saveMessageFile(message: ChatMessage, dest: PlatformFile) {
        val path = message.filePath ?: return
        scope.launch(Dispatchers.IO) {
            try {
                copyFileToPlatformFile(File(path), dest)
            } catch (_: Exception) {
            }
        }
    }

    override fun cancelOutgoing(transferId: String) {
        if (transferId.isEmpty()) return
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_CANCEL_TRANSFER,
                    transferId = transferId,
                )
            )
        }
    }

    override fun respondToApproval(
        approvalId: String,
        allow: Boolean,
        approvalMessage: String,
    ) {
        if (approvalId.isEmpty()) return
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_RESPOND_APPROVAL,
                    approvalId = approvalId,
                    allow = allow,
                    approvalMessage = approvalMessage,
                )
            )
        }
    }

    override fun sendMessage(content: String, attachments: List<OutgoingAttachment>) {
        if (content.isBlank() && attachments.isEmpty()) return
        val resolved = attachments.mapNotNull { attachment ->
            attachment.file.path?.let { it to attachment.name }
        }
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_SEND_TEXT,
                    text = content,
                    attachmentPaths = resolved.map { it.first },
                    attachmentNames = resolved.map { it.second },
                )
            )
        }
    }

    override fun sendPicture(file: PlatformFile) {
        val path = file.path ?: return
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_SEND_PICTURE,
                    path = path,
                )
            )
        }
    }

    override fun sendFile(file: PlatformFile) {
        val path = file.path ?: return
        commandScope.launch {
            request(
                DaemonClientFrame(
                    command = DaemonProtocol.COMMAND_SEND_FILE,
                    path = path,
                )
            )
        }
    }
}
