package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MemberIdentityTest {
    @Test
    fun legacyIdsMatchThePythonFixedVectors() {
        assertEquals("legacy_8ddfb8585d44e615b2fe8b37846b4ef6", legacyMemberId("Aki"))
        assertEquals("legacy_8ddfb8585d44e615b2fe8b37846b4ef6", legacyMemberId(" Aki "))
        assertEquals("legacy_86a236d147effcfbdfe3cd8b005cdf7b", legacyMemberId("FUYU"))
        assertEquals("legacy_c156f64c68b7f6b4347be31185fc8d31", legacyMemberId(" 秋 "))
        assertEquals("legacy_8ddfb8585d44e615b2fe8b37846b4ef6", legacyMemberId("Ａｋｉ"))
    }

    @Test
    fun randomIdsUseTheMemPrefixAndDoNotRepeat() {
        val first = newMemberId()
        val second = newMemberId()
        assertTrue(isMemberId(first))
        assertTrue(isMemberId(second))
        assertNotEquals(first, second)
    }

    @Test
    fun renameKeepsOldNameAsAlias() {
        val identity = MemberIdentity(id = legacyMemberId("Aki"), name = "Aki")
        val renamed = identity.renamed("秋")
        assertEquals("秋", renamed.name)
        assertTrue(renamed.aliases.any { canonicalMemberName(it) == "aki" })
    }
}
