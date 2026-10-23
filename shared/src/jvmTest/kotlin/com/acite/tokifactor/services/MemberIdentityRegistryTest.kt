package com.acite.tokifactor.services

import com.acite.tokifactor.model.MemberIdentity
import com.acite.tokifactor.model.legacyMemberId
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MemberIdentityRegistryTest {
    private val previousHome = System.getProperty("user.home")
    private val tempHome = Files.createTempDirectory("tokifactor-identities-test").toFile()

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
    fun renameKeepsOldLabelPermanently() {
        val registry = MemberIdentityRegistry()
        val id = legacyMemberId("Aki")
        registry.register(MemberIdentity(id = id, name = "Aki"), source = "device-a")
        assertEquals("device-a", registry.sourceFor(id))
        registry.rename(id, "秋")
        assertEquals(id, registry.resolve("Aki"))
        assertEquals(id, registry.resolve("秋"))
        assertEquals("秋", registry.get(id)?.name)
    }

    @Test
    fun conflictingRemoteIdentityDoesNotStealLabel() {
        val registry = MemberIdentityRegistry()
        val owner = legacyMemberId("Aki")
        registry.register(MemberIdentity(id = owner, name = "Aki"), source = "device-a")
        val claimed = registry.observe(
            MemberIdentity(id = "mem_0123456789abcdef0123456789abcdef", name = "Aki"),
            source = "device-b",
        )
        assertFalse(claimed)
        assertEquals(owner, registry.resolve("Aki"))
    }

    @Test
    fun renameToAnotherOwnersLabelFails() {
        val registry = MemberIdentityRegistry()
        val aki = legacyMemberId("Aki")
        val fuyu = legacyMemberId("Fuyu")
        registry.register(MemberIdentity(id = aki, name = "Aki"), source = "a")
        registry.register(MemberIdentity(id = fuyu, name = "Fuyu"), source = "b")
        assertFailsWith<IdentityLabelConflict> { registry.rename(fuyu, "Aki") }
    }
}
