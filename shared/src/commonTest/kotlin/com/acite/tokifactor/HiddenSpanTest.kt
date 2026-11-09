package com.acite.tokifactor

import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.pages.ChatDisplayItem
import com.acite.tokifactor.pages.EmojiAsset
import com.acite.tokifactor.pages.EmojiCatalog
import com.acite.tokifactor.pages.buildChatDisplayItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HiddenSpanTest {
    @Test
    fun leavesTextWithoutASpanUntouched() {
        val source = "hello  world"
        assertEquals(source, concealHiddenSpans(source))
    }

    @Test
    fun removesASpanAndCollapsesTheGap() {
        assertEquals("看见 这句话", concealHiddenSpans("看见 ~[不要显示] 这句话"))
        assertEquals("a b c", concealHiddenSpans("a ~[one] b ~[two] c"))
        assertEquals("prefixsuffix", concealHiddenSpans("prefix~[x]suffix"))
        assertEquals("hello", concealHiddenSpans("hello ~[x]"))
        assertEquals("hello", concealHiddenSpans("~[x] hello"))
        assertEquals("", concealHiddenSpans("~[only]"))
        assertEquals("", concealHiddenSpans("~[]"))
    }

    @Test
    fun firstBracketEndsTheSpanAndAnUnclosedSpanStays() {
        assertEquals("still]", concealHiddenSpans("~[outer ~[inner] still]"))
        assertEquals("hello ~[no close", concealHiddenSpans("hello ~[no close"))
        assertEquals(
            "see this",
            concealHiddenSpans("see ~[line1\nline2] this"),
        )
    }

    @Test
    fun keepsSpansInsideCode() {
        val fenced = "before\n\n```\n~[keep]\n```\n\nafter"
        assertEquals(fenced, concealHiddenSpans(fenced))
        assertEquals("see `~[keep]` now", concealHiddenSpans("see `~[keep]` now"))
        assertEquals("visible", concealHiddenSpans("visible ~[hide]"))
    }

    @Test
    fun displayDropsHiddenParagraphsAndKeepsTheRest() {
        val message = ChatMessage(
            id = "turn-1",
            content = "第一段\n\n~[隐藏]\n\n第二段 ~[也不显示]",
            pic = null,
            agentKind = "turn",
            phase = "final",
            sender = "Aki",
        )
        val items = buildChatDisplayItems(listOf(message), EmojiCatalog.EMPTY)
        val parts = items.map { assertIs<ChatDisplayItem.TextPart>(it) }
        assertEquals(listOf("第一段", "第二段"), parts.map { it.message.content })
        assertEquals(listOf(false, true), parts.map { it.isLast })
        assertTrue(parts.all { it.message.id == "turn-1" })
    }

    @Test
    fun hiddenOnlyMessageProducesNoBubble() {
        val message = ChatMessage(
            id = "turn-2",
            content = "~[整条都不显示]",
            pic = null,
            agentKind = "turn",
            phase = "final",
            sender = "Aki",
        )
        assertEquals(emptyList(), buildChatDisplayItems(listOf(message), EmojiCatalog.EMPTY))
    }

    @Test
    fun hiddenTextBesideAnEmojiDoesNotLeak() {
        val asset = EmojiAsset(
            name = "aki_cheer",
            file = "emoji/aki/aki_cheer.png",
            desc = "开心",
        )
        val message = ChatMessage(
            id = "turn-3",
            content = "#[aki_cheer:开心] ~[旁白]",
            pic = null,
            agentKind = "turn",
            phase = "final",
            sender = "Aki",
        )
        val items = buildChatDisplayItems(
            listOf(message),
            EmojiCatalog(mapOf(asset.name to asset)),
        )
        assertEquals(1, items.size)
        assertIs<ChatDisplayItem.EmojiPart>(items.single())
    }

    @Test
    fun parseInnerVoiceSegmentsExtractsCorrectSegments() {
        val mixed = "看到这个 ~[心里暗暗高兴] 真的很棒"
        val segments = parseInnerVoiceSegments(mixed)
        assertEquals(3, segments.size)
        assertEquals("看到这个 ", segments[0].text)
        assertEquals(false, segments[0].isInnerVoice)
        assertEquals("心里暗暗高兴", segments[1].text)
        assertEquals(true, segments[1].isInnerVoice)
        assertEquals(" 真的很棒", segments[2].text)
        assertEquals(false, segments[2].isInnerVoice)

        val pure = "~[只是一段心声]"
        val pureSegments = parseInnerVoiceSegments(pure)
        assertEquals(1, pureSegments.size)
        assertEquals("只是一段心声", pureSegments[0].text)
        assertEquals(true, pureSegments[0].isInnerVoice)
    }

    @Test
    fun isEntirelyHiddenSpanDetectsProperly() {
        assertTrue(isEntirelyHiddenSpan("~[只是一段心声]"))
        assertTrue(isEntirelyHiddenSpan("   ~[只是一段心声]   "))
        assertTrue(isEntirelyHiddenSpan("~[一段]  ~[二段]"))
        assertFalse(isEntirelyHiddenSpan("hello ~[world]"))
        assertFalse(isEntirelyHiddenSpan("hello world"))
        assertFalse(isEntirelyHiddenSpan(""))
        assertFalse(isEntirelyHiddenSpan("   "))
    }

    @Test
    fun unwrapAndFormatInnerVoice() {
        assertEquals("只是一段心声", unwrapInnerVoice("~[只是一段心声]"))
        assertEquals("💭 只是一段心声", formatInnerVoiceText("~[只是一段心声]"))
        assertEquals("prefix 💭 thought suffix", formatInnerVoiceText("prefix~[thought]suffix"))
    }

    @Test
    fun buildChatDisplayItemsWithShowInnerVoiceRevealsMessages() {
        val message = ChatMessage(
            id = "turn-hidden",
            content = "~[原本隐藏的心声]",
            pic = null,
            agentKind = "turn",
            phase = "final",
            sender = "Aki",
        )
        // When showInnerVoice is false, should be empty
        assertEquals(
            emptyList(),
            buildChatDisplayItems(listOf(message), EmojiCatalog.EMPTY, showInnerVoice = false),
        )
        // When showInnerVoice is true, should be displayed
        val items = buildChatDisplayItems(listOf(message), EmojiCatalog.EMPTY, showInnerVoice = true)
        assertEquals(1, items.size)
        val item = assertIs<ChatDisplayItem.TextPart>(items.single())
        assertEquals("~[原本隐藏的心声]", item.message.content)
    }

    @Test
    fun buildChatDisplayItemsWithShowInnerVoiceKeepsAllParagraphs() {
        val message = ChatMessage(
            id = "turn-multi",
            content = "第一段\n\n~[隐藏心声]\n\n第二段",
            pic = null,
            agentKind = "turn",
            phase = "final",
            sender = "Aki",
        )
        val items = buildChatDisplayItems(listOf(message), EmojiCatalog.EMPTY, showInnerVoice = true)
        assertEquals(3, items.size)
        val parts = items.map { assertIs<ChatDisplayItem.TextPart>(it) }
        assertEquals(listOf("第一段", "~[隐藏心声]", "第二段"), parts.map { it.message.content })
    }
}
