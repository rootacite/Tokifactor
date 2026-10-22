package com.acite.tokifactor.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.acite.tokifactor.copyFileToPlatformFile
import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.AvatarEnvelope
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.FileChunk
import com.acite.tokifactor.model.InboundNotice
import com.acite.tokifactor.model.PictureChunk
import com.acite.tokifactor.model.TextChunk
import com.acite.tokifactor.model.TextEnvelope
import com.acite.tokifactor.model.selectedMember
import com.acite.tokifactor.model.shouldExpireAki
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64

@SingleIn(AppScope::class)
class ChatEngine @Inject constructor(
    private val mqttService: MqttService,
    private val settingsStore: SettingsStore,
) : ChatSession {
    companion object {
        private const val CONTROL_TOPIC = "tokifactor/control"
        private const val STATUS_TIMEOUT_MS = 30_000L
        private const val IDLE_PROBE_MS = 15_000L
        private const val ROSTER_PROBE_MS = 60_000L
        private const val PROTOCOL_VERSION = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val historyReady = AtomicBoolean(false)

    override val messages: List<ChatMessage>
        field = mutableStateListOf()

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
            .sortedWith(compareBy<AgentStatus>({ it.agentId != "aki" }, { it.agentName.lowercase() }, { it.agentId }))

    override val agentStatus: AgentStatus
        get() = selectedMember(memberStates.values, selectedAgentId) ?: AgentStatus()

    override fun selectAgent(agentId: String) {
        if (agentId.isNotBlank()) selectedAgentId = agentId
    }

    override val deviceId: String
        get() = settingsStore.deviceId

    private val _connectionStatus = MutableStateFlow("Connecting…")
    override val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _inboundNotices = MutableSharedFlow<InboundNotice>(extraBufferCapacity = 32)
    override val inboundNotices: SharedFlow<InboundNotice> = _inboundNotices.asSharedFlow()

    private val _events = MutableSharedFlow<ChatEngineEvent>(
        extraBufferCapacity = 8192,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ChatEngineEvent> = _events.asSharedFlow()

    override val peerAvatars = mutableStateMapOf<String, ByteArray>()
    private val peerAvatarHash = mutableMapOf<String, String>()
    private val pendingWantAt = mutableMapOf<String, Long>()
    private val avatarRefreshRequested = ConcurrentHashMap.newKeySet<String>()
    private var lastPublishedAvatarHash: String? = null



    private class IncomingBinary(
        val hash: String,
        val total: Int,
        val sender: String,
        val senderId: String,
        val fileName: String?,
        val fileSize: Long,
        val sentAt: Long,
        val received: BooleanArray,
        val isChunkedText: Boolean = false,
        var caption: String = "",
        var count: Int = 0,
    )

    private val incoming = mutableMapOf<String, IncomingBinary>()
    private val incomingWatchdogs = mutableMapOf<String, Job>()
    private val abortedTransfers = mutableSetOf<String>()
    private val completedTransfers = ConcurrentHashMap.newKeySet<String>()
    private val outgoingJobs = mutableMapOf<String, Job>()

    private var mqttSessionJob: Job? = null
    private var sessionChannelKey: String? = null
    private val sessionGeneration = AtomicInteger(0)
    private var akiControlJob: Job? = null
    @Volatile private var controlReady = false
    @Volatile private var lastProbeSentAt = 0L
    private var persistJob: Job? = null
    private val persistGeneration = AtomicInteger(0)

    override fun start() {
        if (!started.compareAndSet(false, true)) {
            if (historyReady.get() && mqttSessionJob?.isActive != true) startMqttSession()
            return
        }
        scope.launch {
            val cachedAvatars = try {
                PeerAvatarStore.load()
            } catch (_: Exception) {
                emptyMap()
            }
            val loaded = try {
                ChatHistoryStore.load()
            } catch (_: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                for ((id, entry) in cachedAvatars) {
                    peerAvatars[id] = entry.bytes
                    peerAvatarHash[id] = entry.hash
                    emitEvent(ChatEngineEvent.PeerAvatarChanged(id, entry.bytes))
                }
                if (loaded.isNotEmpty()) {
                    val existing = messages.map { it.id }.toHashSet()
                    val restored = loaded.filter { it.id !in existing }
                    if (restored.isNotEmpty()) {
                        messages.addAll(0, restored)
                        restored.forEach { emitEvent(ChatEngineEvent.MessageUpsert(it)) }
                    }
                }
                if (messages.isEmpty()) {
                    addMessage("Welcome to TokiFactor.")
                }
            }
            historyReady.set(true)
            startMqttSession()
        }
    }

    override fun refreshSettings() {
        avatarBytes = settingsStore.avatarBytes
        if (channelKey() != sessionChannelKey) {
            startMqttSession()
        } else if (connected) {
            scope.launch {
                publishAkiProbe()
                publishAvatarHave()
            }
        }
    }

    private fun channelKey(): String =
        settingsStore.mqttBroker.connectionKey() + "\u0000" + settingsStore.magicString

    private fun setConnected(value: Boolean, status: String) {
        connected = value
        _connectionStatus.value = status
        emitEvent(ChatEngineEvent.ConnectionChanged(value, status))
    }

    private fun emitEvent(event: ChatEngineEvent) {
        _events.tryEmit(event)
    }

    private fun startMqttSession() {
        val generation = sessionGeneration.incrementAndGet()
        sessionChannelKey = channelKey()
        avatarRefreshRequested.clear()
        mqttSessionJob?.cancel()
        controlReady = false
        stopAkiControlLoop(clearIdentity = true)
        mqttSessionJob = scope.launch {
            var backoffMs = 1_000L
            var announcedFailure = false
            while (isActive && generation == sessionGeneration.get()) {
                val broker = settingsStore.mqttBroker
                sessionChannelKey = channelKey()
                withContext(Dispatchers.Main) {
                    setConnected(false, if (announcedFailure) "Reconnecting…" else "Connecting…")
                }
                try {
                    mqttService.disconnect()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                val s = try {
                    mqttService.connect(broker)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                if (!isActive) {
                    try {
                        mqttService.disconnect()
                    } catch (_: Exception) {
                    }
                    return@launch
                }
                if (!s) {
                    withContext(Dispatchers.Main) {
                        setConnected(false, "Reconnecting…")
                        if (!announcedFailure) {
                            announcedFailure = true
                            addMessage("Connection failed: ${broker.name} (${broker.endpoint})", true)
                        }
                    }
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                    continue
                }
                backoffMs = 1_000L
                announcedFailure = false
                withContext(Dispatchers.Main) {
                    setConnected(
                        true,
                        "Connected to ${broker.name}",
                    )
                    addMessage(
                        "Connected to ${broker.name} (${broker.endpoint} ${broker.transportLabel}), maxSize: ${mqttService.getMaximumPacketSize()}"
                    )
                }

                try {
                    coroutineScope {
                        launch {
                            mqttService.connected.collect { live ->
                                val label = if (live) {
                                    "Connected to ${broker.name}"
                                } else {
                                    "Reconnecting…"
                                }
                                withContext(Dispatchers.Main) {
                                    setConnected(live, label)
                                    if (generation == sessionGeneration.get()) {
                                        if (!live) {
                                            stopAkiControlLoop(clearIdentity = false)
                                        } else if (controlReady &&
                                            akiControlJob?.isActive != true
                                        ) {
                                            startAkiControlLoop()
                                        }
                                    }
                                }
                            }
                        }
                launch {
                    mqttService.subscribe("tokifactor/text")
                        .collect { message ->
                            try {
                                if (message.payload.trimStart().startsWith("{")) {
                                    val chunk = TextChunk.decode(message.payload) ?: return@collect
                                    handleIncomingText(chunk, message.timestamp)
                                } else {
                                    withContext(Dispatchers.Main) {
                                        try {
                                            val chip = Base64.decode(message.payload)
                                            val clear = SecureMessageChannel.decrypt(chip, settingsStore.magicString).decodeToString()
                                            val envelope = TextEnvelope.decode(clear)
                                            if (envelope != null) {
                                                applyIncomingEnvelope(envelope, message.timestamp)
                                                notePeerAvatar(envelope.senderId, envelope.avatarHash)
                                            } else {
                                                addChatText(clear, sender = "Unknown", isMine = false)
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                }
                launch {
                    mqttService.subscribe("tokifactor/picture")
                        .collect { message ->
                            try {
                                val chunk = PictureChunk.decode(message.payload) ?: return@collect
                                handleIncomingPicture(chunk, message.timestamp)
                            } catch (_: Exception) {}
                        }
                }
                launch {
                    mqttService.subscribe("tokifactor/file")
                        .collect { message ->
                            try {
                                val chunk = FileChunk.decode(message.payload) ?: return@collect
                                handleIncomingFile(chunk, message.timestamp)
                            } catch (_: Exception) {}
                        }
                }
                launch {
                    mqttService.subscribe("tokifactor/avatar", MqttDeliveryQos.AtMostOnce)
                        .collect { message ->
                            try {
                                handleAvatarEnvelope(message.payload)
                            } catch (_: Exception) {}
                        }
                }
                launch {
                    mqttService.subscribe(CONTROL_TOPIC, MqttDeliveryQos.AtMostOnce)
                        .collect { message ->
                            try {
                                val envelope = decodeEnvelope(message.payload)
                                    ?: return@collect
                                withContext(Dispatchers.Main) {
                                    applyControlEnvelope(envelope)
                                }
                            } catch (_: Exception) {
                            }
                        }
                }
                        launch {
                            publishAvatarHave(force = true)
                            requestKnownPeerAvatars()
                        }
                        delay(150)
                        controlReady = true
                        startAkiControlLoop()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                if (generation == sessionGeneration.get()) {
                    controlReady = false
                    stopAkiControlLoop(clearIdentity = false)
                }
                if (!isActive) break
                withContext(Dispatchers.Main) {
                    setConnected(false, "Reconnecting…")
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun decodeEnvelope(payload: String): TextEnvelope? {
        val cipher = try {
            Base64.decode(payload)
        } catch (_: Exception) {
            return null
        }
        val clear = try {
            SecureMessageChannel.decrypt(cipher, settingsStore.magicString).decodeToString()
        } catch (_: Exception) {
            return null
        }
        return TextEnvelope.decode(clear)
    }

    private fun parseBoolean(value: String?): Boolean? = when (value?.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }

    private fun startAkiControlLoop() {
        akiControlJob?.cancel()
        lastProbeSentAt = 0L
        akiControlJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val expired = memberStates.filterValues {
                    shouldExpireAki(it.present, it.lastSeenAt, now, STATUS_TIMEOUT_MS)
                }
                if (expired.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        for (id in expired.keys) {
                            val current = memberStates[id] ?: continue
                            memberStates[id] = current.copy(present = false)
                        }
                        if (selectedAgentId != null && memberStates[selectedAgentId]?.present != true) {
                            selectedAgentId = null
                        }
                        emitMembersChanged()
                    }
                }
                val hasMembers = memberStates.values.any { it.present }
                val interval = if (hasMembers) ROSTER_PROBE_MS else IDLE_PROBE_MS
                if (now - lastProbeSentAt >= interval) {
                    publishAkiProbe()
                }
                delay(500L)
            }
        }
    }

    private fun stopAkiControlLoop(clearIdentity: Boolean) {
        akiControlJob?.cancel()
        akiControlJob = null
        lastProbeSentAt = 0L
        scope.launch(Dispatchers.Main) {
            memberStates.clear()
            if (clearIdentity) selectedAgentId = null
            emitMembersChanged()
        }
    }

    private fun emitMembersChanged() {
        emitEvent(ChatEngineEvent.MembersChanged(members))
    }

    private suspend fun publishAkiProbe() {
        if (!mqttService.isConnected()) return
        lastProbeSentAt = System.currentTimeMillis()
        val probeId = UUID.randomUUID().toString()
        val envelope = TextEnvelope(
            sender = settingsStore.displayName,
            text = "Aki probe",
            senderId = settingsStore.deviceId,
            avatarHash = ownAvatarHash(),
            metadata = mapOf(
                "schema" to "akid.v1",
                "kind" to "probe",
                "protocolVersion" to PROTOCOL_VERSION.toString(),
                "probeId" to probeId,
            ),
        )
        val payload = try {
            Base64.encode(
                SecureMessageChannel.encrypt(
                    envelope.encode().encodeToByteArray(),
                    settingsStore.magicString,
                )
            )
        } catch (_: Exception) {
            return
        }
        try {
            mqttService.publish(CONTROL_TOPIC, payload, MqttDeliveryQos.AtMostOnce)
        } catch (_: Exception) {
        }
    }

    private fun applyControlEnvelope(envelope: TextEnvelope) {
        val meta = envelope.metadata
        if (meta["schema"] != "akid.v1") return
        val kind = meta["kind"].orEmpty()
        if (kind != "presence" && kind != "status" && kind != "offline") return
        if (meta["protocolVersion"]?.toIntOrNull() != PROTOCOL_VERSION) return
        val senderId = envelope.senderId
        if (senderId.isNotEmpty() && senderId == settingsStore.deviceId) return
        val agentId = meta["agentId"].orEmpty()
            .ifBlank { meta["agent"].orEmpty() }
            .ifBlank { senderId }
        if (agentId.isBlank() || agentId == settingsStore.deviceId) return
        if (kind == "offline") {
            memberStates.remove(agentId)
            avatarRefreshRequested.remove(agentId)
            if (selectedAgentId == agentId) selectedAgentId = null
            emitMembersChanged()
            return
        }
        val now = System.currentTimeMillis()
        val firstSeen = memberStates[agentId] == null
        val old = memberStates[agentId] ?: AgentStatus(agentId = agentId)
        memberStates[agentId] = old.copy(
            present = true,
            lastSeenAt = now,
            agentId = agentId,
            agentName = meta["agentName"].orEmpty().ifBlank {
                envelope.sender.ifBlank { old.agentName.ifBlank { agentId } }
            },
            description = meta["description"].orEmpty().take(300),
            aliases = meta["aliases"].orEmpty().take(512),
            model = meta["model"] ?: old.model,
            reasoning = meta["reasoning"] ?: old.reasoning,
            contextLimit = meta["contextLimit"]?.toIntOrNull() ?: old.contextLimit,
            contextTokens = meta["contextTokens"]?.toIntOrNull() ?: old.contextTokens,
            backendReady = parseBoolean(meta["backendReady"]) ?: old.backendReady,
            channelReady = parseBoolean(meta["channelReady"]) ?: old.channelReady,
            sandboxReady = parseBoolean(meta["sandboxReady"]) ?: old.sandboxReady,
            queueDepth = meta["queueDepth"]?.toIntOrNull() ?: old.queueDepth,
            activeTurn = parseBoolean(meta["activeTurn"]) ?: old.activeTurn,
            pendingApprovals = meta["pendingApprovals"]?.toIntOrNull()
                ?: old.pendingApprovals,
            uptimeS = meta["uptimeS"]?.toLongOrNull() ?: old.uptimeS,
            memoryTokens = meta["memoryTokens"]?.toIntOrNull() ?: old.memoryTokens,
        )
        if (selectedAgentId != null && memberStates[selectedAgentId]?.present != true) {
            selectedAgentId = null
        }
        if (firstSeen) {
            schedulePeerAvatarRefresh(agentId)
        }
        emitMembersChanged()
    }

    override fun addMessage(content: String, isError: Boolean) {
        insertMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                content = content,
                pic = null,
                isError = isError,
                isSystem = true,
                sentAt = System.currentTimeMillis(),
            )
        )
        schedulePersist()
    }

    private fun addChatText(
        content: String,
        sender: String,
        isMine: Boolean,
        senderId: String = "",
        id: String = UUID.randomUUID().toString(),
        sentAt: Long = System.currentTimeMillis(),
    ) {
        if (messages.any { it.id == id }) return
        val message = ChatMessage(
            id = id,
            content = content,
            pic = null,
            sender = sender,
            senderId = senderId,
            isMine = isMine,
            sentAt = sentAt,
        )
        insertMessage(message)
        emitNotice(message)
        schedulePersist()
    }

    private fun applyIncomingEnvelope(
        envelope: TextEnvelope,
        receivedAt: Long = System.currentTimeMillis(),
    ) {
        val eventAt = envelope.sentAt.takeIf { it > 0L } ?: receivedAt
        val meta = envelope.metadata
        if (meta["schema"] != "akid.v1") {
            addChatText(
                envelope.text,
                sender = envelope.sender,
                senderId = envelope.senderId,
                isMine = false,
                id = envelope.messageId.ifBlank { UUID.randomUUID().toString() },
                sentAt = eventAt,
            )
            return
        }
        val kind = meta["kind"].orEmpty()
        val turnId = meta["turnId"].orEmpty()
        val seq = meta["seq"]?.toIntOrNull() ?: 0
        when (kind) {
            "turn" -> applyAgentTurn(envelope, turnId, seq, eventAt)
            "tool" -> applyAgentTool(envelope, turnId, seq, eventAt)
            "approval" -> applyAgentApproval(envelope, turnId, seq, eventAt)
            "approval_resolved" -> applyAgentApprovalResolved(envelope, turnId, seq, eventAt)
            "error" -> applyAgentError(envelope, turnId, seq, eventAt)
        }
    }

    private fun applyAgentTurn(
        envelope: TextEnvelope,
        turnId: String,
        seq: Int,
        sentAt: Long,
    ) {
        if (turnId.isEmpty()) return
        val phase = envelope.metadata["phase"].orEmpty()
        val segment = envelope.metadata["segment"]?.toIntOrNull() ?: 0
        when (phase) {
            "started" -> upsertAgent(
                ChatMessage(
                    id = "$turnId:status",
                    content = "",
                    pic = null,
                    sender = envelope.sender,
                    senderId = envelope.senderId,
                    agentKind = "turn",
                    turnId = turnId,
                    segment = segment,
                    seq = seq,
                    phase = phase,
                    status = "running",
                    sentAt = sentAt,
                )
            )
            "progress", "final" -> {
                if (envelope.text.isBlank()) return
                upsertAgent(
                    ChatMessage(
                        id = "$turnId:segment:$segment",
                        content = envelope.text,
                        pic = null,
                        sender = envelope.sender,
                        senderId = envelope.senderId,
                        agentKind = "turn",
                        turnId = turnId,
                        segment = segment,
                        seq = seq,
                        phase = phase,
                        markdown = true,
                        status = if (phase == "final") "complete" else "running",
                        sentAt = sentAt,
                    )
                )
                if (phase == "final") schedulePersist()
            }
            "ended" -> {
                val statusId = "$turnId:status"
                if (messages.removeAll { it.id == statusId }) {
                    emitEvent(ChatEngineEvent.MessagesRemoved(setOf(statusId)))
                }
                for (index in messages.indices) {
                    val message = messages[index]
                    if (message.turnId != turnId || !message.isAgent) continue
                    if (message.phase == "progress" || message.status == "running") {
                        messages[index] = message.copy(phase = "completed", status = "complete")
                        emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
                    }
                }
                schedulePersist()
            }
            "no_reply" -> {
                val removed = messages.filter { it.turnId == turnId }.map { it.id }.toSet()
                messages.removeAll { it.turnId == turnId }
                if (removed.isNotEmpty()) emitEvent(ChatEngineEvent.MessagesRemoved(removed))
                schedulePersist()
            }
        }
    }

    private fun applyAgentTool(
        envelope: TextEnvelope,
        turnId: String,
        seq: Int,
        sentAt: Long,
    ) {
        if (turnId.isEmpty()) return
        val toolCallId = envelope.metadata["toolCallId"].orEmpty()
        if (toolCallId.isEmpty()) return
        val status = envelope.metadata["toolStatus"].orEmpty().ifBlank { "running" }
        upsertAgent(
            ChatMessage(
                id = "$turnId:tool:$toolCallId",
                content = envelope.text,
                pic = null,
                sender = envelope.sender,
                senderId = envelope.senderId,
                agentKind = "tool",
                turnId = turnId,
                seq = seq,
                phase = if (status == "running") "running" else "completed",
                toolCallId = toolCallId,
                toolName = envelope.metadata["toolName"].orEmpty(),
                status = status,
                sentAt = sentAt,
            )
        )
        if (status != "running") schedulePersist()
    }

    private fun applyAgentApproval(
        envelope: TextEnvelope,
        turnId: String,
        seq: Int,
        sentAt: Long,
    ) {
        val approvalId = envelope.metadata["approvalId"].orEmpty()
        if (approvalId.isEmpty()) return
        upsertAgent(
            ChatMessage(
                id = "approval:$approvalId",
                content = envelope.text,
                pic = null,
                sender = envelope.sender,
                senderId = envelope.senderId,
                agentKind = "approval",
                turnId = turnId,
                seq = seq,
                phase = "pending",
                toolCallId = envelope.metadata["toolCallId"].orEmpty(),
                toolName = envelope.metadata["toolName"].orEmpty(),
                approvalId = approvalId,
                requesterId = envelope.metadata["requesterId"].orEmpty(),
                timeoutS = envelope.metadata["timeoutS"]?.toIntOrNull() ?: 0,
                details = envelope.metadata["details"].orEmpty(),
                status = "pending",
                sentAt = sentAt,
            )
        )
    }

    private fun applyAgentApprovalResolved(
        envelope: TextEnvelope,
        turnId: String,
        seq: Int,
        sentAt: Long,
    ) {
        val approvalId = envelope.metadata["approvalId"].orEmpty()
        if (approvalId.isEmpty()) return
        val decision = envelope.metadata["decision"].orEmpty().ifBlank { "deny" }
        val id = "approval:$approvalId"
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) {
            upsertAgent(
                ChatMessage(
                    id = id,
                    content = envelope.text,
                    pic = null,
                    sender = envelope.sender,
                    senderId = envelope.senderId,
                    agentKind = "approval",
                    turnId = turnId,
                    seq = seq,
                    phase = "resolved",
                    toolName = envelope.metadata["toolName"].orEmpty(),
                    approvalId = approvalId,
                    details = envelope.metadata["details"].orEmpty(),
                    status = decision,
                    sentAt = sentAt,
                )
            )
        } else {
            val existing = messages[index]
            if (seq >= existing.seq) {
                messages[index] = existing.copy(
                    seq = seq,
                    phase = "resolved",
                    status = decision,
                )
                emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
            }
        }
        schedulePersist()
    }

    private fun applyAgentError(
        envelope: TextEnvelope,
        turnId: String,
        seq: Int,
        sentAt: Long,
    ) {
        if (turnId.isEmpty()) return
        upsertAgent(
            ChatMessage(
                id = "$turnId:error:$seq",
                content = envelope.text,
                pic = null,
                isError = true,
                sender = envelope.sender,
                senderId = envelope.senderId,
                agentKind = "error",
                turnId = turnId,
                seq = seq,
                phase = "ended",
                status = "error",
                sentAt = sentAt,
            )
        )
        schedulePersist()
    }

    private fun upsertAgent(message: ChatMessage) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) {
            insertMessage(message)
        } else if (message.seq >= messages[index].seq) {
            val existing = messages[index]
            messages[index] = message.copy(
                sentAt = existing.sentAt.takeIf { it > 0L } ?: message.sentAt,
            )
            emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
        }
    }

    private fun emitNotice(message: ChatMessage) {
        if (message.isMine || message.isSystem || message.isError || message.isAgent) return
        val preview = when {
            message.isFile -> message.fileName ?: "File"
            message.isImage -> "Photo"
            else -> message.content.trim().replace(Regex("\\s+"), " ").take(80).ifBlank { "Message" }
        }
        _inboundNotices.tryEmit(
            InboundNotice(
                id = message.id,
                senderId = message.senderId,
                sender = message.sender.ifBlank { "Unknown" },
                preview = preview,
            )
        )
    }

    private fun insertMessage(message: ChatMessage) {
        val stamped = if (message.sentAt > 0L) message else message.copy(sentAt = System.currentTimeMillis())
        messages.add(insertionIndexBySentAt(messages, stamped.sentAt), stamped)
        emitEvent(ChatEngineEvent.MessageUpsert(stamped))
    }

    override fun removeMessage(id: String) {
        deleteMessages(listOf(id))
    }

    override fun deleteMessages(ids: Collection<String>) {
        val set = ids.toSet()
        if (set.isEmpty()) return
        for (id in set) {
            outgoingJobs.remove(id)?.cancel()
            cancelIncomingWatchdog(id)
            incoming.remove(id)
            abortedTransfers.add(id)
            ChunkedTransfer.deleteTransfer(id)
            ChatHistoryStore.deleteBlob(id)
        }
        messages.removeAll { it.id in set }
        emitEvent(ChatEngineEvent.MessagesRemoved(set))
        schedulePersist()
    }

    override fun clearHistory() {
        persistGeneration.incrementAndGet()
        persistJob?.cancel()
        persistJob = null
        outgoingJobs.values.forEach { it.cancel() }
        outgoingJobs.clear()
        incomingWatchdogs.values.forEach { it.cancel() }
        incomingWatchdogs.clear()
        incoming.clear()
        abortedTransfers.clear()
        messages.clear()
        try {
            ChunkedTransfer.transfersRoot().deleteRecursively()
        } catch (_: Exception) {
        }
        try {
            ChatHistoryStore.clear()
        } catch (_: Exception) {
        }
        emitEvent(ChatEngineEvent.HistoryCleared)
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
        outgoingJobs[transferId]?.cancel()
    }

    override fun respondToApproval(approvalId: String, allow: Boolean) {
        if (!connected || approvalId.isEmpty()) return
        val id = "approval:$approvalId"
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val message = messages[index]
        if (message.requesterId.isNotEmpty() && message.requesterId != settingsStore.deviceId) return
        if (message.status != "pending") return
        val pendingStatus = if (allow) "allow_pending" else "deny_pending"
        messages[index] = message.copy(status = pendingStatus)
        emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
        val command = if (allow) "akid-approve $approvalId" else "akid-deny $approvalId"
        val sentAt = System.currentTimeMillis()
        val envelope = TextEnvelope(
            sender = settingsStore.displayName,
            text = command,
            senderId = settingsStore.deviceId,
            avatarHash = ownAvatarHash(),
            messageId = TextEnvelope.messageId(settingsStore.deviceId, sentAt, command),
            sentAt = sentAt,
        ).encode()
        val payload = Base64.encode(
            SecureMessageChannel.encrypt(
                envelope.encodeToByteArray(),
                settingsStore.magicString,
            )
        )
        scope.launch(Dispatchers.IO) {
            try {
                mqttService.publish("tokifactor/text", payload)
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    val current = messages.indexOfFirst { it.id == id }
                    if (current >= 0 && messages[current].status == pendingStatus) {
                        messages[current] = messages[current].copy(status = "pending")
                        emitEvent(ChatEngineEvent.MessageUpsert(messages[current]))
                    }
                }
            }
        }
    }

    override fun sendMessage(content: String) {
        if (!connected) return
        val utf8 = content.encodeToByteArray()
        if (utf8.size > ChunkedTransfer.TEXT_CHUNK_THRESHOLD_BYTES) {
            sendChunkedText(content, utf8)
            return
        }
        val sentAt = System.currentTimeMillis()
        val messageId = TextEnvelope.messageId(settingsStore.deviceId, sentAt, content)
        val envelope = TextEnvelope(
            sender = settingsStore.displayName,
            text = content,
            senderId = settingsStore.deviceId,
            avatarHash = ownAvatarHash(),
            messageId = messageId,
            sentAt = sentAt,
        ).encode()
        val c = SecureMessageChannel.encrypt(envelope.encodeToByteArray(), settingsStore.magicString)
        val msg = Base64.encode(c)
        val size = mqttService.getMaximumPacketSize()
        if (size != null && msg.encodeToByteArray().size > size) {
            addMessage("Error : ${msg.encodeToByteArray().size} is too large.", true)
            return
        }
        addChatText(
            content,
            sender = settingsStore.displayName,
            senderId = settingsStore.deviceId,
            isMine = true,
            id = messageId,
            sentAt = sentAt,
        )
        scope.launch(Dispatchers.IO) {
            try {
                mqttService.publish("tokifactor/text", msg)
            } catch (_: Exception) {
            }
        }
    }

    private fun sendChunkedText(content: String, utf8: ByteArray) {
        val senderName = settingsStore.displayName
        val senderId = settingsStore.deviceId
        val avatarHash = ownAvatarHash()
        val sentAt = System.currentTimeMillis()
        val transferId = TextEnvelope.messageId(senderId, sentAt, content)
        val job = scope.launch(Dispatchers.IO) {
            try {
                val payloadFile = ChunkedTransfer.payloadFile(transferId)
                val (fileSize, hash) = ChunkedTransfer.importBytes(utf8, payloadFile)
                val chunkSize = ChunkedTransfer.chunkSize(mqttService.getMaximumPacketSize())
                val total = ChunkedTransfer.chunkCount(fileSize, chunkSize)
                val maxPacket = mqttService.getMaximumPacketSize()

                withContext(Dispatchers.Main) {
                    insertMessage(
                        ChatMessage(
                            id = transferId,
                            content = content,
                            pic = null,
                            sender = senderName,
                            senderId = senderId,
                            isMine = true,
                            chunksReceived = 0,
                            chunksTotal = total,
                            fileSize = fileSize,
                            isChunkedText = true,
                            sentAt = sentAt,
                        )
                    )
                }

                publishSlices(
                    transferId = transferId,
                    payloadFile = payloadFile,
                    fileSize = fileSize,
                    chunkSize = chunkSize,
                    total = total,
                    maxPacket = maxPacket,
                    topic = "tokifactor/text",
                ) { index, encryptedB64 ->
                    TextChunk(
                        transferId = transferId,
                        index = index,
                        total = total,
                        hash = hash,
                        encryptedData = encryptedB64,
                        sender = senderName,
                        senderId = senderId,
                        avatarHash = avatarHash,
                        messageId = transferId,
                        sentAt = sentAt,
                        size = fileSize,
                    ).encode()
                }
                withContext(Dispatchers.Main) {
                    updateMessage(transferId) {
                        it.copy(chunksReceived = 0, chunksTotal = 0, isChunkedText = false)
                    }
                }
                ChunkedTransfer.deleteTransfer(transferId)
                schedulePersist()
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    publishAbort(TransferKind.Text, transferId, senderName, senderId, "cancel")
                    ChunkedTransfer.deleteTransfer(transferId)
                    withContext(Dispatchers.Main) {
                        replaceMessage(
                            transferId,
                            ChatMessage(
                                id = transferId,
                                content = "Cancelled sending text.",
                                pic = null,
                                isSystem = true,
                            )
                        )
                    }
                }
                throw e
            } catch (t: Throwable) {
                publishAbort(TransferKind.Text, transferId, senderName, senderId, "fail")
                ChunkedTransfer.deleteTransfer(transferId)
                val tooLarge = t.message?.removePrefix("too-large:")?.toIntOrNull()
                withContext(Dispatchers.Main) {
                    replaceMessage(
                        transferId,
                        ChatMessage(
                            id = transferId,
                            content = when {
                                tooLarge != null -> "Error : $tooLarge is too large."
                                t is OutOfMemoryError -> "Error : not enough memory to send text."
                                else -> "Error : failed to send text."
                            },
                            pic = null,
                            isError = true,
                            isSystem = true,
                        )
                    )
                }
            } finally {
                outgoingJobs.remove(transferId)
            }
        }
        outgoingJobs[transferId] = job
    }

    override fun sendPicture(file: PlatformFile) {
        sendBinary(
            file = file,
            asFile = false,
            failLabel = "image",
        )
    }

    override fun sendFile(file: PlatformFile) {
        sendBinary(
            file = file,
            asFile = true,
            failLabel = "file",
        )
    }

    private fun sendBinary(file: PlatformFile, asFile: Boolean, failLabel: String) {
        if (!connected) return
        val senderName = settingsStore.displayName
        val senderId = settingsStore.deviceId
        val avatarHash = ownAvatarHash()
        var sendAsFile = asFile
        var sendTopic = if (asFile) "tokifactor/file" else "tokifactor/picture"
        val sentAt = System.currentTimeMillis()
        val transferId = UUID.randomUUID().toString()
        val job = scope.launch(Dispatchers.IO) {
            try {
                val payloadFile = ChunkedTransfer.payloadFile(transferId)
                val (fileSize, hash) = ChunkedTransfer.importSource(file, payloadFile)
                val sourceName = FileChunk.sanitizeFileName(file.name)
                val uiAsFile = asFile || fileSize > ChunkedTransfer.PREVIEW_MAX_BYTES
                val fileName = if (uiAsFile) sourceName else null
                val preview = if (!uiAsFile) {
                    payloadFile.readBytes()
                } else {
                    null
                }
                sendAsFile = uiAsFile
                sendTopic = if (sendAsFile) "tokifactor/file" else "tokifactor/picture"
                val chunkSize = ChunkedTransfer.chunkSize(mqttService.getMaximumPacketSize())
                val total = ChunkedTransfer.chunkCount(fileSize, chunkSize)
                val maxPacket = mqttService.getMaximumPacketSize()

                withContext(Dispatchers.Main) {
                    insertMessage(
                        ChatMessage(
                            id = transferId,
                            content = fileName ?: "",
                            pic = preview,
                            sender = senderName,
                            senderId = senderId,
                            isMine = true,
                            chunksReceived = 0,
                            chunksTotal = total,
                            fileName = fileName,
                            filePath = payloadFile.absolutePath,
                            fileSize = fileSize,
                            sentAt = sentAt,
                        )
                    )
                }

                publishSlices(
                    transferId = transferId,
                    payloadFile = payloadFile,
                    fileSize = fileSize,
                    chunkSize = chunkSize,
                    total = total,
                    maxPacket = maxPacket,
                    topic = sendTopic,
                ) { index, encryptedB64 ->
                    if (sendAsFile) {
                        FileChunk(
                            transferId = transferId,
                            index = index,
                            total = total,
                            hash = hash,
                            encryptedData = encryptedB64,
                            name = fileName ?: sourceName,
                            sender = senderName,
                            senderId = senderId,
                            avatarHash = avatarHash,
                            sentAt = sentAt,
                            size = fileSize,
                        ).encode()
                    } else {
                        PictureChunk(
                            transferId = transferId,
                            index = index,
                            total = total,
                            hash = hash,
                            encryptedData = encryptedB64,
                            sender = senderName,
                            senderId = senderId,
                            avatarHash = avatarHash,
                            sentAt = sentAt,
                            size = fileSize,
                        ).encode()
                    }
                }
                val blobPath = ChatHistoryStore.storeBlobFromFile(transferId, payloadFile)
                ChunkedTransfer.deleteTransfer(transferId)
                withContext(Dispatchers.Main) {
                    updateMessage(transferId) {
                        it.copy(
                            chunksReceived = 0,
                            chunksTotal = 0,
                            filePath = blobPath,
                        )
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    publishAbort(
                        if (sendAsFile) TransferKind.File else TransferKind.Image,
                        transferId,
                        senderName,
                        senderId,
                        "cancel",
                    )
                    ChunkedTransfer.deleteTransfer(transferId)
                    withContext(Dispatchers.Main) {
                        replaceMessage(
                            transferId,
                            ChatMessage(
                                id = transferId,
                                content = "Cancelled sending $failLabel.",
                                pic = null,
                                isSystem = true,
                            )
                        )
                    }
                }
                throw e
            } catch (t: Throwable) {
                publishAbort(
                    if (sendAsFile) TransferKind.File else TransferKind.Image,
                    transferId,
                    senderName,
                    senderId,
                    "fail",
                )
                ChunkedTransfer.deleteTransfer(transferId)
                val tooLarge = t.message?.removePrefix("too-large:")?.toIntOrNull()
                withContext(Dispatchers.Main) {
                    replaceMessage(
                        transferId,
                        ChatMessage(
                            id = transferId,
                            content = when {
                                tooLarge != null -> "Error : $tooLarge is too large."
                                t is OutOfMemoryError -> "Error : not enough memory to send $failLabel."
                                else -> "Error : failed to send $failLabel."
                            },
                            pic = null,
                            isError = true,
                            isSystem = true,
                        )
                    )
                }
            } finally {
                outgoingJobs.remove(transferId)
            }
        }
        outgoingJobs[transferId] = job
    }

    private suspend fun publishSlices(
        transferId: String,
        payloadFile: File,
        fileSize: Long,
        chunkSize: Int,
        total: Int,
        maxPacket: Int?,
        topic: String,
        encodeChunk: (index: Int, encryptedB64: String) -> String,
    ) {
        val completed = AtomicInteger(0)
        val queue = Channel<Int>(Channel.UNLIMITED)
        for (index in 0 until total) {
            queue.send(index)
        }
        queue.close()
        coroutineScope {
            repeat(ChunkedTransfer.MAX_IN_FLIGHT_CHUNKS) {
                launch {
                    for (index in queue) {
                        val plain = ChunkedTransfer.readSlice(payloadFile, index, chunkSize, fileSize)
                        val cipher = SecureMessageChannel.encrypt(plain, settingsStore.magicString)
                        val payload = encodeChunk(index, Base64.encode(cipher))
                        val wireSize = payload.encodeToByteArray().size
                        if (maxPacket != null && wireSize > maxPacket) {
                            error("too-large:$wireSize")
                        }
                        mqttService.publish(topic, payload)
                        val n = completed.incrementAndGet()
                        withContext(Dispatchers.Main) {
                            updateMessage(transferId) {
                                it.copy(chunksReceived = n, chunksTotal = total)
                            }
                        }
                    }
                }
            }
        }
    }

    private enum class TransferKind { Image, File, Text }

    private suspend fun publishAbort(
        kind: TransferKind,
        transferId: String,
        sender: String,
        senderId: String,
        reason: String,
    ) {
        val payload = when (kind) {
            TransferKind.File -> FileChunk.abort(transferId, sender, senderId, reason).encode()
            TransferKind.Image -> PictureChunk.abort(transferId, sender, senderId, reason).encode()
            TransferKind.Text -> TextChunk.abort(transferId, sender, senderId, reason).encode()
        }
        val topics = when (kind) {
            TransferKind.Text -> listOf("tokifactor/text")
            else -> listOf("tokifactor/file", "tokifactor/picture")
        }
        for (topic in topics) {
            try {
                mqttService.publish(topic, payload)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun handleIncomingPicture(chunk: PictureChunk, receivedAt: Long) {
        handleIncomingBinary(
            transferId = chunk.transferId,
            index = chunk.index,
            total = chunk.total,
            hash = chunk.hash,
            encryptedData = chunk.encryptedData,
            sender = chunk.sender,
            senderId = chunk.senderId,
            avatarHash = chunk.avatarHash,
            abort = chunk.abort,
            reason = chunk.reason,
            fileName = null,
            fileSize = chunk.size,
            mismatchLabel = "image",
            isChunkedText = false,
            sentAt = chunk.sentAt,
            receivedAt = receivedAt,
            caption = decryptPictureCaption(chunk.captionEncrypted),
        )
    }

    private fun decryptPictureCaption(encrypted: String): String {
        if (encrypted.isBlank()) return ""
        return try {
            SecureMessageChannel.decrypt(Base64.decode(encrypted), settingsStore.magicString)
                .decodeToString(throwOnInvalidSequence = false)
                .take(2000)
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun handleIncomingFile(chunk: FileChunk, receivedAt: Long) {
        handleIncomingBinary(
            transferId = chunk.transferId,
            index = chunk.index,
            total = chunk.total,
            hash = chunk.hash,
            encryptedData = chunk.encryptedData,
            sender = chunk.sender,
            senderId = chunk.senderId,
            avatarHash = chunk.avatarHash,
            abort = chunk.abort,
            reason = chunk.reason,
            fileName = chunk.name,
            fileSize = chunk.size,
            mismatchLabel = "file",
            isChunkedText = false,
            sentAt = chunk.sentAt,
            receivedAt = receivedAt,
        )
    }

    private suspend fun handleIncomingText(chunk: TextChunk, receivedAt: Long) {
        handleIncomingBinary(
            transferId = chunk.transferId,
            index = chunk.index,
            total = chunk.total,
            hash = chunk.hash,
            encryptedData = chunk.encryptedData,
            sender = chunk.sender,
            senderId = chunk.senderId,
            avatarHash = chunk.avatarHash,
            abort = chunk.abort,
            reason = chunk.reason,
            fileName = null,
            fileSize = chunk.size,
            mismatchLabel = "text",
            isChunkedText = true,
            messageId = chunk.messageId,
            sentAt = chunk.sentAt,
            receivedAt = receivedAt,
            metadata = chunk.metadata,
        )
    }

    private suspend fun handleIncomingBinary(
        transferId: String,
        index: Int,
        total: Int,
        hash: String,
        encryptedData: String,
        sender: String,
        senderId: String,
        avatarHash: String,
        abort: Boolean,
        reason: String,
        fileName: String?,
        fileSize: Long,
        mismatchLabel: String,
        isChunkedText: Boolean,
        messageId: String = "",
        sentAt: Long = 0L,
        receivedAt: Long = System.currentTimeMillis(),
        caption: String = "",
        metadata: Map<String, String> = emptyMap(),
    ) {
        val messageAt = sentAt.takeIf { it > 0L } ?: receivedAt
        if (transferId in completedTransfers) return
        if (withContext(Dispatchers.Main) {
                messages.any { it.id == transferId && !it.isTransferring }
            }
        ) {
            return
        }
        if (abort) {
            withContext(Dispatchers.Main) {
                endIncoming(
                    transferId,
                    if (reason.equals("fail", ignoreCase = true)) {
                        IncomingEnd.PeerFail
                    } else {
                        IncomingEnd.PeerCancel
                    },
                )
            }
            return
        }
        if (senderId.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                notePeerAvatar(senderId, avatarHash)
            }
        }
        val cipher = try {
            Base64.decode(encryptedData)
        } catch (_: Exception) {
            return
        }
        val clear = try {
            SecureMessageChannel.decrypt(cipher, settingsStore.magicString)
        } catch (_: Exception) {
            return
        }

        try {
            ChunkedTransfer.writePart(transferId, index, clear)
        } catch (_: Exception) {
            return
        }

        val complete = withContext(Dispatchers.Main) {
            noteIncomingChunk(
                transferId = transferId,
                index = index,
                total = total,
                hash = hash,
                sender = sender,
                senderId = senderId,
                fileName = fileName,
                fileSize = fileSize,
                isChunkedText = isChunkedText,
                sentAt = messageAt,
                caption = caption,
            )
        }
        if (!complete) return

        try {
            val dest = ChunkedTransfer.payloadFile(transferId)
            ChunkedTransfer.concatParts(transferId, total, dest)
            val actualHash = ChunkedTransfer.sha256File(dest)
            if (!actualHash.equals(hash, ignoreCase = true)) {
                withContext(Dispatchers.Main) {
                    abortedTransfers.add(transferId)
                    incoming.remove(transferId)
                    ChunkedTransfer.deleteTransfer(transferId)
                    replaceMessage(
                        transferId,
                        ChatMessage(
                            id = transferId,
                            content = "Error : $mismatchLabel hash mismatch.",
                            pic = null,
                            isError = true,
                            isSystem = true,
                        )
                    )
                }
                return
            }
            val size = dest.length()
            if (isChunkedText) {
                val text = dest.readText()
                ChunkedTransfer.deleteTransfer(transferId)
                withContext(Dispatchers.Main) {
                    if (metadata["schema"] == "akid.v1") {
                        if (messages.removeAll { it.id == transferId }) {
                            emitEvent(ChatEngineEvent.MessagesRemoved(setOf(transferId)))
                        }
                        applyIncomingEnvelope(
                            TextEnvelope(
                                sender = sender,
                                text = text,
                                senderId = senderId,
                                avatarHash = avatarHash,
                                messageId = messageId,
                                sentAt = sentAt,
                                metadata = metadata,
                            ),
                            receivedAt,
                        )
                    } else {
                        updateMessage(transferId) {
                            it.copy(
                                content = text,
                                pic = null,
                                fileBytes = null,
                                fileName = null,
                                filePath = null,
                                fileSize = size,
                                chunksReceived = 0,
                                chunksTotal = 0,
                                isChunkedText = false,
                            )
                        }
                        messages.firstOrNull { it.id == transferId }?.let { emitNotice(it) }
                    }
                }
                completedTransfers.add(transferId)
                return
            }
            val asFile = fileName != null || size > ChunkedTransfer.PREVIEW_MAX_BYTES
            val preview = if (!asFile) dest.readBytes() else null
            val displayName = fileName ?: if (asFile) "image.bin" else null
            val blobPath = ChatHistoryStore.storeBlobFromFile(transferId, dest)
            ChunkedTransfer.deleteTransfer(transferId)
            withContext(Dispatchers.Main) {
                updateMessage(transferId) {
                    it.copy(
                        pic = preview,
                        fileBytes = null,
                        fileName = displayName,
                        filePath = blobPath,
                        fileSize = size,
                        chunksReceived = 0,
                        chunksTotal = 0,
                    )
                }
                messages.firstOrNull { it.id == transferId }?.let { emitNotice(it) }
            }
            completedTransfers.add(transferId)
        } catch (_: Exception) {
            withContext(Dispatchers.Main) {
                endIncoming(transferId, IncomingEnd.LocalError)
            }
        }
    }

    private fun noteIncomingChunk(
        transferId: String,
        index: Int,
        total: Int,
        hash: String,
        sender: String,
        senderId: String,
        fileName: String?,
        fileSize: Long,
        isChunkedText: Boolean,
        sentAt: Long,
        caption: String,
    ): Boolean {
        if (transferId in abortedTransfers) return false
        val existingMsg = messages.indexOfFirst { it.id == transferId }
        if (existingMsg >= 0 && !messages[existingMsg].isTransferring) return false
        val transfer = incoming[transferId]
        if (transfer != null && existingMsg < 0) {
            cancelIncomingWatchdog(transferId)
            incoming.remove(transferId)
            abortedTransfers.add(transferId)
            ChunkedTransfer.deleteTransfer(transferId)
            return false
        }
        if (transfer != null && (transfer.hash != hash || transfer.total != total)) {
            return false
        }

        val state = transfer ?: IncomingBinary(
            hash = hash,
            total = total,
            sender = sender,
            senderId = senderId,
            fileName = fileName,
            fileSize = fileSize,
            sentAt = sentAt,
            received = BooleanArray(total),
            isChunkedText = isChunkedText,
            caption = caption,
        ).also { incoming[transferId] = it }
        if (caption.isNotBlank() && state.caption.isBlank()) {
            state.caption = caption
        }

        if (index < 0 || index >= state.received.size) return false
        val isNew = !state.received[index]
        if (isNew) {
            state.received[index] = true
            state.count++
        }
        bumpIncomingWatchdog(transferId)
        val displayName = state.fileName
        val displayContent = if (!state.isChunkedText && displayName == null) {
            state.caption
        } else {
            displayName ?: ""
        }

        if (existingMsg < 0) {
            insertMessage(
                ChatMessage(
                    id = transferId,
                    content = displayContent,
                    pic = null,
                    sender = state.sender,
                    senderId = state.senderId,
                    isMine = false,
                    chunksReceived = state.count,
                    chunksTotal = total,
                    fileName = displayName,
                    fileSize = state.fileSize,
                    isChunkedText = state.isChunkedText,
                    sentAt = state.sentAt,
                )
            )
        } else {
            val existing = messages[existingMsg]
            messages[existingMsg] = existing.copy(
                content = if (displayContent.isNotBlank()) displayContent else existing.content,
                chunksReceived = state.count,
                chunksTotal = total,
                fileSize = state.fileSize,
            )
            emitEvent(ChatEngineEvent.MessageUpsert(messages[existingMsg]))
        }

        if (state.count < total) {
            return false
        }

        cancelIncomingWatchdog(transferId)
        incoming.remove(transferId)
        return true
    }

    private fun bumpIncomingWatchdog(transferId: String) {
        cancelIncomingWatchdog(transferId)
        incomingWatchdogs[transferId] = scope.launch {
            delay(ChunkedTransfer.INCOMPLETE_TIMEOUT_MS)
            withContext(Dispatchers.Main) {
                endIncoming(transferId, IncomingEnd.Timeout)
            }
        }
    }

    private fun cancelIncomingWatchdog(transferId: String) {
        incomingWatchdogs.remove(transferId)?.cancel()
    }

    private enum class IncomingEnd {
        PeerCancel,
        PeerFail,
        Timeout,
        LocalError,
    }

    private fun endIncoming(transferId: String, why: IncomingEnd) {
        if (transferId in abortedTransfers && messages.none { it.id == transferId && it.isTransferring }) {
            return
        }
        cancelIncomingWatchdog(transferId)
        incoming.remove(transferId)
        abortedTransfers.add(transferId)
        ChunkedTransfer.deleteTransfer(transferId)
        val transferring = messages.any { it.id == transferId && it.isTransferring && !it.isMine }
        if (!transferring) return
        val text = when (why) {
            IncomingEnd.PeerCancel -> "Peer cancelled."
            IncomingEnd.PeerFail -> "Peer failed to send."
            IncomingEnd.Timeout -> "Receive timed out."
            IncomingEnd.LocalError -> "Receive failed."
        }
        replaceMessage(
            transferId,
            ChatMessage(
                id = transferId,
                content = text,
                pic = null,
                isError = true,
                isSystem = true,
            ),
        )
    }

    private fun ownAvatarHash(): String {
        val bytes = settingsStore.avatarBytes ?: return ""
        return ChunkedTransfer.sha256Hex(bytes)
    }

    private fun notePeerAvatar(senderId: String, hash: String) {
        if (senderId.isEmpty() || senderId == settingsStore.deviceId) return
        if (hash.isEmpty()) {
            peerAvatars.remove(senderId)
            peerAvatarHash.remove(senderId)
            pendingWantAt.remove(senderId)
            emitEvent(ChatEngineEvent.PeerAvatarChanged(senderId, null))
            scope.launch(Dispatchers.IO) {
                try {
                    PeerAvatarStore.delete(senderId)
                } catch (_: Exception) {
                }
            }
            return
        }
        if (peerAvatarHash[senderId] != hash) {
            peerAvatars.remove(senderId)
            emitEvent(ChatEngineEvent.PeerAvatarChanged(senderId, null))
        }
        if (peerAvatarHash[senderId] == hash && peerAvatars[senderId] != null) return
        val now = System.currentTimeMillis()
        val last = pendingWantAt[senderId] ?: 0L
        if (now - last < 8_000L) return
        pendingWantAt[senderId] = now
        scope.launch(Dispatchers.IO) {
            publishAvatarWant(senderId, hash)
        }
    }

    private suspend fun requestKnownPeerAvatars() {
        val ids = withContext(Dispatchers.Main.immediate) {
            buildSet {
                messages.mapTo(this) { it.senderId }
                addAll(peerAvatars.keys)
                addAll(memberStates.keys)
            }.filter { it.isNotEmpty() && it != settingsStore.deviceId }
        }
        for (id in ids) {
            avatarRefreshRequested.add(id)
            val hash = withContext(Dispatchers.Main.immediate) { peerAvatarHash[id] ?: "" }
            publishAvatarWant(id, hash)
        }
    }

    private fun schedulePeerAvatarRefresh(agentId: String) {
        if (agentId.isEmpty() || agentId == settingsStore.deviceId) return
        if (!avatarRefreshRequested.add(agentId)) return
        val hash = peerAvatarHash[agentId] ?: ""
        scope.launch(Dispatchers.IO) {
            publishAvatarWant(agentId, hash)
        }
    }

    private suspend fun publishAvatarWant(senderId: String, hash: String) {
        try {
            mqttService.publish(
                "tokifactor/avatar",
                AvatarEnvelope.want(senderId, hash).encode(),
                MqttDeliveryQos.AtMostOnce,
            )
        } catch (_: Exception) {
        }
    }

    private suspend fun publishAvatarHave(force: Boolean = false) {
        val hash = ownAvatarHash()
        if (!force && lastPublishedAvatarHash == hash) return
        val bytes = settingsStore.avatarBytes
        val data = if (bytes != null) {
            Base64.encode(SecureMessageChannel.encrypt(bytes, settingsStore.magicString))
        } else {
            ""
        }
        val payload = AvatarEnvelope.have(settingsStore.deviceId, hash, data).encode()
        val maxPacket = mqttService.getMaximumPacketSize()
        if (maxPacket != null && payload.encodeToByteArray().size > maxPacket) return
        try {
            mqttService.publish("tokifactor/avatar", payload, MqttDeliveryQos.AtMostOnce)
            lastPublishedAvatarHash = hash
        } catch (_: Exception) {
        }
    }

    private suspend fun handleAvatarEnvelope(payload: String) {
        val envelope = AvatarEnvelope.decode(payload) ?: return
        when (envelope.type) {
            AvatarEnvelope.WANT -> {
                if (envelope.id == settingsStore.deviceId) {
                    publishAvatarHave(force = true)
                }
            }
            AvatarEnvelope.HAVE -> {
                if (envelope.id == settingsStore.deviceId) return
                applyPeerAvatar(envelope)
            }
        }
    }

    private suspend fun applyPeerAvatar(envelope: AvatarEnvelope) {
        if (envelope.hash.isEmpty() || envelope.data.isEmpty()) {
            try {
                PeerAvatarStore.delete(envelope.id)
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) {
                peerAvatars.remove(envelope.id)
                peerAvatarHash.remove(envelope.id)
                pendingWantAt.remove(envelope.id)
                emitEvent(ChatEngineEvent.PeerAvatarChanged(envelope.id, null))
            }
            return
        }
        val clear = try {
            val cipher = Base64.decode(envelope.data)
            SecureMessageChannel.decrypt(cipher, settingsStore.magicString)
        } catch (_: Exception) {
            return
        }
        val actual = ChunkedTransfer.sha256Hex(clear)
        if (!actual.equals(envelope.hash, ignoreCase = true)) return
        try {
            PeerAvatarStore.save(envelope.id, clear)
        } catch (_: Exception) {
        }
        withContext(Dispatchers.Main) {
            peerAvatars[envelope.id] = clear
            peerAvatarHash[envelope.id] = envelope.hash
            pendingWantAt.remove(envelope.id)
            emitEvent(ChatEngineEvent.PeerAvatarChanged(envelope.id, clear))
        }
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) {
            messages[i] = transform(messages[i])
            emitEvent(ChatEngineEvent.MessageUpsert(messages[i]))
            if (!messages[i].isTransferring) schedulePersist()
        }
    }

    private fun replaceMessage(id: String, message: ChatMessage) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) {
            messages[i] = message
            emitEvent(ChatEngineEvent.MessageUpsert(message))
        } else {
            insertMessage(message)
        }
        if (!message.isTransferring) schedulePersist()
    }

    private fun schedulePersist() {
        val gen = persistGeneration.get()
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(300)
            if (gen != persistGeneration.get()) return@launch
            val snap = withContext(Dispatchers.Main.immediate) {
                messages.filter { it.shouldPersist }.toList()
            }
            if (gen != persistGeneration.get()) return@launch
            try {
                ChatHistoryStore.save(snap)
            } catch (_: Exception) {
            }
        }
    }
}

internal fun insertionIndexBySentAt(messages: List<ChatMessage>, sentAt: Long): Int {
    val index = messages.indexOfFirst { it.sentAt > sentAt }
    return if (index >= 0) index else messages.size
}
