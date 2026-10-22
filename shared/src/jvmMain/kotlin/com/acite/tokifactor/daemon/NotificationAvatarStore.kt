package com.acite.tokifactor.daemon

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest

object NotificationAvatarStore {
    fun iconPath(senderId: String, bytes: ByteArray): String? {
        if (senderId.isBlank() || bytes.isEmpty()) return null
        val runtime = daemonSocketFile().parentFile ?: return null
        return writeAvatar(File(runtime, "notifications"), senderId, bytes)?.absolutePath
    }

    internal fun writeAvatar(dir: File, senderId: String, bytes: ByteArray): File? {
        if (senderId.isBlank() || bytes.isEmpty()) return null
        return try {
            dir.mkdirs()
            setDirectoryPermissions(dir)
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(senderId.encodeToByteArray())
                .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
            val extension = imageExtension(bytes)
            val dest = File(dir, "avatar-$hash.$extension")
            val tmp = File(dir, "avatar-$hash.tmp")
            tmp.writeBytes(bytes)
            Files.setPosixFilePermissions(
                tmp.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
            if (!tmp.renameTo(dest)) {
                dest.writeBytes(bytes)
                tmp.delete()
            }
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun imageExtension(bytes: ByteArray): String = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() &&
            bytes[3] == 0x47.toByte() -> "png"
        bytes.size >= 3 &&
            bytes[0] == 0xff.toByte() &&
            bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte() -> "jpg"
        bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() -> "gif"
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte() -> "webp"
        else -> "img"
    }
}
