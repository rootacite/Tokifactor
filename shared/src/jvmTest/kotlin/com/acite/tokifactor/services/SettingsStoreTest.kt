package com.acite.tokifactor.services

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.acite.tokifactor.appConfigFile
import com.acite.tokifactor.model.isMemberId
import com.acite.tokifactor.model.legacyMemberId

class SettingsStoreTest {
    private val previousHome = System.getProperty("user.home")
    private val tempHome = Files.createTempDirectory("tokifactor-settings-test").toFile()

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", previousHome)
        tempHome.deleteRecursively()
    }

    @Test
    fun bioIsClampedAndPersisted() {
        System.setProperty("user.home", tempHome.absolutePath)
        val store = SettingsStore()
        store.updateBio("中".repeat(300))
        assertEquals(512, store.bioWeight())
        assertEquals("中".repeat(256), store.bio)

        val reloaded = SettingsStore()
        assertEquals("中".repeat(256), reloaded.bio)
    }

    @Test
    fun existingInstallMigratesToLegacyMemberId() {
        System.setProperty("user.home", tempHome.absolutePath)
        val file = appConfigFile()
        file.parentFile?.mkdirs()
        file.writeText(
            "displayName = \"Aki\"\n" +
                "deviceId = \"device-old\"\n" +
                "mqttBrokerId = \"default\"\n",
        )
        val store = SettingsStore()
        assertEquals(legacyMemberId("Aki"), store.memberId)
        assertTrue(store.identityBound)
        assertTrue(file.readText().contains("memberId = \"${legacyMemberId("Aki")}\""))
    }

    @Test
    fun freshInstallGetsRandomMemberIdAndIsUnbound() {
        System.setProperty("user.home", tempHome.absolutePath)
        val store = SettingsStore()
        assertTrue(isMemberId(store.memberId))
        assertTrue(store.memberId.startsWith("mem_"))
        assertFalse(store.identityBound)
    }

    @Test
    fun soloStateAndConversationSurviveReload() {
        System.setProperty("user.home", tempHome.absolutePath)
        val store = SettingsStore()
        store.updateSoloState(
            sessionId = "solo-123",
            targetId = "legacy_aki",
            targetName = "Aki",
            startedAt = "2026-10-25T00:00:00Z",
            conversationId = "solo:legacy_aki",
        )
        val reloaded = SettingsStore()
        assertTrue(reloaded.soloActive)
        assertEquals("solo-123", reloaded.soloSessionId)
        assertEquals("legacy_aki", reloaded.soloTargetId)
        assertEquals("Aki", reloaded.soloTargetName)
        assertEquals("solo:legacy_aki", reloaded.conversationId)
        reloaded.clearSoloState()
        val ended = SettingsStore()
        assertFalse(ended.soloActive)
        assertEquals("group", ended.conversationId)
    }
}
