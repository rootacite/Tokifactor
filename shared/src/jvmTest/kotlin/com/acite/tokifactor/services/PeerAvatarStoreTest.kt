package com.acite.tokifactor.services

import com.acite.tokifactor.model.legacyMemberId
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class PeerAvatarStoreTest {
    private val previousHome = System.getProperty("user.home")
    private val tempHome = Files.createTempDirectory("tokifactor-avatar-identity-test").toFile()

    @BeforeTest
    fun setUp() {
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", previousHome)
        tempHome.deleteRecursively()
    }

    @Test
    fun oldNameKeyMovesToMemberId() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        PeerAvatarStore.save("Fuyu", bytes)
        val memberId = legacyMemberId("Fuyu")
        PeerAvatarStore.migrateKey("Fuyu", memberId)
        assertContentEquals(bytes, PeerAvatarStore.load()[memberId]?.bytes)
        assertNull(PeerAvatarStore.load()["Fuyu"])
    }
}
