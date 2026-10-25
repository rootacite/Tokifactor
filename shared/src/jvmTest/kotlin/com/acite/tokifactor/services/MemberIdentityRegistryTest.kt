package com.acite.tokifactor.services

import com.acite.tokifactor.model.MemberIdentity
import com.acite.tokifactor.model.legacyMemberId
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    @Test
    fun concurrentReadsAndObservesDoNotOverlapFileLocks() {
        val registry = MemberIdentityRegistry()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(80)
        val failures = mutableListOf<Throwable>()
        repeat(80) { index ->
            pool.execute {
                try {
                    start.await()
                    val id = legacyMemberId("Member$index")
                    if (index % 2 == 0) {
                        registry.observe(MemberIdentity(id = id, name = "Member$index"), "d$index")
                    } else {
                        registry.get(id)
                    }
                } catch (t: Throwable) {
                    synchronized(failures) { failures += t }
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        pool.shutdownNow()
        assertEquals(emptyList(), failures)
    }
}
