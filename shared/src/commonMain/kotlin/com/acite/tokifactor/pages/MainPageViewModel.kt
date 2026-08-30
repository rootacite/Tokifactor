package com.acite.tokifactor.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acite.tokifactor.copyFileToPlatformFile
import com.acite.tokifactor.model.AvatarEnvelope
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.FileChunk
import com.acite.tokifactor.model.MqttBrokers
import com.acite.tokifactor.model.PictureChunk
import com.acite.tokifactor.model.TextEnvelope
import com.acite.tokifactor.services.MqttService
import com.acite.tokifactor.services.PictureTransfer
import com.acite.tokifactor.services.SecureMessageChannel
import com.acite.tokifactor.services.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class MainPageViewModel(
    private val mqttService: MqttService,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val messages: List<ChatMessage>
        field = mutableStateListOf()

    var avatarBytes by mutableStateOf(settingsStore.avatarBytes)
        private set

    var connected by mutableStateOf(false)
        private set

    val peerAvatars = mutableStateMapOf<String, ByteArray>()
    private val peerAvatarHash = mutableMapOf<String, String>()
    private val pendingWantAt = mutableMapOf<String, Long>()
    private var lastPublishedAvatarHash: String? = null

    private var idCounter = 0

    private class IncomingBinary(
        val hash: String,
        val total: Int,
        val sender: String,
        val senderId: String,
        val fileName: String?,
        val fileSize: Long,
        val received: BooleanArray,
        var count: Int = 0,
    )

    private val incoming = mutableMapOf<String, IncomingBinary>()
    private val incomingWatchdogs = mutableMapOf<String, Job>()
    private val abortedTransfers = mutableSetOf<String>()
    private val outgoingJobs = mutableMapOf<String, Job>()

    private var mqttSessionJob: Job? = null
    private var sessionBrokerId: String? = null

    init {
        addMessage("Welcome to TokiFactor.")
        startMqttSession()
    }

    fun refreshSettings() {
        avatarBytes = settingsStore.avatarBytes
        if (settingsStore.mqttBrokerId != sessionBrokerId) {
            startMqttSession()
        } else if (connected) {
            viewModelScope.launch(Dispatchers.IO) {
                publishAvatarHave()
            }
        }
    }

    private fun startMqttSession() {
        val brokerId = settingsStore.mqttBrokerId
        sessionBrokerId = brokerId
        mqttSessionJob?.cancel()
        mqttSessionJob = viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { connected = false }
            try {
                mqttService.disconnect()
            } catch (_: Exception) {
            }
            val broker = MqttBrokers.byId(brokerId)
            val s = mqttService.connect(broker)
            if (!isActive) {
                try {
                    mqttService.disconnect()
                } catch (_: Exception) {
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                connected = s
                addMessage(
                    if (s) {
                        "Connected to ${broker.name} (${broker.endpoint} ${broker.transportLabel}), maxSize: ${mqttService.getMaximumPacketSize()}"
                    } else {
                        "Connection failed: ${broker.name} (${broker.endpoint})"
                    }
                )
            }
            if (!s) return@launch

            coroutineScope {
                launch {
                    mqttService.subscribe("tokifactor/text")
                        .collect { message ->
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
                                } catch (_: Exception) {}
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
                }
            }
        }
    }

    fun addMessage(content: String, isError: Boolean = false) {
        messages.add(
            ChatMessage(
                id = (idCounter++).toString(),
                content = content,
                pic = null,
                isError = isError,
                isSystem = true,
            )
        )
    }

    private fun addChatText(
        content: String,
        sender: String,
        isMine: Boolean,
        senderId: String = "",
    ) {
        messages.add(
            ChatMessage(
                id = (idCounter++).toString(),
                content = content,
                pic = null,
                sender = sender,
                senderId = senderId,
                isMine = isMine,
            )
        )
    }

    fun removeMessage(id: String) {
        messages.removeAll { it.id == id }
        incoming.remove(id)
        PictureTransfer.deleteTransfer(id)
    }

    fun saveMessageFile(message: ChatMessage, dest: PlatformFile) {
        val path = message.filePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mqttService.publish("tokifactor/text", msg)
            } catch (_: Exception) {
            }
        }
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
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val payloadFile = PictureTransfer.payloadFile(transferId)
                val (fileSize, hash) = PictureTransfer.importSource(file, payloadFile)
                val sourceName = FileChunk.sanitizeFileName(file.name)
                val uiAsFile = asFile || fileSize > PictureTransfer.PREVIEW_MAX_BYTES
                val fileName = if (uiAsFile) sourceName else null
                val preview = if (!uiAsFile) {
                    payloadFile.readBytes()
                } else {
                    null
                }
                sendAsFile = uiAsFile
                sendTopic = if (sendAsFile) "tokifactor/file" else "tokifactor/picture"
                val chunkSize = PictureTransfer.chunkSize(mqttService.getMaximumPacketSize())
                val total = PictureTransfer.chunkCount(fileSize, chunkSize)
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

                val completed = AtomicInteger(0)
                val queue = Channel<Int>(Channel.UNLIMITED)
                for (index in 0 until total) {
                    queue.send(index)
                }
                queue.close()
                coroutineScope {
                    repeat(PictureTransfer.MAX_IN_FLIGHT_CHUNKS) {
                        launch {
                            for (index in queue) {
                                val plain = PictureTransfer.readSlice(payloadFile, index, chunkSize, fileSize)
                                val cipher = SecureMessageChannel.encrypt(plain, settingsStore.magicString)
                                val payload = if (sendAsFile) {
                                    FileChunk(
                                        transferId = transferId,
                                        index = index,
                                        total = total,
                                        hash = hash,
                                        encryptedData = Base64.encode(cipher),
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
                                        encryptedData = Base64.encode(cipher),
                                        sender = senderName,
                                        senderId = senderId,
                                        avatarHash = avatarHash,
                                        size = fileSize,
                                    ).encode()
                                }
                                val wireSize = payload.encodeToByteArray().size
                                if (maxPacket != null && wireSize > maxPacket) {
                                    error("too-large:$wireSize")
                                }
                                mqttService.publish(sendTopic, payload)
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
                withContext(Dispatchers.Main) {
                    updateMessage(transferId) { it.copy(chunksReceived = 0, chunksTotal = 0) }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    publishBinaryAbort(sendAsFile, transferId, senderName, senderId, "cancel")
                    PictureTransfer.deleteTransfer(transferId)
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
                publishBinaryAbort(sendAsFile, transferId, senderName, senderId, "fail")
                PictureTransfer.deleteTransfer(transferId)
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

    private suspend fun publishBinaryAbort(
        asFile: Boolean,
        transferId: String,
        sender: String,
        senderId: String,
        reason: String,
    ) {
        val payload = if (asFile) {
            FileChunk.abort(transferId, sender, senderId, reason).encode()
        } else {
            PictureChunk.abort(transferId, sender, senderId, reason).encode()
        }
        for (topic in listOf("tokifactor/file", "tokifactor/picture")) {
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
            PictureTransfer.writePart(transferId, index, clear)
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
            )
        }
        if (!complete) return

        try {
            val dest = PictureTransfer.payloadFile(transferId)
            PictureTransfer.concatParts(transferId, total, dest)
            val actualHash = PictureTransfer.sha256File(dest)
            if (!actualHash.equals(hash, ignoreCase = true)) {
                withContext(Dispatchers.Main) {
                    abortedTransfers.add(transferId)
                    incoming.remove(transferId)
                    PictureTransfer.deleteTransfer(transferId)
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
            val asFile = fileName != null || size > PictureTransfer.PREVIEW_MAX_BYTES
            val preview = if (!asFile) dest.readBytes() else null
            val displayName = fileName ?: if (asFile) "image.bin" else null
            withContext(Dispatchers.Main) {
                updateMessage(transferId) {
                    it.copy(
                        pic = preview,
                        fileBytes = null,
                        fileName = displayName,
                        filePath = dest.absolutePath,
                        fileSize = size,
                        chunksReceived = 0,
                        chunksTotal = 0,
                    )
                }
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
    ): Boolean {
        if (transferId in abortedTransfers) return false
        val existingMsg = messages.indexOfFirst { it.id == transferId }
        val transfer = incoming[transferId]
        if (transfer != null && existingMsg < 0) {
            cancelIncomingWatchdog(transferId)
            incoming.remove(transferId)
            abortedTransfers.add(transferId)
            PictureTransfer.deleteTransfer(transferId)
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
        incomingWatchdogs[transferId] = viewModelScope.launch {
            delay(PictureTransfer.INCOMPLETE_TIMEOUT_MS)
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
        PictureTransfer.deleteTransfer(transferId)
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
        return PictureTransfer.sha256Hex(bytes)
    }

    private fun notePeerAvatar(senderId: String, hash: String) {
        if (senderId.isEmpty() || senderId == settingsStore.deviceId) return
        if (hash.isEmpty()) {
            peerAvatars.remove(senderId)
            peerAvatarHash.remove(senderId)
            pendingWantAt.remove(senderId)
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mqttService.publish(
                    "tokifactor/avatar",
                    AvatarEnvelope.want(senderId, hash).encode(),
                )
            } catch (_: Exception) {
            }
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
        val actual = PictureTransfer.sha256Hex(clear)
        if (!actual.equals(envelope.hash, ignoreCase = true)) return
        withContext(Dispatchers.Main) {
            peerAvatars[envelope.id] = clear
            peerAvatarHash[envelope.id] = envelope.hash
            pendingWantAt.remove(envelope.id)
        }
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = transform(messages[i])
    }

    private fun replaceMessage(id: String, message: ChatMessage) {
        val i = messages.indexOfFirst { it.id == id }
        if (i >= 0) messages[i] = message else messages.add(message)
    }
}
