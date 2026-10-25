package com.acite.tokifactor.services

import com.acite.tokifactor.appHistoryBlobsDir
import com.acite.tokifactor.appHistoryDir
import com.acite.tokifactor.appHistoryIndexFile
import com.acite.tokifactor.model.ChatAttachment
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.WireJson
import java.io.File

object ChatHistoryStore {
    @Synchronized
    fun load(): List<ChatMessage> {
        val index = appHistoryIndexFile()
        if (!index.exists()) return emptyList()
        val lines = try {
            index.readLines()
        } catch (_: Exception) {
            return emptyList()
        }
        val out = ArrayList<ChatMessage>(lines.size)
        for (line in lines) {
            val raw = line.trim()
            if (raw.isEmpty()) continue
            val message = decode(raw) ?: continue
            out.add(message)
        }
        return out
    }

    @Synchronized
    fun save(messages: List<ChatMessage>) {
        val dir = appHistoryDir()
        dir.mkdirs()
        val blobs = appHistoryBlobsDir()
        blobs.mkdirs()
        val keep = mutableSetOf<String>()
        val body = buildString {
            for (message in messages) {
                if (!message.shouldPersist) continue
                val blobPath = ensureBlob(message)
                keep.add(blobName(message.id))
                message.attachments.forEachIndexed { index, attachment ->
                    ensureAttachmentBlob(message.id, index, attachment)
                    keep.add(attachmentBlobName(message.id, index, attachment.id))
                }
                append(encode(message, blobPath))
                append('\n')
            }
        }
        val dest = appHistoryIndexFile()
        val tmp = File(dir, "index.jsonl.tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(dest)) {
            dest.writeText(body)
            tmp.delete()
        }
        blobs.listFiles()?.forEach { file ->
            if (file.name !in keep) file.delete()
        }
    }

    @Synchronized
    fun clear() {
        appHistoryDir().deleteRecursively()
    }

    @Synchronized
    fun deleteBlob(id: String) {
        blobFile(id).delete()
        val prefix = blobName(id) + "__"
        appHistoryBlobsDir().listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix)) file.delete()
        }
    }

    fun storeBlobFromFile(id: String, source: File): String {
        val dest = blobFile(id)
        dest.parentFile?.mkdirs()
        if (source.exists() && source.canonicalFile != dest.canonicalFile) {
            source.copyTo(dest, overwrite = true)
        }
        return dest.absolutePath
    }

    private fun ensureBlob(message: ChatMessage): String? {
        if (!message.isImage && !message.isFile) return null
        val dest = blobFile(message.id)
        dest.parentFile?.mkdirs()
        val fromPath = message.filePath?.let { File(it) }?.takeIf { it.exists() }
        when {
            fromPath != null && fromPath.canonicalFile != dest.canonicalFile -> {
                fromPath.copyTo(dest, overwrite = true)
            }
            fromPath != null && fromPath.canonicalFile == dest.canonicalFile -> Unit
            message.pic != null -> dest.writeBytes(message.pic)
            else -> return fromPath?.absolutePath
        }
        return dest.takeIf { it.exists() }?.absolutePath
    }

    private fun ensureAttachmentBlob(
        messageId: String,
        index: Int,
        attachment: ChatAttachment,
    ): String? {
        val dest = attachmentBlobFile(messageId, index, attachment.id)
        dest.parentFile?.mkdirs()
        val source = attachment.filePath?.let { File(it) }?.takeIf { it.exists() }
        if (source != null && source.canonicalFile != dest.canonicalFile) {
            source.copyTo(dest, overwrite = true)
        }
        return dest.takeIf { it.exists() }?.absolutePath
    }

    private fun blobFile(id: String): File = File(appHistoryBlobsDir(), blobName(id))

    private fun attachmentBlobFile(messageId: String, index: Int, attachmentId: String): File =
        File(appHistoryBlobsDir(), attachmentBlobName(messageId, index, attachmentId))

    private fun attachmentBlobName(messageId: String, index: Int, attachmentId: String): String =
        blobName(messageId) + "__" + index + "_" + blobName(attachmentId)

    private fun blobName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown" }

    private fun encode(message: ChatMessage, blobPath: String?): String {
        val kind = when {
            message.isAgent -> "agent"
            message.isFile -> "file"
            message.isImage -> "image"
            message.isSystem -> "system"
            else -> "text"
        }
        val pairs = mutableListOf(
            "id" to message.id,
            "content" to message.content,
            "kind" to kind,
            "isError" to message.isError.toString(),
            "isMine" to message.isMine.toString(),
            "isSystem" to message.isSystem.toString(),
            "sender" to message.sender,
            "senderId" to message.senderId,
            "fileSize" to message.fileSize.toString(),
            "sentAt" to message.sentAt.toString(),
            "conversationId" to message.conversationId,
        )
        message.fileName?.let { pairs += "fileName" to it }
        if (message.isAgent) {
            pairs += "agentKind" to message.agentKind
            pairs += "turnId" to message.turnId
            pairs += "segment" to message.segment.toString()
            pairs += "seq" to message.seq.toString()
            pairs += "phase" to message.phase
            pairs += "markdown" to message.markdown.toString()
            pairs += "toolCallId" to message.toolCallId
            pairs += "toolName" to message.toolName
            pairs += "approvalId" to message.approvalId
            pairs += "requesterId" to message.requesterId
            pairs += "timeoutS" to message.timeoutS.toString()
            pairs += "details" to message.details
            pairs += "status" to message.status
        }
        if (!blobPath.isNullOrBlank() && (message.isFile || message.isImage)) {
            pairs += "hasBlob" to "true"
        }
        if (message.attachments.isNotEmpty()) {
            pairs += "attachmentCount" to message.attachments.size.toString()
            message.attachments.forEachIndexed { index, attachment ->
                pairs += "attachment${index}Id" to attachment.id
                pairs += "attachment${index}Name" to attachment.name
                pairs += "attachment${index}Mime" to attachment.mime
                pairs += "attachment${index}Size" to attachment.size.toString()
            }
        }
        return WireJson.encodeObject(*pairs.toTypedArray())
    }

    private fun decode(json: String): ChatMessage? {
        val id = WireJson.extractString(json, "id") ?: return null
        val content = WireJson.extractString(json, "content") ?: ""
        val kind = WireJson.extractString(json, "kind") ?: "text"
        val isError = WireJson.extractString(json, "isError")?.toBooleanStrictOrNull() ?: false
        val isMine = WireJson.extractString(json, "isMine")?.toBooleanStrictOrNull() ?: false
        val isSystem = WireJson.extractString(json, "isSystem")?.toBooleanStrictOrNull()
            ?: (kind == "system")
        val sender = WireJson.extractString(json, "sender") ?: ""
        val senderId = WireJson.extractString(json, "senderId") ?: ""
        val fileName = WireJson.extractString(json, "fileName")
        val fileSize = WireJson.extractString(json, "fileSize")?.toLongOrNull() ?: 0L
        val sentAt = WireJson.extractString(json, "sentAt")?.toLongOrNull() ?: 0L
        val conversationId = WireJson.extractString(json, "conversationId") ?: "group"
        val agentKind = WireJson.extractString(json, "agentKind") ?: ""
        val turnId = WireJson.extractString(json, "turnId") ?: ""
        val segment = WireJson.extractString(json, "segment")?.toIntOrNull() ?: 0
        val seq = WireJson.extractString(json, "seq")?.toIntOrNull() ?: 0
        val phase = WireJson.extractString(json, "phase") ?: ""
        val markdown = WireJson.extractString(json, "markdown")?.toBooleanStrictOrNull() ?: false
        val toolCallId = WireJson.extractString(json, "toolCallId") ?: ""
        val toolName = WireJson.extractString(json, "toolName") ?: ""
        val approvalId = WireJson.extractString(json, "approvalId") ?: ""
        val requesterId = WireJson.extractString(json, "requesterId") ?: ""
        val timeoutS = WireJson.extractString(json, "timeoutS")?.toIntOrNull() ?: 0
        val details = WireJson.extractString(json, "details") ?: ""
        val status = WireJson.extractString(json, "status") ?: ""
        val attachmentCount = WireJson.extractString(json, "attachmentCount")?.toIntOrNull() ?: 0
        val attachments = buildList {
            for (index in 0 until attachmentCount.coerceAtMost(4)) {
                val attachmentId = WireJson.extractString(json, "attachment${index}Id") ?: continue
                val name = WireJson.extractString(json, "attachment${index}Name") ?: "图片${index + 1}"
                val mime = WireJson.extractString(json, "attachment${index}Mime") ?: "image/png"
                val size = WireJson.extractString(json, "attachment${index}Size")?.toLongOrNull() ?: 0L
                val blob = attachmentBlobFile(id, index, attachmentId).takeIf { it.exists() }
                add(
                    ChatAttachment(
                        id = attachmentId,
                        name = name,
                        mime = mime,
                        filePath = blob?.absolutePath,
                        size = size.takeIf { it > 0L } ?: (blob?.length() ?: 0L),
                    )
                )
            }
        }
        val blob = blobFile(id).takeIf { it.exists() }
        val blobPath = blob?.absolutePath
        return when (kind) {
            "agent" -> ChatMessage(
                id = id,
                content = content,
                pic = null,
                isError = isError,
                sender = sender,
                senderId = senderId,
                isMine = isMine,
                isSystem = isSystem,
                agentKind = agentKind,
                turnId = turnId,
                segment = segment,
                seq = seq,
                phase = phase,
                markdown = markdown,
                toolCallId = toolCallId,
                toolName = toolName,
                approvalId = approvalId,
                requesterId = requesterId,
                timeoutS = timeoutS,
                details = details,
                status = status,
                sentAt = sentAt,
                conversationId = conversationId,
                attachments = attachments,
            )
            "file" -> ChatMessage(
                id = id,
                content = content,
                pic = null,
                isError = isError,
                sender = sender,
                senderId = senderId,
                isMine = isMine,
                isSystem = isSystem,
                fileName = fileName ?: "file.bin",
                filePath = blobPath,
                fileSize = fileSize.takeIf { it > 0L } ?: (blob?.length() ?: 0L),
                sentAt = sentAt,
                conversationId = conversationId,
                attachments = attachments,
            )
            "image" -> ChatMessage(
                id = id,
                content = content,
                pic = null,
                isError = isError,
                sender = sender,
                senderId = senderId,
                isMine = isMine,
                isSystem = isSystem,
                filePath = blobPath,
                fileSize = fileSize.takeIf { it > 0L } ?: (blob?.length() ?: 0L),
                sentAt = sentAt,
                conversationId = conversationId,
                attachments = attachments,
            )
            else -> ChatMessage(
                id = id,
                content = content,
                pic = null,
                isError = isError,
                sender = sender,
                senderId = senderId,
                isMine = isMine,
                isSystem = isSystem,
                sentAt = sentAt,
                conversationId = conversationId,
                attachments = attachments,
            )
        }
    }
}
