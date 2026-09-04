package com.acite.tokifactor.services

import com.acite.tokifactor.appConfigFile
import io.github.vinceglb.filekit.core.PlatformFile
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.min

/**
 * Disk-backed chunk IO for pictures, files, and long text. MQTT publish/subscribe
 * still lives in [com.acite.tokifactor.pages.MainPageViewModel].
 */
object ChunkedTransfer {
    const val MAX_IN_FLIGHT_CHUNKS = 16
    const val INCOMPLETE_TIMEOUT_MS = 5_000L
    const val MAX_CHUNK_BYTES = 8 * 1024
    const val PREVIEW_MAX_BYTES = 8L * 1024 * 1024
    const val TEXT_CHUNK_THRESHOLD_BYTES = 4 * 1024
    private const val MQTT_HEAD_BYTES = 256
    private const val JSON_OVERHEAD_BYTES = 512
    private const val DEFAULT_PACKET_BYTES = 256 * 1024

    fun sha256Hex(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(data))

    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(MAX_CHUNK_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return hex(digest.digest())
    }

    fun chunkSize(maxPacketSize: Int?): Int {
        val packet = maxPacketSize?.takeIf { it > MQTT_HEAD_BYTES + JSON_OVERHEAD_BYTES }
            ?: DEFAULT_PACKET_BYTES
        val budget = (packet / 2) - MQTT_HEAD_BYTES - JSON_OVERHEAD_BYTES
        val fromPacket = ((budget * 3) / 4).coerceAtLeast(256)
        return min(fromPacket, MAX_CHUNK_BYTES)
    }

    fun chunkCount(fileSize: Long, chunkSize: Int): Int {
        if (fileSize <= 0L) return 1
        return ((fileSize + chunkSize - 1) / chunkSize).toInt()
    }

    fun transfersRoot(): File {
        val dir = File(requireNotNull(appConfigFile().parentFile), "transfers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun transferDir(transferId: String): File = File(transfersRoot(), transferId)

    fun payloadFile(transferId: String): File = File(transferDir(transferId), "payload")

    fun partFile(transferId: String, index: Int): File {
        val dir = File(transferDir(transferId), "parts")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, index.toString())
    }

    fun deleteTransfer(transferId: String) {
        transferDir(transferId).deleteRecursively()
    }

    fun importBytes(data: ByteArray, dest: File): Pair<Long, String> {
        dest.parentFile?.mkdirs()
        dest.writeBytes(data)
        return data.size.toLong() to sha256Hex(data)
    }

    suspend fun importSource(file: PlatformFile, dest: File): Pair<Long, String> {
        dest.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        dest.outputStream().buffered().use { output ->
            if (file.supportsStreams()) {
                file.getStream().use { stream ->
                    val buf = ByteArray(MAX_CHUNK_BYTES)
                    while (true) {
                        val n = stream.readInto(buf, buf.size)
                        if (n < 0) break
                        if (n == 0) continue
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        size += n
                    }
                }
            } else {
                val bytes = file.readBytes()
                output.write(bytes)
                digest.update(bytes)
                size = bytes.size.toLong()
            }
        }
        return size to hex(digest.digest())
    }

    fun readSlice(file: File, index: Int, chunkSize: Int, fileSize: Long): ByteArray {
        val offset = index.toLong() * chunkSize
        if (offset >= fileSize) return ByteArray(0)
        val len = min(chunkSize.toLong(), fileSize - offset).toInt()
        val out = ByteArray(len)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(out)
        }
        return out
    }

    fun writePart(transferId: String, index: Int, clear: ByteArray) {
        partFile(transferId, index).writeBytes(clear)
    }

    fun concatParts(transferId: String, total: Int, dest: File): Long {
        dest.parentFile?.mkdirs()
        var size = 0L
        dest.outputStream().buffered().use { output ->
            for (i in 0 until total) {
                val part = partFile(transferId, i)
                part.inputStream().buffered().use { input ->
                    val buf = ByteArray(MAX_CHUNK_BYTES)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        size += n
                    }
                }
            }
        }
        File(transferDir(transferId), "parts").deleteRecursively()
        return size
    }

    fun split(data: ByteArray, chunkSize: Int): List<ByteArray> {
        require(chunkSize > 0)
        if (data.isEmpty()) return listOf(ByteArray(0))
        val out = ArrayList<ByteArray>((data.size + chunkSize - 1) / chunkSize)
        var offset = 0
        while (offset < data.size) {
            val end = min(offset + chunkSize, data.size)
            out.add(data.copyOfRange(offset, end))
            offset = end
        }
        return out
    }

    fun join(parts: Array<ByteArray>): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    private fun hex(digest: ByteArray): String =
        digest.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
}
