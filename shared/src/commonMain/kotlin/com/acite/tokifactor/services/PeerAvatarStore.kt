package com.acite.tokifactor.services

import com.acite.tokifactor.appPeerAvatarsDir
import java.io.File

object PeerAvatarStore {
    data class Entry(val id: String, val hash: String, val bytes: ByteArray)

    @Synchronized
    fun load(): Map<String, Entry> {
        val dir = appPeerAvatarsDir()
        if (!dir.isDirectory) return emptyMap()
        val out = mutableMapOf<String, Entry>()
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name.endsWith(".tmp")) return@forEach
            if (file.length() <= 0L || file.length() > SettingsStore.MAX_AVATAR_BYTES) return@forEach
            val id = file.name
            if (id.isEmpty()) return@forEach
            val bytes = try {
                file.readBytes()
            } catch (_: Exception) {
                return@forEach
            }
            if (bytes.isEmpty()) return@forEach
            out[id] = Entry(id = id, hash = ChunkedTransfer.sha256Hex(bytes), bytes = bytes)
        }
        return out
    }

    @Synchronized
    fun save(id: String, bytes: ByteArray) {
        val key = fileName(id) ?: return
        if (bytes.isEmpty() || bytes.size > SettingsStore.MAX_AVATAR_BYTES) return
        val dir = appPeerAvatarsDir()
        dir.mkdirs()
        val dest = File(dir, key)
        val tmp = File(dir, "$key.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(dest)) {
            dest.writeBytes(bytes)
            tmp.delete()
        }
    }

    @Synchronized
    fun delete(id: String) {
        val key = fileName(id) ?: return
        File(appPeerAvatarsDir(), key).delete()
    }

    private fun fileName(id: String): String? {
        val key = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return key.takeIf { it.isNotEmpty() }
    }
}
