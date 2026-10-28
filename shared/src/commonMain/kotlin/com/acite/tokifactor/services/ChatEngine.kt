package com.acite.tokifactor.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.acite.tokifactor.copyFileToPlatformFile
import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.aliasList
import com.acite.tokifactor.model.AvatarEnvelope
import com.acite.tokifactor.model.ChatAttachment
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.ConversationSummary
import com.acite.tokifactor.model.GROUP_CONVERSATION
import com.acite.tokifactor.model.SOLO_END
import com.acite.tokifactor.model.SOLO_FAILED
import com.acite.tokifactor.model.SOLO_READY
import com.acite.tokifactor.model.SOLO_START
import com.acite.tokifactor.model.SoloState
import com.acite.tokifactor.model.deriveSoloMagic
import com.acite.tokifactor.model.soloConversationId
import com.acite.tokifactor.model.FileChunk
import com.acite.tokifactor.model.InboundNotice
import com.acite.tokifactor.model.MEMBER_KIND_USER
import com.acite.tokifactor.model.MemberIdentity
import com.acite.tokifactor.model.canonicalMemberName
import com.acite.tokifactor.model.PictureChunk
import com.acite.tokifactor.model.TextChunk
import com.acite.tokifactor.model.TextEnvelope
import com.acite.tokifactor.model.WireJson
import com.acite.tokifactor.model.selectedMember
import com.acite.tokifactor.model.shouldExpireAki
import com.acite.tokifactor.model.memberControlMetadata
import com.acite.tokifactor.model.memberNameKey
import com.acite.tokifactor.model.isMemberId
import com.acite.tokifactor.model.legacyMemberId
import com.acite.tokifactor.model.withControlProfile
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
import kotlinx.coroutines.cancelAndJoin
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
    private val identityRegistry: MemberIdentityRegistry,
) : ChatSession {
    companion object {
        private const val CONTROL_TOPIC = "tokifactor/control"
        private const val STATUS_TIMEOUT_MS = 30_000L
        private const val IDLE_PROBE_MS = 15_000L
        private const val ROSTER_PROBE_MS = 20_000L
        private const val PROTOCOL_VERSION = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val historyReady = AtomicBoolean(false)

    override val messages: List<ChatMessage>
        field = mutableStateListOf()

    override var currentConversationId by mutableStateOf(settingsStore.conversationId)
        private set

    override var soloState by mutableStateOf(
        SoloState(
            active = settingsStore.soloActive,
            phase = if (settingsStore.soloActive) "active" else "idle",
            sessionId = settingsStore.soloSessionId,
            targetId = settingsStore.soloTargetId,
            targetName = settingsStore.soloTargetName,
            startedAt = settingsStore.soloStartedAt,
        )
    )
        private set

    override val visibleMessages: List<ChatMessage>
        get() = messages.filter { it.conversationId == currentConversationId }

    override val conversations: List<ConversationSummary>
        get() {
            val summaries = linkedMapOf<String, ConversationSummary>()
            summaries[GROUP_CONVERSATION] = ConversationSummary(
                id = GROUP_CONVERSATION,
                title = "Group chat",
            )
            for (message in messages) {
                val id = message.conversationId
                if (!id.startsWith("solo:")) continue
                val memberId = id.removePrefix("solo:")
                val title = if (soloState.active && memberId == soloState.targetId) {
                    "Solo · ${soloState.targetName}"
                } else {
                    "Solo · ${identityRegistry.get(memberId)?.name ?: memberId}"
                }
                val previous = summaries[id]
                summaries[id] = ConversationSummary(
                    id = id,
                    title = title,
                    memberId = memberId,
                    lastMessageAt = maxOf(previous?.lastMessageAt ?: 0L, message.sentAt),
                )
            }
            if (soloState.active && soloState.targetId.isNotBlank()) {
                val id = soloConversationId(soloState.targetId)
                summaries[id] = ConversationSummary(
                    id = id,
                    title = "Solo · ${soloState.targetName}",
                    memberId = soloState.targetId,
                    lastMessageAt = summaries[id]?.lastMessageAt ?: 0L,
                )
            }
            return summaries.values.sortedWith(
                compareBy<ConversationSummary> { it.id != GROUP_CONVERSATION }
                    .thenByDescending { it.lastMessageAt }
            )
        }

    private var pendingSoloStart: String = ""
    private var pendingSoloTargetId: String = ""
    private var pendingSoloTargetName: String = ""

    override var avatarBytes by mutableStateOf(settingsStore.avatarBytes)
        private set

    override var connected by mutableStateOf(false)
        private set

    private val memberStates = mutableStateMapOf<String, AgentStatus>()
    private val memberSources = mutableMapOf<String, MutableMap<String, Long>>()
    private val memberOwners = mutableMapOf<String, String>()
    private val avatarOwners = mutableMapOf<String, String>()

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

    override fun selectAgent(agentId: String) {
        if (agentId.isNotBlank()) selectedAgentId = agentId
    }

    override fun selectConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        currentConversationId = conversationId
        settingsStore.updateConversationId(conversationId)
        emitStateChanged()
    }

    private fun emitStateChanged() {
        emitEvent(ChatEngineEvent.StateChanged(currentConversationId, soloState))
    }

    private fun soloMagic(): String? {
        val target = soloState.targetId
        if (target.isBlank() || !soloState.active) return null
        return deriveSoloMagic(settingsStore.magicString, target)
    }

    private fun activeMagicCandidates(): List<String> = listOfNotNull(
        soloMagic()?.takeIf { soloState.active },
        settingsStore.magicString,
    ).distinct()

    private fun magicForConversation(conversationId: String): String {
        if (!conversationId.startsWith("solo:")) return settingsStore.magicString
        val target = conversationId.removePrefix("solo:")
        return deriveSoloMagic(settingsStore.magicString, target)
    }

    private fun canSendInCurrentConversation(): Boolean {
        if (!soloState.active) return currentConversationId == GROUP_CONVERSATION
        return currentConversationId == soloConversationId(soloState.targetId)
    }

    private fun soloControlMetadata(
        kind: String,
        sessionId: String,
        targetId: String,
        targetName: String,
        startedAt: String,
        error: String = "",
    ): Map<String, String> = mapOf(
        "schema" to "akid.v1",
        "kind" to kind,
        "protocolVersion" to PROTOCOL_VERSION.toString(),
        "memberId" to settingsStore.memberId,
        "memberName" to settingsStore.displayName,
        "sourceId" to settingsStore.deviceId,
        "memberKind" to MEMBER_KIND_USER,
        "soloId" to sessionId,
        "soloTargetId" to targetId,
        "soloTargetName" to targetName,
        "soloStartedAt" to startedAt,
        if (error.isBlank()) "" to "" else "soloError" to error,
    ).filterKeys { it.isNotEmpty() }

    private suspend fun publishSoloControl(
        kind: String,
        sessionId: String,
        targetId: String,
        targetName: String,
        startedAt: String,
        magic: String,
        retain: Boolean,
        error: String = "",
    ) {
        val envelope = TextEnvelope(
            sender = settingsStore.displayName,
            text = "tokifactor solo control",
            senderId = settingsStore.memberId,
            metadata = soloControlMetadata(
                kind = kind,
                sessionId = sessionId,
                targetId = targetId,
                targetName = targetName,
                startedAt = startedAt,
                error = error,
            ),
        )
        val payload = Base64.encode(
            SecureMessageChannel.encrypt(
                envelope.encode().encodeToByteArray(),
                magic,
            )
        )
        mqttService.publish(
            CONTROL_TOPIC,
            payload,
            MqttDeliveryQos.AtLeastOnce,
            retain = retain,
        )
    }

    override fun startSolo(memberId: String, memberName: String) {
        if (memberId.isBlank()) return
        if (soloState.active || soloState.phase == "starting") return
        val sessionId = "solo-${UUID.randomUUID()}"
        val startedAt = java.time.Instant.now().toString()
        pendingSoloStart = sessionId
        pendingSoloTargetId = memberId
        pendingSoloTargetName = memberName
        soloState = SoloState(
            active = false,
            phase = "starting",
            sessionId = sessionId,
            targetId = memberId,
            targetName = memberName,
            startedAt = startedAt,
        )
        emitStateChanged()
        scope.launch(Dispatchers.IO) {
            try {
                publishSoloControl(
                    SOLO_START,
                    sessionId,
                    memberId,
                    memberName,
                    startedAt,
                    settingsStore.magicString,
                    retain = true,
                )
            } catch (_: Exception) {
                failPendingSolo("cannot publish solo start")
            }
        }
        scope.launch(Dispatchers.IO) {
            delay(8_000L)
            if (pendingSoloStart == sessionId && !soloState.active) {
                failPendingSolo("solo target did not become ready")
            }
        }
    }

    private fun failPendingSolo(message: String) {
        val sessionId = pendingSoloStart
        val targetId = pendingSoloTargetId
        val targetName = pendingSoloTargetName
        pendingSoloStart = ""
        pendingSoloTargetId = ""
        pendingSoloTargetName = ""
        soloState = SoloState(active = false, phase = "error", error = message)
        emitStateChanged()
        if (sessionId.isNotBlank() && targetId.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    publishSoloControl(
                        SOLO_END,
                        sessionId,
                        targetId,
                        targetName,
                        "",
                        settingsStore.magicString,
                        retain = true,
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun activateSolo(
        sessionId: String,
        targetId: String,
        targetName: String,
        startedAt: String,
    ) {
        pendingSoloStart = ""
        pendingSoloTargetId = ""
        pendingSoloTargetName = ""
        soloState = SoloState(
            active = true,
            phase = "active",
            sessionId = sessionId,
            targetId = targetId,
            targetName = targetName,
            startedAt = startedAt,
        )
        val conversationId = soloConversationId(targetId)
        currentConversationId = conversationId
        settingsStore.updateSoloState(
            sessionId = sessionId,
            targetId = targetId,
            targetName = targetName,
            startedAt = startedAt,
            conversationId = conversationId,
        )
        selectedAgentId = targetId
        emitStateChanged()
    }

    override fun endSolo() {
        val session = soloState
        if (!session.sessionId.isNotBlank() && !session.active) return
        soloState = SoloState(active = false, phase = "ending")
        currentConversationId = GROUP_CONVERSATION
        settingsStore.clearSoloState(GROUP_CONVERSATION)
        emitStateChanged()
        scope.launch(Dispatchers.IO) {
            val groupMagic = settingsStore.magicString
            val soloMagic = if (session.targetId.isNotBlank()) {
                deriveSoloMagic(groupMagic, session.targetId)
            } else {
                ""
            }
            for (magic in listOf(soloMagic, groupMagic).filter { it.isNotBlank() }.distinct()) {
                try {
                    publishSoloControl(
                        SOLO_END,
                        session.sessionId,
                        session.targetId,
                        session.targetName,
                        session.startedAt,
                        magic,
                        retain = true,
                    )
                } catch (_: Exception) {
                }
            }
            soloState = SoloState(active = false, phase = "idle")
            emitStateChanged()
        }
    }

    override fun findMember(name: String): AgentStatus? =
        memberStates.values.firstOrNull { member ->
            canonicalMemberName(member.memberName) == canonicalMemberName(name) ||
                member.memberId == name ||
                member.aliasList().any { alias -> canonicalMemberName(alias) == canonicalMemberName(name) }
        }

    override val deviceId: String
        get() = settingsStore.deviceId

    override val displayName: String
        get() = settingsStore.displayName

    override val memberId: String
        get() = settingsStore.memberId

    override val bio: String
        get() = settingsStore.bio

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
    private class IncomingImageGroup(var expected: Int) {
        val attachments = mutableMapOf<Int, ChatAttachment>()
        var envelope: TextEnvelope? = null
        var metadata: Map<String, String> = emptyMap()
        var eventAt: Long = 0L
        var sender: String = ""
        var senderId: String = ""
        var sentAt: Long = 0L
        var timer: Job? = null
        var expectText: Boolean = false
    }

    private val incomingImageGroups = mutableMapOf<String, IncomingImageGroup>()
    private val incomingWatchdogs = mutableMapOf<String, Job>()
    private val abortedTransfers = mutableSetOf<String>()
    private val completedTransfers = ConcurrentHashMap.newKeySet<String>()
    private val transferConversations = ConcurrentHashMap<String, String>()
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
            identityRegistry.register(
                MemberIdentity(
                    id = settingsStore.memberId,
                    name = settingsStore.displayName,
                    aliases = emptyList(),
                    kind = MEMBER_KIND_USER,
                ),
                source = settingsStore.deviceId,
            )
            val ownId = settingsStore.memberId
            val ownNameKey = canonicalMemberName(settingsStore.displayName)
            val migratedAvatars = linkedMapOf<String, PeerAvatarStore.Entry>()
            for ((key, entry) in cachedAvatars) {
                if (key == ownId) continue
                if (key == settingsStore.deviceId || canonicalMemberName(key) == ownNameKey) {
                    PeerAvatarStore.delete(key)
                    continue
                }
                val target = if (isMemberId(key)) {
                    key
                } else {
                    identityRegistry.resolve(key).ifBlank { legacyMemberId(key) }
                }
                if (target != key) PeerAvatarStore.migrateKey(key, target)
                migratedAvatars[target] = entry.copy(id = target)
            }
            withContext(Dispatchers.Main) {
                for ((id, entry) in migratedAvatars) {
                    peerAvatars[id] = entry.bytes
                    peerAvatarHash[id] = entry.hash
                    emitEvent(ChatEngineEvent.PeerAvatarChanged(id, entry.bytes))
                }
                if (loaded.isNotEmpty()) {
                    val existing = messages.map { it.id }.toHashSet()
                    val restored = loaded
                        .filter { it.id !in existing }
                        .map(::normalizeStoredMessage)
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
            if (loaded.isNotEmpty()) schedulePersist()
            startMqttSession()
        }
    }

    override fun refreshSettings() {
        avatarBytes = settingsStore.avatarBytes
        val ownId = settingsStore.memberId
        for (index in messages.indices) {
            val message = messages[index]
            val mine = message.senderId == ownId
            val name = when {
                mine -> settingsStore.displayName
                message.senderId.isBlank() -> message.sender
                else -> identityRegistry.get(message.senderId)?.name ?: message.sender
            }
            if (mine != message.isMine || name != message.sender) {
                messages[index] = message.copy(isMine = mine, sender = name)
                emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
            }
        }
        if (channelKey() != sessionChannelKey) {
            // start() owns the first session start; racing it here can cancel an in-flight connect.
            if (historyReady.get()) startMqttSession()
        } else if (connected) {
            scope.launch {
                publishAkiProbe()
                publishAvatarHave(force = true)
            }
        }
    }

    private fun normalizeStoredMessage(message: ChatMessage): ChatMessage {
        val ownId = settingsStore.memberId
        val target = when {
            isMemberId(message.senderId) -> message.senderId
            message.senderId == settingsStore.deviceId -> ownId
            canonicalMemberName(message.sender) == canonicalMemberName(settingsStore.displayName) -> ownId
            else -> identityRegistry.resolve(message.sender).ifBlank {
                legacyMemberId(message.sender.ifBlank { message.senderId })
            }
        }
        val name = when {
            target == ownId -> settingsStore.displayName
            else -> identityRegistry.get(target)?.name ?: message.sender
        }
        return message.copy(
            senderId = target,
            sender = name.ifBlank { message.sender },
            isMine = target == ownId,
        )
    }

    private fun resolveIncomingMemberId(
        sender: String,
        senderId: String,
        metadata: Map<String, String> = emptyMap(),
    ): String {
        val explicit = metadata["memberId"].orEmpty().trim()
        if (isMemberId(explicit)) return explicit
        val envelopeId = senderId.trim()
        if (isMemberId(envelopeId)) return envelopeId
        return identityRegistry.resolve(sender).ifBlank { legacyMemberId(sender) }
    }

    private fun normalizeMemberReference(value: String): String {
        val reference = value.trim()
        if (reference.isEmpty() || isMemberId(reference)) return reference
        if (reference == settingsStore.deviceId) return settingsStore.memberId
        return identityRegistry.resolve(reference).ifBlank {
            legacyMemberId(reference)
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
        controlReady = false
        stopAkiControlLoop(clearIdentity = true)
        val previous = mqttSessionJob
        mqttSessionJob = scope.launch {
            // Let the old client finish disconnecting before the replacement reuses its client id.
            previous?.cancelAndJoin()
            if (generation != sessionGeneration.get()) return@launch
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
                                    val decoded = withContext(Dispatchers.Default) {
                                        decodeEnvelopeWithMagic(message.payload)
                                    } ?: return@collect
                                    val (envelope, magic) = decoded
                                    withContext(Dispatchers.Main) {
                                        applyIncomingEnvelope(envelope, message.timestamp)
                                    }
                                    tagEnvelopeConversation(
                                        envelope,
                                        conversationForMagic(magic),
                                    )
                                    notePeerAvatar(
                                        resolveIncomingMemberId(
                                            envelope.sender,
                                            envelope.senderId,
                                            envelope.metadata,
                                        ),
                                        envelope.avatarHash,
                                    )
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
                    mqttService.subscribe(CONTROL_TOPIC, MqttDeliveryQos.AtLeastOnce)
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

    private fun decodeEnvelopeWithMagic(payload: String): Pair<TextEnvelope, String>? {
        val cipher = try {
            Base64.decode(payload)
        } catch (_: Exception) {
            return null
        }
        for (magic in activeMagicCandidates()) {
            val clear = try {
                SecureMessageChannel.decrypt(cipher, magic).decodeToString()
            } catch (_: Exception) {
                continue
            }
            val envelope = TextEnvelope.decode(clear) ?: continue
            return envelope to magic
        }
        return null
    }

    private fun decodeEnvelope(payload: String): TextEnvelope? =
        decodeEnvelopeWithMagic(payload)?.first

    private fun conversationForMagic(magic: String): String {
        val target = soloState.targetId
        if (
            soloState.active &&
            target.isNotBlank() &&
            magic == deriveSoloMagic(settingsStore.magicString, target)
        ) {
            return soloConversationId(target)
        }
        return GROUP_CONVERSATION
    }

    private fun conversationForIncomingSender(senderId: String): String {
        val target = soloState.targetId
        return if (soloState.active && target.isNotBlank() && senderId == target) {
            soloConversationId(target)
        } else {
            GROUP_CONVERSATION
        }
    }

    private fun tagEnvelopeConversation(
        envelope: TextEnvelope,
        conversationId: String,
    ) {
        if (conversationId == GROUP_CONVERSATION) return
        val messageId = envelope.messageId
        val turnId = envelope.metadata["turnId"].orEmpty()
        scope.launch(Dispatchers.Main) {
            for (index in messages.indices) {
                val current = messages[index]
                if (
                    (messageId.isNotBlank() && current.id == messageId) ||
                    (turnId.isNotBlank() && current.turnId == turnId)
                ) {
                    if (current.conversationId != conversationId) {
                        messages[index] = current.copy(conversationId = conversationId)
                        emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
                    }
                }
            }
        }
    }

    private fun parseBoolean(value: String?): Boolean? = when (value?.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> null
    }

    private fun isOwnMember(memberId: String): Boolean =
        memberId == settingsStore.memberId

    private fun startAkiControlLoop() {
        akiControlJob?.cancel()
        lastProbeSentAt = 0L
        akiControlJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val hasMembers = withContext(Dispatchers.Main) {
                    val expired = memberStates.filterValues {
                        shouldExpireAki(it.present, it.lastSeenAt, now, STATUS_TIMEOUT_MS)
                    }
                    if (expired.isNotEmpty()) {
                        for (key in expired.keys) {
                            val current = memberStates[key] ?: continue
                            val sources = memberSources[key]
                            if (sources != null) {
                                sources.entries.removeAll { (_, seenAt) ->
                                    now - seenAt > STATUS_TIMEOUT_MS
                                }
                                if (sources.isNotEmpty()) {
                                    memberStates[key] = current.copy(
                                        present = true,
                                        lastSeenAt = sources.values.max(),
                                    )
                                    continue
                                }
                            }
                            memberStates[key] = current.copy(present = false)
                        }
                        if (selectedAgentId != null && memberStates[selectedAgentId]?.present != true) {
                            selectedAgentId = null
                        }
                        emitMembersChanged()
                    }
                    memberStates.values.any { it.present }
                }
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
            memberSources.clear()
            if (clearIdentity) {
                selectedAgentId = null
                memberOwners.clear()
                avatarOwners.clear()
            }
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
            senderId = settingsStore.memberId,
            avatarHash = ownAvatarHash(),
            metadata = mapOf(
                "schema" to "akid.v1",
                "kind" to "probe",
                "protocolVersion" to PROTOCOL_VERSION.toString(),
                "probeId" to probeId,
            ) + memberControlMetadata(
                name = settingsStore.displayName,
                bio = settingsStore.bio,
                memberKind = MEMBER_KIND_USER,
                memberId = settingsStore.memberId,
                sourceId = settingsStore.deviceId,
                aliases = identityRegistry.get(settingsStore.memberId)?.aliases.orEmpty(),
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
        if (kind == SOLO_START || kind == SOLO_READY || kind == SOLO_FAILED || kind == SOLO_END) {
            applySoloControl(meta)
            return
        }
        if (kind != "presence" && kind != "status" && kind != "offline") return
        if (meta["protocolVersion"]?.toIntOrNull() != PROTOCOL_VERSION) return
        val senderId = envelope.senderId
        val memberName = meta["memberName"].orEmpty().trim()
            .ifBlank { meta["agentName"].orEmpty().trim() }
            .ifBlank { envelope.sender.trim() }
        val memberId = meta["memberId"].orEmpty().trim().takeIf(::isMemberId)
            ?: senderId.trim().takeIf(::isMemberId)
            ?: identityRegistry.resolve(memberName).ifBlank { legacyMemberId(memberName) }
        if (memberId.isBlank() || memberId == settingsStore.memberId) return
        val sourceId = meta["sourceId"].orEmpty().trim()
            .ifBlank { senderId.trim().ifBlank { memberId } }
        val aliases = meta["aliases"].orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val claimed = identityRegistry.observe(
            MemberIdentity(
                id = memberId,
                name = memberName.ifBlank { memberId },
                aliases = aliases,
                kind = meta["memberKind"].orEmpty().ifBlank { "agent" },
            ),
            source = sourceId,
        )
        val profileMetadata = if (claimed) {
            meta
        } else {
            meta - "memberName" - "agentName" - "aliases"
        }
        val effectiveName = if (claimed) {
            memberName
        } else {
            memberStates[memberId]?.memberName?.ifBlank { memberId } ?: memberId
        }
        if (kind == "offline") {
            val sources = memberSources[memberId]
            sources?.remove(sourceId)
            if (sources.isNullOrEmpty()) {
                memberStates.remove(memberId)
                memberSources.remove(memberId)
                memberOwners.remove(memberId)
                avatarRefreshRequested.remove(memberId)
                if (selectedAgentId == memberId) selectedAgentId = null
            } else {
                memberStates[memberId]?.let { current ->
                    memberStates[memberId] = current.copy(
                        present = true,
                        lastSeenAt = sources.values.max(),
                    )
                }
            }
            emitMembersChanged()
            return
        }
        val now = System.currentTimeMillis()
        val firstSeen = memberStates[memberId] == null
        val old = memberStates[memberId] ?: AgentStatus(memberId = memberId)
        val owner = memberOwners.getOrPut(memberId) { sourceId }
        memberSources.getOrPut(memberId) { mutableMapOf() }[sourceId] = now
        val updated = old.withControlProfile(
            metadata = profileMetadata,
            fallbackName = effectiveName,
            now = now,
            updateProfile = owner == sourceId,
        ).copy(
            memberId = memberId,
            model = meta["model"] ?: old.model,
            reasoning = meta["reasoning"] ?: old.reasoning,
            contextLimit = meta["contextLimit"]?.toIntOrNull() ?: old.contextLimit,
            contextTokens = meta["contextTokens"]?.toIntOrNull() ?: old.contextTokens,
            backendReady = parseBoolean(meta["backendReady"]) ?: old.backendReady,
            channelReady = parseBoolean(meta["channelReady"]) ?: old.channelReady,
            sandboxReady = parseBoolean(meta["sandboxReady"]) ?: old.sandboxReady,
            queueDepth = meta["queueDepth"]?.toIntOrNull() ?: old.queueDepth,
            activeTurn = parseBoolean(meta["activeTurn"]) ?: old.activeTurn,
            waitingUser = parseBoolean(meta["waitingUser"]) ?: old.waitingUser,
            waitingOther = parseBoolean(meta["waitingOther"]) ?: old.waitingOther,
            pendingApprovals = meta["pendingApprovals"]?.toIntOrNull()
                ?: old.pendingApprovals,
            uptimeS = meta["uptimeS"]?.toLongOrNull() ?: old.uptimeS,
            memoryTokens = meta["memoryTokens"]?.toIntOrNull() ?: old.memoryTokens,
        )
        memberStates[memberId] = updated
        if (updated.memberName != old.memberName && updated.memberName.isNotBlank()) {
            for (index in messages.indices) {
                val message = messages[index]
                if (message.senderId == memberId && message.sender != updated.memberName) {
                    messages[index] = message.copy(sender = updated.memberName)
                    emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
                }
            }
        }
        if (selectedAgentId != null && memberStates[selectedAgentId]?.present != true) {
            selectedAgentId = null
        }
        if (firstSeen) {
            schedulePeerAvatarRefresh(memberId)
        }
        emitMembersChanged()
    }

    private fun applySoloControl(meta: Map<String, String>) {
        val kind = meta["kind"].orEmpty()
        val sessionId = meta["soloId"].orEmpty()
        val targetId = meta["soloTargetId"].orEmpty()
        val targetName = meta["soloTargetName"].orEmpty()
        val startedAt = meta["soloStartedAt"].orEmpty()
        if (sessionId.isBlank() || targetId.isBlank()) return
        when (kind) {
            SOLO_START -> {
                if (soloState.active && soloState.sessionId == sessionId) return
                if (soloState.active && soloState.sessionId != sessionId) return
                if (pendingSoloStart.isBlank()) {
                    activateSolo(sessionId, targetId, targetName, startedAt)
                }
            }
            SOLO_READY -> {
                if (
                    soloState.active &&
                    soloState.sessionId == sessionId
                ) return
                if (pendingSoloStart.isNotBlank() && pendingSoloStart != sessionId) return
                activateSolo(sessionId, targetId, targetName, startedAt)
            }
            SOLO_FAILED -> {
                if (
                    (pendingSoloStart.isBlank() || pendingSoloStart == sessionId) &&
                    (!soloState.active || soloState.sessionId == sessionId)
                ) {
                    pendingSoloStart = ""
                    soloState = SoloState(
                        active = false,
                        phase = "error",
                        sessionId = sessionId,
                        targetId = targetId,
                        targetName = targetName,
                        error = meta["soloError"].orEmpty().ifBlank { "solo failed" },
                    )
                }
            }
            SOLO_END -> {
                if (
                    soloState.sessionId.isBlank() ||
                    soloState.sessionId == sessionId
                ) {
                    pendingSoloStart = ""
                    pendingSoloTargetId = ""
                    pendingSoloTargetName = ""
                    soloState = SoloState(active = false, phase = "idle")
                    if (currentConversationId.startsWith("solo:")) {
                        currentConversationId = GROUP_CONVERSATION
                    }
                    settingsStore.clearSoloState(currentConversationId)
                    emitStateChanged()
                }
            }
        }
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
        attachments: List<ChatAttachment> = emptyList(),
        conversationId: String = GROUP_CONVERSATION,
    ) {
        if (messages.any { it.id == id }) return
        val message = ChatMessage(
            id = id,
            content = content,
            pic = null,
            conversationId = conversationId,
            sender = sender,
            senderId = senderId,
            isMine = isMine,
            sentAt = sentAt,
            attachments = attachments,
        )
        insertMessage(message)
        emitNotice(message)
        schedulePersist()
    }

    private fun applyIncomingEnvelope(
        envelope: TextEnvelope,
        receivedAt: Long = System.currentTimeMillis(),
        attachments: List<ChatAttachment> = emptyList(),
        deferAttachments: Boolean = true,
    ) {
        val eventAt = envelope.sentAt.takeIf { it > 0L } ?: receivedAt
        val meta = envelope.metadata
        val memberId = resolveIncomingMemberId(
            envelope.sender,
            envelope.senderId,
            meta,
        )
        val senderName = meta["memberName"].orEmpty().trim()
            .ifBlank { meta["agentName"].orEmpty().trim() }
            .ifBlank { identityRegistry.get(memberId)?.name.orEmpty() }
            .ifBlank { envelope.sender }
        if (memberId.isNotBlank() && memberId != settingsStore.memberId) {
            identityRegistry.observe(
                MemberIdentity(
                    id = memberId,
                    name = senderName.ifBlank { memberId },
                    aliases = meta["aliases"].orEmpty().split(',').map(String::trim)
                        .filter(String::isNotEmpty),
                    kind = meta["memberKind"].orEmpty().ifBlank { "agent" },
                ),
                source = meta["sourceId"].orEmpty().ifBlank { envelope.senderId },
            )
        }
        val normalized = envelope.copy(sender = senderName, senderId = memberId)
        val expectedAttachments = meta["attachmentCount"]?.toIntOrNull() ?: 0
        var resolvedAttachments = attachments
        if (deferAttachments && expectedAttachments > 0 && resolvedAttachments.isEmpty()) {
            val imageOnly = messages.firstOrNull {
                it.id == normalized.messageId && !it.isAgent && it.attachments.isNotEmpty()
            }
            if (imageOnly != null) {
                val removedId = imageOnly.id
                messages.removeAll { it.id == removedId }
                emitEvent(ChatEngineEvent.MessagesRemoved(setOf(removedId)))
                resolvedAttachments = imageOnly.attachments
            } else {
                deferAttachmentEnvelope(normalized, meta, expectedAttachments, eventAt)
                return
            }
        }
        val effectiveAttachments = if (resolvedAttachments.isEmpty() && expectedAttachments > 0) {
            attachmentPlaceholders(meta, expectedAttachments)
        } else {
            resolvedAttachments
        }
        if (meta["schema"] != "akid.v1") {
            addChatText(
                normalized.text,
                sender = normalized.sender,
                senderId = normalized.senderId,
                isMine = isOwnMember(normalized.senderId),
                id = normalized.messageId.ifBlank { UUID.randomUUID().toString() },
                sentAt = eventAt,
                attachments = effectiveAttachments,
            )
            return
        }
        val kind = meta["kind"].orEmpty()
        val turnId = meta["turnId"].orEmpty()
        val seq = meta["seq"]?.toIntOrNull() ?: 0
        when (kind) {
            "turn" -> applyAgentTurn(normalized, turnId, seq, eventAt, effectiveAttachments)
            "tool" -> applyAgentTool(normalized, turnId, seq, eventAt)
            "approval" -> applyAgentApproval(normalized, turnId, seq, eventAt)
            "approval_resolved" -> applyAgentApprovalResolved(normalized, turnId, seq, eventAt)
            "error" -> applyAgentError(normalized, turnId, seq, eventAt)
            "wait_user" -> applyAgentWaitUser(normalized, meta, eventAt)
            "wait_other" -> applyAgentWaitOther(normalized, meta, eventAt)
        }
    }

    private fun applyAgentWaitUser(
        envelope: TextEnvelope,
        metadata: Map<String, String>,
        eventAt: Long,
    ) {
        val memberId = envelope.senderId
        if (memberId.isBlank() || memberId == settingsStore.memberId) return
        val waiting = parseBoolean(metadata["waitingUser"]) ?: return
        val old = memberStates[memberId]
        memberStates[memberId] = (old ?: AgentStatus(
            memberId = memberId,
            memberName = envelope.sender,
        )).copy(
            present = true,
            lastSeenAt = maxOf(old?.lastSeenAt ?: 0L, eventAt),
            waitingUser = waiting,
        )
        emitMembersChanged()
    }

    private fun applyAgentWaitOther(
        envelope: TextEnvelope,
        metadata: Map<String, String>,
        eventAt: Long,
    ) {
        val memberId = envelope.senderId
        if (memberId.isBlank() || memberId == settingsStore.memberId) return
        val waiting = parseBoolean(metadata["waitingOther"]) ?: return
        val old = memberStates[memberId]
        memberStates[memberId] = (old ?: AgentStatus(
            memberId = memberId,
            memberName = envelope.sender,
        )).copy(
            present = true,
            lastSeenAt = maxOf(old?.lastSeenAt ?: 0L, eventAt),
            waitingOther = waiting,
        )
        emitMembersChanged()
    }

    private fun attachmentPlaceholders(
        metadata: Map<String, String>,
        expected: Int,
    ): List<ChatAttachment> {
        val ids = metadata["attachmentIds"].orEmpty().split(',').map { it.trim() }
        val names = metadata["attachmentNames"].orEmpty().split('\n').map { it.trim() }
        return (0 until expected.coerceIn(0, 4)).map { index ->
            ChatAttachment(
                id = ids.getOrNull(index)?.ifBlank { null } ?: "missing-$index",
                name = names.getOrNull(index)?.ifBlank { null } ?: "图片${index + 1}",
            )
        }
    }

    private fun deferAttachmentEnvelope(
        envelope: TextEnvelope,
        metadata: Map<String, String>,
        expected: Int,
        eventAt: Long,
    ) {
        val messageId = envelope.messageId
        if (messageId.isBlank()) {
            applyIncomingEnvelope(envelope, eventAt, emptyList(), false)
            return
        }
        val group = incomingImageGroups.getOrPut(messageId) {
            IncomingImageGroup(expected.coerceIn(1, 4))
        }
        group.expected = maxOf(group.expected, expected.coerceIn(1, 4))
        group.envelope = envelope
        group.metadata = metadata
        group.eventAt = eventAt
        group.sender = envelope.sender
        group.senderId = envelope.senderId
        group.sentAt = envelope.sentAt.takeIf { it > 0L } ?: eventAt
        if (group.attachments.size >= group.expected) {
            flushAttachmentGroup(messageId, group)
        } else {
            scheduleAttachmentGroupTimeout(messageId, 5_000L)
        }
    }

    private fun completeImageAttachment(
        messageId: String,
        index: Int,
        expected: Int,
        attachment: ChatAttachment,
        sender: String,
        senderId: String,
        sentAt: Long,
        expectText: Boolean = false,
    ) {
        val group = incomingImageGroups.getOrPut(messageId) {
            IncomingImageGroup(expected.coerceIn(1, 4))
        }
        group.expected = maxOf(group.expected, expected.coerceIn(1, 4))
        group.expectText = group.expectText || expectText
        group.attachments[index.coerceIn(0, 3)] = attachment
        group.sender = sender
        group.senderId = senderId
        group.sentAt = sentAt
        if (group.attachments.size >= group.expected) {
            if (group.envelope != null) {
                flushAttachmentGroup(messageId, group)
            } else {
                scheduleAttachmentGroupTimeout(
                    messageId,
                    if (group.expectText) 5_000L else 750L,
                )
            }
        } else {
            scheduleAttachmentGroupTimeout(messageId, 5_000L)
        }
    }

    private fun scheduleAttachmentGroupTimeout(messageId: String, delayMs: Long) {
        val group = incomingImageGroups[messageId] ?: return
        group.timer?.cancel()
        group.timer = scope.launch {
            delay(delayMs)
            withContext(Dispatchers.Main) {
                val current = incomingImageGroups[messageId] ?: return@withContext
                flushAttachmentGroup(messageId, current)
            }
        }
    }

    private fun flushAttachmentGroup(messageId: String, group: IncomingImageGroup) {
        group.timer = null
        incomingImageGroups.remove(messageId)
        val attachments = group.attachments.toSortedMap().values.toList()
        val envelope = group.envelope
        if (envelope != null) {
            applyIncomingEnvelope(envelope, group.eventAt, attachments, false)
            tagEnvelopeConversation(
                envelope,
                conversationForIncomingSender(
                    resolveIncomingMemberId(group.sender, group.senderId, group.metadata)
                ),
            )
            return
        }
        if (attachments.isEmpty()) return
        val id = messageId
        val existing = messages.indexOfFirst { it.id == id }
        val message = ChatMessage(
            id = id,
            content = "",
            pic = null,
            conversationId = conversationForIncomingSender(
                resolveIncomingMemberId(group.sender, group.senderId, group.metadata)
            ),
            sender = group.sender,
            senderId = resolveIncomingMemberId(group.sender, group.senderId, group.metadata),
            isMine = isOwnMember(group.senderId),
            sentAt = group.sentAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            attachments = attachments,
        )
        if (existing >= 0) {
            messages[existing] = message.copy(sentAt = messages[existing].sentAt)
            emitEvent(ChatEngineEvent.MessageUpsert(messages[existing]))
        } else {
            insertMessage(message)
            emitNotice(message)
        }
        schedulePersist()
    }

    private fun applyAgentTurn(
        envelope: TextEnvelope,
        turnId: String,
        seq: Int,
        sentAt: Long,
        attachments: List<ChatAttachment> = emptyList(),
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
                        attachments = attachments,
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
                requesterId = normalizeMemberReference(
                    envelope.metadata["requesterId"].orEmpty()
                ),
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
            message.hasAttachments -> message.attachments.first().name
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
        incomingImageGroups.values.forEach { it.timer?.cancel() }
        incomingImageGroups.clear()
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
        if (message.requesterId.isNotEmpty() &&
            message.requesterId != settingsStore.deviceId &&
            message.requesterId != settingsStore.memberId
        ) return
        if (message.status != "pending") return
        val pendingStatus = if (allow) "allow_pending" else "deny_pending"
        messages[index] = message.copy(status = pendingStatus)
        emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
        val command = if (allow) "akid-approve $approvalId" else "akid-deny $approvalId"
        val sentAt = System.currentTimeMillis()
        val envelope = TextEnvelope(
            sender = settingsStore.displayName,
            text = command,
            senderId = settingsStore.memberId,
            avatarHash = ownAvatarHash(),
            messageId = TextEnvelope.messageId(
                settingsStore.memberId,
                sentAt,
                command,
            ),
            sentAt = sentAt,
            metadata = mapOf(
                "memberId" to settingsStore.memberId,
                "memberName" to settingsStore.displayName,
            ),
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

    override fun sendMessage(content: String, attachments: List<OutgoingAttachment>) {
        if (!connected) return
        if (!canSendInCurrentConversation()) return
        if (attachments.isNotEmpty()) {
            sendRichMessage(content, attachments)
            return
        }
        if (content.isBlank()) return
        val utf8 = content.encodeToByteArray()
        if (utf8.size > ChunkedTransfer.TEXT_CHUNK_THRESHOLD_BYTES) {
            sendChunkedText(content, utf8)
            return
        }
        val sentAt = System.currentTimeMillis()
        val senderId = settingsStore.memberId
        val messageId = TextEnvelope.messageId(senderId, sentAt, content)
        val sendConversation = currentConversationId
        val sendMagic = magicForConversation(sendConversation)
        val envelope = TextEnvelope(
            sender = settingsStore.displayName,
            text = content,
            senderId = senderId,
            avatarHash = ownAvatarHash(),
            messageId = messageId,
            sentAt = sentAt,
            metadata = mapOf(
                "memberId" to senderId,
                "memberName" to settingsStore.displayName,
            ),
        ).encode()
        val c = SecureMessageChannel.encrypt(envelope.encodeToByteArray(), sendMagic)
        val msg = Base64.encode(c)
        val size = mqttService.getMaximumPacketSize()
        if (size != null && msg.encodeToByteArray().size > size) {
            addMessage("Error : ${msg.encodeToByteArray().size} is too large.", true)
            return
        }
        addChatText(
            content,
            sender = settingsStore.displayName,
            senderId = senderId,
            isMine = true,
            id = messageId,
            sentAt = sentAt,
            conversationId = sendConversation,
        )
        scope.launch(Dispatchers.IO) {
            try {
                mqttService.publish("tokifactor/text", msg)
            } catch (_: Exception) {
            }
        }
    }

    private fun sendRichMessage(content: String, selected: List<OutgoingAttachment>) {
        if (!connected) return
        if (!canSendInCurrentConversation()) return
        if (selected.isEmpty() || selected.size > 4) {
            addMessage("Error : a message may contain at most 4 images.", true)
            return
        }
        val senderName = settingsStore.displayName
        val senderId = settingsStore.memberId
        val avatarHash = ownAvatarHash()
        val messageId = UUID.randomUUID().toString()
        val sentAt = System.currentTimeMillis()
        val sendConversation = currentConversationId
        val sendMagic = magicForConversation(sendConversation)

        data class Prepared(
            val transferId: String,
            val name: String,
            val mime: String,
            val fileSize: Long,
            val hash: String,
            val payloadFile: File,
            val blobPath: String,
            val total: Int,
        )

        val job = scope.launch(Dispatchers.IO) {
            val prepared = mutableListOf<Prepared>()
            var totalBytes = 0L
            try {
                for ((index, outgoing) in selected.withIndex()) {
                    val transferId = UUID.randomUUID().toString()
                    val payloadFile = ChunkedTransfer.payloadFile(transferId)
                    val (fileSize, hash) = ChunkedTransfer.importSource(outgoing.file, payloadFile)
                    totalBytes += fileSize
                    if (fileSize <= 0L || fileSize > 8L * 1024L * 1024L) {
                        ChunkedTransfer.deleteTransfer(transferId)
                        error("bad-image-size")
                    }
                    if (totalBytes > 16L * 1024L * 1024L) {
                        ChunkedTransfer.deleteTransfer(transferId)
                        error("image-total-too-large")
                    }
                    val name = outgoing.name.trim().ifBlank { "图片${index + 1}" }.take(64)
                    val chunkSize = ChunkedTransfer.chunkSize(mqttService.getMaximumPacketSize())
                    val total = ChunkedTransfer.chunkCount(fileSize, chunkSize)
                    val blobPath = ChatHistoryStore.storeAttachmentBlobFromFile(
                        messageId = messageId,
                        index = index,
                        attachmentId = transferId,
                        source = payloadFile,
                    )
                    prepared += Prepared(
                        transferId = transferId,
                        name = name,
                        mime = imageMimeForName(name),
                        fileSize = fileSize,
                        hash = hash,
                        payloadFile = payloadFile,
                        blobPath = blobPath,
                        total = total,
                    )
                }
                withContext(Dispatchers.Main) {
                    insertMessage(
                        ChatMessage(
                            id = messageId,
                            content = content,
                            pic = null,
                            conversationId = sendConversation,
                            sender = senderName,
                            senderId = senderId,
                            isMine = true,
                            sentAt = sentAt,
                            attachments = prepared.map {
                                ChatAttachment(
                                    id = it.transferId,
                                    name = it.name,
                                    mime = it.mime,
                                    filePath = it.blobPath,
                                    size = it.fileSize,
                                    isTransferring = true,
                                    chunksReceived = 0,
                                    chunksTotal = it.total,
                                )
                            },
                        )
                    )
                }
                prepared.forEachIndexed { index, attachment ->
                    val chunkSize = ChunkedTransfer.chunkSize(mqttService.getMaximumPacketSize())
                    val maxPacket = mqttService.getMaximumPacketSize()
                    publishSlices(
                        transferId = attachment.transferId,
                        payloadFile = attachment.payloadFile,
                        fileSize = attachment.fileSize,
                        chunkSize = chunkSize,
                        total = attachment.total,
                        maxPacket = maxPacket,
                        topic = "tokifactor/picture",
                        magic = sendMagic,
                        encodeChunk = { chunkIndex, encryptedB64 ->
                            PictureChunk(
                                transferId = attachment.transferId,
                                index = chunkIndex,
                                total = attachment.total,
                                hash = attachment.hash,
                                encryptedData = encryptedB64,
                                sender = senderName,
                                senderId = senderId,
                                avatarHash = avatarHash,
                                messageId = messageId,
                                attachmentIndex = index,
                                attachmentCount = prepared.size,
                                textCommit = content.isNotBlank(),
                                sentAt = sentAt,
                                size = attachment.fileSize,
                                captionEncrypted = encryptCaption(attachment.name, sendMagic),
                            ).encode()
                        },
                        onProgress = { completed, total ->
                            updateAttachmentProgress(messageId, attachment.transferId, completed, total)
                        },
                    )
                    withContext(Dispatchers.Main) {
                        updateMessage(messageId) { message ->
                            message.copy(
                                attachments = message.attachments.map { item ->
                                    if (item.id == attachment.transferId) {
                                        item.copy(
                                            filePath = attachment.blobPath,
                                            isTransferring = false,
                                            chunksReceived = attachment.total,
                                            chunksTotal = attachment.total,
                                        )
                                    } else item
                                }
                            )
                        }
                    }
                    ChunkedTransfer.deleteTransfer(attachment.transferId)
                }
                if (content.isNotBlank()) {
                    publishRichText(
                        content,
                        messageId,
                        sentAt,
                        prepared.map {
                            ChatAttachment(
                                id = it.transferId,
                                name = it.name,
                                mime = it.mime,
                                filePath = it.blobPath,
                                size = it.fileSize,
                            )
                        },
                        sendMagic,
                    )
                }
                withContext(Dispatchers.Main) {
                    updateMessage(messageId) { message ->
                        message.copy(attachments = message.attachments.map { it.copy(isTransferring = false) })
                    }
                }
                schedulePersist()
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    prepared.forEach { attachment ->
                        publishAbort(
                            TransferKind.Image,
                            attachment.transferId,
                            senderName,
                            senderId,
                            "cancel",
                        )
                        ChunkedTransfer.deleteTransfer(attachment.transferId)
                    }
                    ChatHistoryStore.deleteBlob(messageId)
                    withContext(Dispatchers.Main) {
                        replaceMessage(
                            messageId,
                            ChatMessage(
                                id = messageId,
                                content = "Cancelled sending image message.",
                                pic = null,
                                isSystem = true,
                            ),
                        )
                    }
                }
                throw e
            } catch (t: Throwable) {
                prepared.forEach { attachment ->
                    publishAbort(
                        TransferKind.Image,
                        attachment.transferId,
                        senderName,
                        senderId,
                        "fail",
                    )
                    ChunkedTransfer.deleteTransfer(attachment.transferId)
                }
                ChatHistoryStore.deleteBlob(messageId)
                withContext(Dispatchers.Main) {
                    replaceMessage(
                        messageId,
                        ChatMessage(
                            id = messageId,
                            content = when (t.message) {
                                "bad-image-size" -> "Error : each image must be between 1 byte and 8 MiB."
                                "image-total-too-large" -> "Error : images exceed the 16 MiB message limit."
                                else -> "Error : failed to send image message."
                            },
                            pic = null,
                            isError = true,
                            isSystem = true,
                        ),
                    )
                }
            } finally {
                outgoingJobs.remove(messageId)
            }
        }
        outgoingJobs[messageId] = job
    }

    private suspend fun publishRichText(
        content: String,
        messageId: String,
        sentAt: Long,
        attachments: List<ChatAttachment>,
        magic: String,
    ) {
        val metadata = attachmentMetadata(attachments)
        val utf8 = content.encodeToByteArray()
        if (utf8.size <= ChunkedTransfer.TEXT_CHUNK_THRESHOLD_BYTES) {
            val envelope = TextEnvelope(
                sender = settingsStore.displayName,
                text = content,
                senderId = settingsStore.memberId,
                avatarHash = ownAvatarHash(),
                messageId = messageId,
                sentAt = sentAt,
                metadata = metadata,
            ).encode()
            val encrypted = SecureMessageChannel.encrypt(
                envelope.encodeToByteArray(), magic,
            )
            mqttService.publish("tokifactor/text", Base64.encode(encrypted))
            return
        }
        val payloadFile = ChunkedTransfer.payloadFile(messageId)
        val (fileSize, hash) = ChunkedTransfer.importBytes(utf8, payloadFile)
        val chunkSize = ChunkedTransfer.chunkSize(mqttService.getMaximumPacketSize())
        val total = ChunkedTransfer.chunkCount(fileSize, chunkSize)
        publishSlices(
            transferId = messageId,
            payloadFile = payloadFile,
            fileSize = fileSize,
            chunkSize = chunkSize,
            total = total,
            maxPacket = mqttService.getMaximumPacketSize(),
            topic = "tokifactor/text",
            magic = magic,
            encodeChunk = { index, encryptedB64 ->
                TextChunk(
                    transferId = messageId,
                    index = index,
                    total = total,
                    hash = hash,
                    encryptedData = encryptedB64,
                    sender = settingsStore.displayName,
                    senderId = settingsStore.memberId,
                    avatarHash = ownAvatarHash(),
                    messageId = messageId,
                    sentAt = sentAt,
                    size = fileSize,
                    metadata = metadata + mapOf(
                        "memberId" to settingsStore.memberId,
                        "memberName" to settingsStore.displayName,
                    ),
                ).encode()
            },
            onProgress = { _, _ -> },
        )
        ChunkedTransfer.deleteTransfer(messageId)
    }

    private fun attachmentMetadata(attachments: List<ChatAttachment>): Map<String, String> {
        val values = attachments
        if (values.isEmpty()) return emptyMap()
        val json = values.joinToString(prefix = "[", postfix = "]") {
            WireJson.encodeObject(
                "id" to it.id,
                "name" to it.name,
                "mime" to it.mime,
                "bytes" to it.size.toString(),
            )
        }
        return mapOf(
            "attachmentCount" to values.size.toString(),
            "attachmentIds" to values.joinToString(",") { it.id },
            "attachmentNames" to values.joinToString("\n") { it.name },
            "attachments" to json,
            "memberId" to settingsStore.memberId,
            "memberName" to settingsStore.displayName,
        )
    }

    private fun updateAttachmentProgress(
        messageId: String,
        attachmentId: String,
        completed: Int,
        total: Int,
    ) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val message = messages[index]
        messages[index] = message.copy(
            attachments = message.attachments.map { attachment ->
                if (attachment.id == attachmentId) {
                    attachment.copy(chunksReceived = completed, chunksTotal = total)
                } else attachment
            }
        )
        emitEvent(ChatEngineEvent.MessageUpsert(messages[index]))
    }

    private fun encryptCaption(caption: String, magic: String): String =
        Base64.encode(
            SecureMessageChannel.encrypt(
                caption.encodeToByteArray(),
                magic,
            )
        )

    private fun imageMimeForName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    private fun sendChunkedText(content: String, utf8: ByteArray) {
        val senderName = settingsStore.displayName
        val senderId = settingsStore.memberId
        val avatarHash = ownAvatarHash()
        val sentAt = System.currentTimeMillis()
        val transferId = TextEnvelope.messageId(senderId, sentAt, content)
        val sendConversation = currentConversationId
        val sendMagic = magicForConversation(sendConversation)
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
                            conversationId = sendConversation,
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
                    magic = sendMagic,
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
                        metadata = mapOf(
                            "memberId" to senderId,
                            "memberName" to senderName,
                        ),
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
        sendMessage("", listOf(OutgoingAttachment(file, file.name)))
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
        if (!canSendInCurrentConversation()) return
        val senderName = settingsStore.displayName
        val senderId = settingsStore.memberId
        val avatarHash = ownAvatarHash()
        var sendAsFile = asFile
        var sendTopic = if (asFile) "tokifactor/file" else "tokifactor/picture"
        val sentAt = System.currentTimeMillis()
        val transferId = UUID.randomUUID().toString()
        val sendConversation = currentConversationId
        val sendMagic = magicForConversation(sendConversation)
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
                            conversationId = sendConversation,
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
                    magic = sendMagic,
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
        magic: String,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
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
                        val cipher = SecureMessageChannel.encrypt(plain, magic)
                        val payload = encodeChunk(index, Base64.encode(cipher))
                        val wireSize = payload.encodeToByteArray().size
                        if (maxPacket != null && wireSize > maxPacket) {
                            error("too-large:$wireSize")
                        }
                        mqttService.publish(topic, payload)
                        val n = completed.incrementAndGet()
                        if (onProgress != null) {
                            withContext(Dispatchers.Main) { onProgress(n, total) }
                        } else {
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
            messageId = chunk.messageId.ifBlank { chunk.transferId },
            attachmentIndex = chunk.attachmentIndex,
            attachmentCount = chunk.attachmentCount,
            textCommit = chunk.textCommit,
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
        attachmentIndex: Int = 0,
        attachmentCount: Int = 1,
        textCommit: Boolean = false,
    ) {
        val messageAt = sentAt.takeIf { it > 0L } ?: receivedAt
        val memberId = resolveIncomingMemberId(sender, senderId, metadata)
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
                notePeerAvatar(memberId, avatarHash)
            }
        }
        val cipher = try {
            Base64.decode(encryptedData)
        } catch (_: Exception) {
            return
        }
        var clear: ByteArray? = null
        var transferConversation = GROUP_CONVERSATION
        for (magic in activeMagicCandidates()) {
            try {
                clear = SecureMessageChannel.decrypt(cipher, magic)
                transferConversation = conversationForMagic(magic)
                break
            } catch (_: Exception) {
            }
        }
        if (clear == null) return
        transferConversations[transferId] = transferConversation

        try {
            ChunkedTransfer.writePart(transferId, index, clear)
        } catch (_: Exception) {
            return
        }

        val complete = withContext(Dispatchers.Main) {
            if (!isChunkedText && fileName == null && messageId.isNotBlank()) {
                noteIncomingAttachmentChunk(
                    transferId = transferId,
                    index = index,
                    total = total,
                    hash = hash,
                    sender = sender,
                    senderId = memberId,
                    sentAt = messageAt,
                    caption = caption,
                )
            } else {
                noteIncomingChunk(
                    transferId = transferId,
                    index = index,
                    total = total,
                    hash = hash,
                    sender = sender,
                    senderId = memberId,
                    fileName = fileName,
                    fileSize = fileSize,
                    isChunkedText = isChunkedText,
                    sentAt = messageAt,
                    caption = caption,
                )
            }
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
                        val envelope = TextEnvelope(
                                sender = sender,
                                text = text,
                                senderId = memberId,
                                avatarHash = avatarHash,
                                messageId = messageId,
                                sentAt = sentAt,
                                metadata = metadata,
                            )
                        applyIncomingEnvelope(
                            envelope,
                            receivedAt,
                        )
                        tagEnvelopeConversation(
                            envelope,
                            transferConversations[transferId] ?: GROUP_CONVERSATION,
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
                                conversationId = transferConversations[transferId]
                                    ?: GROUP_CONVERSATION,
                                isMine = isOwnMember(memberId),
                            )
                        }
                        messages.firstOrNull { it.id == transferId }?.let { emitNotice(it) }
                    }
                }
                completedTransfers.add(transferId)
                return
            }
            val asFile = fileName != null || size > ChunkedTransfer.PREVIEW_MAX_BYTES
            if (!isChunkedText && fileName == null && messageId.isNotBlank()) {
                val clear = dest.readBytes()
                val blobPath = ChatHistoryStore.storeAttachmentBlobFromFile(
                    messageId = messageId,
                    index = attachmentIndex,
                    attachmentId = transferId,
                    source = dest,
                )
                ChunkedTransfer.deleteTransfer(transferId)
                val name = caption.ifBlank { "图片${attachmentIndex + 1}" }
                val attachment = ChatAttachment(
                    id = transferId,
                    name = name,
                    mime = imageMime(clear, name),
                    filePath = blobPath,
                    size = size,
                )
                withContext(Dispatchers.Main) {
                    completeImageAttachment(
                        messageId = messageId,
                        index = attachmentIndex,
                        expected = attachmentCount,
                        attachment = attachment,
                        sender = sender,
                        senderId = memberId,
                        sentAt = messageAt,
                        expectText = textCommit,
                    )
                }
                completedTransfers.add(transferId)
                return
            }
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
                        isMine = isOwnMember(memberId),
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
                    isMine = isOwnMember(state.senderId),
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

    private fun noteIncomingAttachmentChunk(
        transferId: String,
        index: Int,
        total: Int,
        hash: String,
        sender: String,
        senderId: String,
        sentAt: Long,
        caption: String,
    ): Boolean {
        if (transferId in abortedTransfers) return false
        val transfer = incoming[transferId]
        if (transfer != null && (transfer.hash != hash || transfer.total != total)) {
            return false
        }
        val state = transfer ?: IncomingBinary(
            hash = hash,
            total = total,
            sender = sender,
            senderId = senderId,
            fileName = null,
            fileSize = 0,
            sentAt = sentAt,
            received = BooleanArray(total),
            caption = caption,
        ).also { incoming[transferId] = it }
        if (caption.isNotBlank() && state.caption.isBlank()) state.caption = caption
        if (index < 0 || index >= state.received.size) return false
        if (!state.received[index]) {
            state.received[index] = true
            state.count++
        }
        bumpIncomingWatchdog(transferId)
        if (state.count < total) return false
        cancelIncomingWatchdog(transferId)
        incoming.remove(transferId)
        return true
    }

    private fun imageMime(bytes: ByteArray, name: String = ""): String {
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "image/png"
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
        if (bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        ) return "image/gif"
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) return "image/webp"
        return imageMimeForName(name)
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
        if (senderId.isEmpty() || senderId == settingsStore.memberId) return
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
            }.filter {
                    it.isNotEmpty() && it != settingsStore.memberId
            }
        }
        for (id in ids) {
            avatarRefreshRequested.add(id)
            val hash = withContext(Dispatchers.Main.immediate) { peerAvatarHash[id] ?: "" }
            publishAvatarWant(id, hash)
        }
    }

    private fun schedulePeerAvatarRefresh(memberId: String) {
        if (memberId.isEmpty() || memberId == settingsStore.memberId) return
        if (!avatarRefreshRequested.add(memberId)) return
        val hash = peerAvatarHash[memberId] ?: ""
        scope.launch(Dispatchers.IO) {
            publishAvatarWant(memberId, hash)
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
        val payload = AvatarEnvelope.have(
            settingsStore.memberId,
            hash,
            data,
            settingsStore.deviceId,
        ).encode()
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
        val ownKey = settingsStore.memberId
        when (envelope.type) {
            AvatarEnvelope.WANT -> {
                if (envelope.id == ownKey) {
                    publishAvatarHave(force = true)
                }
            }
            AvatarEnvelope.HAVE -> {
                if (envelope.id == ownKey) return
                applyPeerAvatar(envelope)
            }
        }
    }

    private suspend fun applyPeerAvatar(envelope: AvatarEnvelope) {
        val source = envelope.source.ifBlank { envelope.id }
        val owner = withContext(Dispatchers.Main) {
            avatarOwners.getOrPut(envelope.id) { source }
        }
        if (source != owner) return
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
        val cipher = try {
            Base64.decode(envelope.data)
        } catch (_: Exception) {
            return
        }
        var clear: ByteArray? = null
        for (magic in activeMagicCandidates()) {
            try {
                clear = SecureMessageChannel.decrypt(cipher, magic)
                break
            } catch (_: Exception) {
            }
        }
        if (clear == null) return
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
