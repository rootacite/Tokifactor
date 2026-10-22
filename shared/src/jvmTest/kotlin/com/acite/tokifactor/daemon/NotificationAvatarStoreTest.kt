package com.acite.tokifactor.daemon

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationAvatarStoreTest {
    @Test
    fun writesStablePngIconAndOverwritesIt() {
        val dir = Files.createTempDirectory("tokifactord-avatar-test").toFile()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3, 4)
        val updatedPng = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 9, 8, 7, 6)
        try {
            val first = NotificationAvatarStore.writeAvatar(dir, "alice", png)
            val second = NotificationAvatarStore.writeAvatar(dir, "alice", updatedPng)

            assertTrue(first != null)
            assertEquals(first, second)
            assertTrue(first.name.endsWith(".png"))
            assertContentEquals(updatedPng, first.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun detectsJpegAndRejectsEmptyInput() {
        val dir = Files.createTempDirectory("tokifactord-avatar-test").toFile()
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0, 1)
        try {
            val file = NotificationAvatarStore.writeAvatar(dir, "bob", jpeg)
            assertTrue(file != null && file.name.endsWith(".jpg"))
            assertNull(NotificationAvatarStore.writeAvatar(dir, "", jpeg))
            assertNull(NotificationAvatarStore.writeAvatar(dir, "bob", byteArrayOf()))
        } finally {
            dir.deleteRecursively()
        }
    }
}
