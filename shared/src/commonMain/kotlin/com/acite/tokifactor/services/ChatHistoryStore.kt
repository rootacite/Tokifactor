package com.acite.tokifactor.services

import com.acite.tokifactor.appHistoryBlobsDir
import com.acite.tokifactor.appHistoryDir
import com.acite.tokifactor.appHistoryIndexFile
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

    private fun blobFile(id: String): File = File(appHistoryBlobsDir(), blobName(id))

    private fun blobName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown" }

    private fun encode(message: ChatMessage, blobPath: String?): String {
        val kind = when {
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
        )
        message.fileName?.let { pairs += "fileName" to it }
        if (!blobPath.isNullOrBlank() && (message.isFile || message.isImage)) {
            pairs += "hasBlob" to "true"
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
        val blob = blobFile(id).takeIf { it.exists() }
        val blobPath = blob?.absolutePath
        return when (kind) {
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
            )
        }
    }
}
