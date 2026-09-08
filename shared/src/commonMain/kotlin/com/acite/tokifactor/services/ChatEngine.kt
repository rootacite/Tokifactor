package com.acite.tokifactor.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.acite.tokifactor.copyFileToPlatformFile
import com.acite.tokifactor.model.AvatarEnvelope
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.FileChunk
import com.acite.tokifactor.model.InboundNotice
import com.acite.tokifactor.model.MqttBrokers
import com.acite.tokifactor.model.PictureChunk
import com.acite.tokifactor.model.TextChunk
import com.acite.tokifactor.model.TextEnvelope
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64

@SingleIn(AppScope::class)
class ChatEngine @Inject constructor(
    private val mqttService: MqttService,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val historyReady = AtomicBoolean(false)

    val messages: List<ChatMessage>
        field = mutableStateListOf()

    var avatarBytes by mutableStateOf(settingsStore.avatarBytes)
        private set

    var connected by mutableStateOf(false)
        private set

    private val _connectionStatus = MutableStateFlow("Connecting…")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _inboundNotices = MutableSharedFlow<InboundNotice>(extraBufferCapacity = 32)
    val inboundNotices: SharedFlow<InboundNotice> = _inboundNotices.asSharedFlow()

    val peerAvatars = mutableStateMapOf<String, ByteArray>()
    private val peerAvatarHash = mutableMapOf<String, String>()
    private val pendingWantAt = mutableMapOf<String, Long>()
    private var lastPublishedAvatarHash: String? = null



    private class IncomingBinary(
        val hash: String,
        val total: Int,
        val sender: String,
        val senderId: String,
        val fileName: String?,
        val fileSize: Long,
        val received: BooleanArray,
        val isChunkedText: Boolean = false,
        var count: Int = 0,
    )

    private val incoming = mutableMapOf<String, IncomingBinary>()
    private val incomingWatchdogs = mutableMapOf<String, Job>()
    private val abortedTransfers = mutableSetOf<String>()
    private val outgoingJobs = mutableMapOf<String, Job>()

    private var mqttSessionJob: Job? = null
    private var sessionBrokerId: String? = null
    private var persistJob: Job? = null
    private val persistGeneration = AtomicInteger(0)

    fun start() {
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
                }
                if (loaded.isNotEmpty()) {
                    val existing = messages.map { it.id }.toHashSet()
                    val restored = loaded.filter { it.id !in existing }
                    if (restored.isNotEmpty()) {
                        messages.addAll(0, restored)
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

    fun refreshSettings() {
        avatarBytes = settingsStore.avatarBytes
        if (settingsStore.mqttBrokerId != sessionBrokerId) {
            startMqttSession()
        } else if (connected) {
            scope.launch {
                publishAvatarHave()
            }
        }
    }

    private fun setConnected(value: Boolean, status: String) {
        connected = value
        _connectionStatus.value = status
    }

    private fun startMqttSession() {
        val wantedBrokerId = settingsStore.mqttBrokerId
        sessionBrokerId = wantedBrokerId
        mqttSessionJob?.cancel()
        mqttSessionJob = scope.launch {
            var backoffMs = 1_000L
            var announcedFailure = false
            while (isActive) {
                val brokerId = settingsStore.mqttBrokerId
                sessionBrokerId = brokerId
                val broker = MqttBrokers.byId(brokerId)
                withContext(Dispatchers.Main) {
                    setConnected(false, if (announcedFailure) "Reconnecting…" else "Connecting…")
                }
                try {
                    mqttService.disconnect()
                } catch (_: Exception) {
                }
                val s = try {
                    mqttService.connect(broker)
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
                                }
                            }
                        }
                launch {
                    mqttService.subscribe("tokifactor/text")
                        .collect { message ->
                            try {
                                if (message.payload.trimStart().startsWith("{")) {
                                    val chunk = TextChunk.decode(message.payload) ?: return@collect
                                    handleIncomingText(chunk)
                                } else {
                                    withContext(Dispatchers.Main) {
                                        try {
                                            val chip = Base64.decode(message.payload)
                                            val clear = SecureMessageChannel.decrypt(chip, settingsStore.magicString).decodeToString()
                                            val envelope = TextEnvelope.decode(clear)
                                            if (envelope != null) {
                                                addChatText(
                                                    envelope.text,
                                                    sender = envelope.sender,
                                                    senderId = envelope.senderId,
                                                    isMine = false,
                                                )
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
                                handleIncomingPicture(chunk)
                            } catch (_: Exception) {}
                        }
                }
                launch {
                    mqttService.subscribe("tokifactor/file")
                        .collect { message ->
                            try {
                                val chunk = FileChunk.decode(message.payload) ?: return@collect
                                handleIncomingFile(chunk)
                            } catch (_: Exception) {}
                        }
                }
                launch {
                    mqttService.subscribe("tokifactor/avatar")
                        .collect { message ->
                            try {
                                handleAvatarEnvelope(message.payload)
                            } catch (_: Exception) {}
                        }
                }
                        launch {
                            publishAvatarHave(force = true)
                            requestMissingPeerAvatars()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
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

    fun addMessage(content: String, isError: Boolean = false) {
        messages.add(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                content = content,
                pic = null,
                isError = isError,
                isSystem = true,
            )
        )
        schedulePersist()
    }

    private fun addChatText(
        content: String,
        sender: String,
        isMine: Boolean,
        senderId: String = "",
    ) {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            pic = null,
            sender = sender,
            senderId = senderId,
            isMine = isMine,
        )
        messages.add(message)
        emitNotice(message)
        schedulePersist()
    }

    private fun emitNotice(message: ChatMessage) {
        if (message.isMine || message.isSystem || message.isError) return
        val preview = when {
            message.isFile -> message.fileName ?: "File"
            message.isImage -> "Photo"
            else -> message.content.trim().replace(Regex("\\s+"), " ").take(80).ifBlank { "Message" }
        }
        _inboundNotices.tryEmit(
            InboundNotice(
                id = message.id,
                sender = message.sender.ifBlank { "Unknown" },
                preview = preview,
            )
        )
    }

    fun removeMessage(id: String) {
        deleteMessages(listOf(id))
    }

    fun deleteMessages(ids: Collection<String>) {
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
        schedulePersist()
    }

    fun clearHistory() {
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
    }

    fun saveMessageFile(message: ChatMessage, dest: PlatformFile) {
        val path = message.filePath ?: return
        scope.launch(Dispatchers.IO) {
            try {
                copyFileToPlatformFile(File(path), dest)
            } catch (_: Exception) {
            }
        }
    }

    fun cancelOutgoing(transferId: String) {
        outgoingJobs[transferId]?.cancel()
    }

    fun sendMessage(content: String) {
        if (!connected) return
        val utf8 = content.encodeToByteArray()
        if (utf8.size > ChunkedTransfer.TEXT_CHUNK_THRESHOLD_BYTES) {
            sendChunkedText(content, utf8)
            return
        }
        val envelope = TextEnvelope(
            sender = settingsStore.displayName,
            text = content,
            senderId = settingsStore.deviceId,
            avatarHash = ownAvatarHash(),
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
        val transferId = UUID.randomUUID().toString()
        val job = scope.launch(Dispatchers.IO) {
            try {
                val payloadFile = ChunkedTransfer.payloadFile(transferId)
                val (fileSize, hash) = ChunkedTransfer.importBytes(utf8, payloadFile)
                val chunkSize = ChunkedTransfer.chunkSize(mqttService.getMaximumPacketSize())
                val total = ChunkedTransfer.chunkCount(fileSize, chunkSize)
                val maxPacket = mqttService.getMaximumPacketSize()

                withContext(Dispatchers.Main) {
                    messages.add(
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

    fun sendPicture(file: PlatformFile) {
        sendBinary(
            file = file,
            asFile = false,
            failLabel = "image",
        )
    }

    fun sendFile(file: PlatformFile) {
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
                    messages.add(
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

    private suspend fun handleIncomingPicture(chunk: PictureChunk) {
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
        )
    }

    private suspend fun handleIncomingFile(chunk: FileChunk) {
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
        )
    }

    private suspend fun handleIncomingText(chunk: TextChunk) {
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
    ) {
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
    ): Boolean {
        if (transferId in abortedTransfers) return false
        val existingMsg = messages.indexOfFirst { it.id == transferId }
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
            received = BooleanArray(total),
            isChunkedText = isChunkedText,
        ).also { incoming[transferId] = it }

        if (index < 0 || index >= state.received.size) return false
        val isNew = !state.received[index]
        if (isNew) {
            state.received[index] = true
            state.count++
        }
        bumpIncomingWatchdog(transferId)
        val displayName = state.fileName

        if (existingMsg < 0) {
            messages.add(
                ChatMessage(
                    id = transferId,
                    content = displayName ?: "",
                    pic = null,
                    sender = state.sender,
                    senderId = state.senderId,
                    isMine = false,
                    chunksReceived = state.count,
                    chunksTotal = total,
                    fileName = displayName,
                    fileSize = state.fileSize,
                    isChunkedText = state.isChunkedText,
                )
            )
        } else {
            messages[existingMsg] = messages[existingMsg].copy(
                chunksReceived = state.count,
                chunksTotal = total,
                fileSize = state.fileSize,
            )
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

    private suspend fun requestMissingPeerAvatars() {
        val missing = withContext(Dispatchers.Main.immediate) {
            messages
                .map { it.senderId }
                .filter { it.isNotEmpty() && it != settingsStore.deviceId && peerAvatars[it] == null }
                .distinct()
        }
        for (id in missing) {
            val hash = withContext(Dispatchers.Main.immediate) { peerAvatarHash[id] ?: "" }
            publishAvatarWant(id, hash)
        }
    }

    private suspend fun publishAvatarWant(senderId: String, hash: String) {
        try {
            mqttService.publish(
                "tokifactor/avatar",
                AvatarEnvelope.want(senderId, hash).encode(),
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
            mqttService.publish("tokifactor/avatar", payload)
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
        }
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) {
            messages[i] = transform(messages[i])
            if (!messages[i].isTransferring) schedulePersist()
        }
    }

    private fun replaceMessage(id: String, message: ChatMessage) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = message else messages.add(message)
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
