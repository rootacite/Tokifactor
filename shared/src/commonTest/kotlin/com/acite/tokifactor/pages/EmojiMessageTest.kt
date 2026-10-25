package com.acite.tokifactor.pages

import com.acite.tokifactor.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmojiMessageTest {
    private val asset = EmojiAsset(
        name = "aki_cheer",
        file = "emoji/aki/aki_cheer.png",
        desc = "开心时使用",
    )
    private val catalog = EmojiCatalog(mapOf(asset.name to asset))

    @Test
    fun splitsTokenAndKeepsTextOrder() {
        val segments = splitEmojiSegments(
            "先说一句 #[aki_cheer:开心] 再说一句",
            catalog,
        )
        assertEquals(3, segments.size)
        assertEquals("先说一句", assertIs<EmojiContentSegment.Text>(segments[0]).text)
        assertEquals(asset, assertIs<EmojiContentSegment.Emoji>(segments[1]).asset)
        assertEquals("再说一句", assertIs<EmojiContentSegment.Text>(segments[2]).text)
    }

    @Test
    fun frontendToleratesMissingOrWrongDescription() {
        for (token in listOf("#[aki_cheer]", "#[aki_cheer:]", "#[aki_cheer:写错了]")) {
            val segments = splitEmojiSegments(token, catalog)
            assertEquals(1, segments.size)
            assertEquals(asset, assertIs<EmojiContentSegment.Emoji>(segments.single()).asset)
        }
    }

    @Test
    fun unknownNameAndCodeExamplesStayText() {
        assertEquals(
            listOf(EmojiContentSegment.Text("#[unknown:无语]")),
            splitEmojiSegments("#[unknown:无语]", catalog),
        )
        for (content in listOf(
            "`#[aki_cheer:开心]`",
            "```\n#[aki_cheer:开心]\n```",
        )) {
            val segments = splitEmojiSegments(content, catalog)
            assertEquals(1, segments.size)
            assertIs<EmojiContentSegment.Text>(segments.single())
        }
    }

    @Test
    fun adjacentTokensBecomeSeparateMessages() {
        val segments = splitEmojiSegments(
            "#[aki_cheer:开心]#[aki_cheer:得意]",
            catalog,
        )
        assertTrue(segments.all { it is EmojiContentSegment.Emoji })
        assertEquals(2, segments.size)
    }

    @Test
    fun displayItemsUseStableKeysAndOriginalMessageId() {
        val message = ChatMessage(
            id = "turn-1",
            content = "#[aki_cheer:开心] 你们在干什么？",
            pic = null,
            agentKind = "turn",
            phase = "final",
            sender = "Aki",
        )
        val items = buildChatDisplayItems(listOf(message), catalog)
        assertEquals(2, items.size)
        val emoji = assertIs<ChatDisplayItem.EmojiPart>(items[0])
        val text = assertIs<ChatDisplayItem.TextPart>(items[1])
        assertEquals("turn-1", emoji.message.id)
        assertEquals("turn-1", text.message.id)
        assertEquals("你们在干什么？", text.message.content)
        assertTrue(emoji.key != text.key)
    }

    @Test
    fun userEmojiMessagesSplitIntoIndependentItems() {
        val message = ChatMessage(
            id = "user-1",
            content = "你看这个 #[aki_cheer:开心] 笑死",
            pic = null,
            isMine = true,
            sender = "Master",
        )
        val items = buildChatDisplayItems(listOf(message), catalog)
        assertEquals(3, items.size)
        assertIs<ChatDisplayItem.TextPart>(items[0])
        assertIs<ChatDisplayItem.EmojiPart>(items[1])
        assertIs<ChatDisplayItem.TextPart>(items[2])
    }

    @Test
    fun outgoingTokenUsesCatalogDescriptionAndStaysParseable() {
        val token = emojiToken(asset)
        assertEquals("#[aki_cheer:开心时使用]", token)
        assertIs<EmojiContentSegment.Emoji>(splitEmojiSegments(token, catalog).single())
    }

    @Test
    fun pickerGroupsEveryMemberAlphabetically() {
        val extra = EmojiAsset("fuyu_deadpan", "emoji/fuyu/fuyu_deadpan.jpg", "无语")
        val grouped = emojiGroups(EmojiCatalog(mapOf(asset.name to asset, extra.name to extra)))
        assertEquals(listOf("aki", "fuyu"), grouped.map { it.first })
        assertEquals("fuyu_deadpan", grouped[1].second.single().name)
    }

}
