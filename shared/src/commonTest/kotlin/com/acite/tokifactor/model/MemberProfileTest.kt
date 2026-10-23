package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberProfileTest {
    @Test
    fun bioLimitCountsAsciiAndNonAsciiByCodePoint() {
        assertEquals(512, memberDescriptionWeight("a".repeat(512)))
        assertEquals(512, memberDescriptionWeight("中".repeat(256)))
        assertEquals(514, memberDescriptionWeight("中".repeat(257)))
        assertEquals(512, memberDescriptionWeight("a".repeat(510) + "中"))
        assertEquals(4, memberDescriptionWeight("😀😀"))
    }

    @Test
    fun truncationKeepsCompleteCharacters() {
        assertEquals("a".repeat(512), truncateMemberDescription("a".repeat(600)))
        assertEquals("中".repeat(256), truncateMemberDescription("中".repeat(257)))
        assertEquals("abc中", truncateMemberDescription("abc中中", 5))
        val emoji = truncateMemberDescription("😀".repeat(300))
        assertEquals(256, emoji.codePointsCompat().size)
        assertEquals(512, memberDescriptionWeight(emoji))
        assertTrue(emoji.codePointsCompat().all { it > 0xFFFF })
    }

    @Test
    fun missingControlFieldsPreserveExistingProfile() {
        val existing = AgentStatus(
            memberId = "fuyu",
            memberName = "Fuyu",
            description = "old bio",
            memberKind = MEMBER_KIND_AGENT,
            aliases = "Fuyu,冬",
        )
        val updated = existing.withControlProfile(
            metadata = mapOf("agentName" to "Fuyu"),
            fallbackName = "Fuyu",
            now = 42L,
        )
        assertEquals("old bio", updated.description)
        assertEquals(MEMBER_KIND_AGENT, updated.memberKind)
        assertEquals("Fuyu,冬", updated.aliases)
        assertEquals(42L, updated.lastSeenAt)

        val nonOwnerUpdate = existing.withControlProfile(
            metadata = mapOf(
                "description" to "impostor bio",
                "memberKind" to MEMBER_KIND_USER,
            ),
            fallbackName = "Other",
            now = 99L,
            updateProfile = false,
        )
        assertEquals("old bio", nonOwnerUpdate.description)
        assertEquals(MEMBER_KIND_AGENT, nonOwnerUpdate.memberKind)
        assertEquals(99L, nonOwnerUpdate.lastSeenAt)
    }

    @Test
    fun controlKindDefaultsToAgentAndUserIsExplicit() {
        val base = AgentStatus(memberId = "someone")
        assertEquals(
            MEMBER_KIND_AGENT,
            base.withControlProfile(emptyMap(), "Someone", 1L).memberKind,
        )
        assertEquals(
            MEMBER_KIND_USER,
            base.withControlProfile(mapOf("memberKind" to "user"), "Someone", 1L).memberKind,
        )
    }

    @Test
    fun userProbeMetadataCarriesProfileAndKind() {
        val metadata = memberControlMetadata(
            name = "  Master ",
            bio = "中".repeat(300),
            memberKind = MEMBER_KIND_USER,
            memberId = "legacy_0123456789abcdef0123456789abcdef",
            sourceId = "device-a",
        )
        assertEquals("legacy_0123456789abcdef0123456789abcdef", metadata["agentId"])
        assertEquals("legacy_0123456789abcdef0123456789abcdef", metadata["agent"])
        assertEquals("legacy_0123456789abcdef0123456789abcdef", metadata["memberId"])
        assertEquals("Master", metadata["agentName"])
        assertEquals("Master", metadata["memberName"])
        assertEquals("device-a", metadata["sourceId"])
        assertEquals(MEMBER_KIND_USER, metadata["memberKind"])
        assertEquals("中".repeat(256), metadata["description"])
    }

    @Test
    fun nameKeyIsTrimmedAndCaseInsensitive() {
        assertEquals("master", memberNameKey("  Master "))
        assertEquals("fuyu", memberNameKey("FUYU"))
        assertEquals("秋", memberNameKey(" 秋 "))
    }

    @Test
    fun recentMessagesFilterStatusItemsAndKeepNewestThree() {
        val messages = listOf(
            ChatMessage("1", "one", null, sender = "Fuyu", senderId = "fuyu", sentAt = 1L),
            ChatMessage("2", "tool", null, sender = "Fuyu", senderId = "fuyu", agentKind = "tool"),
            ChatMessage("3", "two", null, sender = "Fuyu", senderId = "fuyu", sentAt = 3L),
            ChatMessage("4", "other", null, sender = "Aki", senderId = "aki", sentAt = 4L),
            ChatMessage("5", "three", null, sender = "Fuyu", senderId = "fuyu", sentAt = 5L),
            ChatMessage("6", "four", null, sender = "Fuyu", senderId = "fuyu", sentAt = 6L),
        )
        assertEquals(
            listOf("3", "5", "6"),
            recentMemberMessages(messages, "fuyu").map { it.id },
        )
    }

    @Test
    fun relativeTimeUsesCompactBuckets() {
        val now = 1_000_000_000L
        assertEquals("Just now", relativeTimeLabel(now - 10_000L, now))
        assertEquals("2m ago", relativeTimeLabel(now - 120_000L, now))
        assertEquals("3h ago", relativeTimeLabel(now - 3 * 3_600_000L, now))
        assertEquals("2d ago", relativeTimeLabel(now - 2 * 86_400_000L, now))
    }
}

private fun String.codePointsCompat(): List<Int> {
    val points = mutableListOf<Int>()
    var index = 0
    while (index < length) {
        val first = this[index].code
        if (first in 0xD800..0xDBFF && index + 1 < length) {
            val second = this[index + 1].code
            points += 0x10000 + ((first - 0xD800) shl 10) + (second - 0xDC00)
            index += 2
        } else {
            points += first
            index += 1
        }
    }
    return points
}
