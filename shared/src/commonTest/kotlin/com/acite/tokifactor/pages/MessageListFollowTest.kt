package com.acite.tokifactor.pages

import com.acite.tokifactor.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MessageListFollowTest {
    @Test
    fun bottomIncludesTheEndOfTheLastItem() {
        assertTrue(
            isListAtBottom(
                lastVisibleIndex = 4,
                lastItemOffset = 400,
                lastItemSize = 80,
                totalItems = 5,
                viewportEndOffset = 500,
                afterContentPadding = 16,
            ),
        )
    }

    @Test
    fun tallLastItemIsNotAtBottomUntilItsTailIsVisible() {
        assertFalse(
            isListAtBottom(
                lastVisibleIndex = 4,
                lastItemOffset = 0,
                lastItemSize = 900,
                totalItems = 5,
                viewportEndOffset = 500,
                afterContentPadding = 16,
            ),
        )
    }

    @Test
    fun hiddenLastItemIsNotAtBottom() {
        assertFalse(
            isListAtBottom(
                lastVisibleIndex = 3,
                lastItemOffset = 420,
                lastItemSize = 60,
                totalItems = 5,
                viewportEndOffset = 500,
                afterContentPadding = 16,
            ),
        )
    }

    @Test
    fun emptyListCountsAsBottom() {
        assertTrue(
            isListAtBottom(
                lastVisibleIndex = -1,
                lastItemOffset = 0,
                lastItemSize = 0,
                totalItems = 0,
                viewportEndOffset = 500,
                afterContentPadding = 16,
            ),
        )
    }

    @Test
    fun feedKeyChangesForEachArrivingKind() {
        val user = message("user", "hello", isMine = true)
        val system = message("system", "Connected to the broker", isSystem = true)
        val agent = message("agent", "On it.", agentKind = "turn", phase = "final")
        val running = message(
            "tool",
            """{"arguments":"","result":null}""",
            agentKind = "tool",
            status = "running",
            turnId = "turn-1",
            toolName = "Read",
        )
        val finished = running.copy(
            status = "success",
            content = """{"arguments":"","result":"ok"}""",
        )
        val userKey = keyOf(user)
        assertNotEquals(userKey, keyOf(user, system))
        assertNotEquals(userKey, keyOf(user, agent))
        assertNotEquals(userKey, keyOf(user, running))
        assertNotEquals(keyOf(user, running), keyOf(user, finished))
        assertNotEquals(keyOf(user, running), keyOf(user, running, finished.copy(id = "tool-2")))
    }

    @Test
    fun nonToolMessageCollapsesOnlyTheBrokenToolChain() {
        val firstChain = listOf(tool("read", "turn-a"), tool("grep", "turn-a"))
        val secondChain = listOf(tool("bash", "turn-b"), tool("write", "turn-b"))
        val reply = message("reply", "Done.", agentKind = "turn", phase = "final")
        val openChain = buildChatDisplayItems(firstChain, EmojiCatalog.EMPTY)
        assertEquals(emptySet(), interruptedToolGroupIds(openChain))

        val broken = buildChatDisplayItems(firstChain + reply, EmojiCatalog.EMPTY)
        assertEquals(setOf("read"), interruptedToolGroupIds(broken))

        val brokenThenContinued = buildChatDisplayItems(
            firstChain + reply + secondChain,
            EmojiCatalog.EMPTY,
        )
        assertEquals(setOf("read"), interruptedToolGroupIds(brokenThenContinued))

        val draft = message("draft", "still typing", isMine = true)
        val stagedOnly = buildChatDisplayItems(
            firstChain + draft,
            EmojiCatalog.EMPTY,
            stagedIds = setOf(draft.id),
        )
        assertEquals(emptySet(), interruptedToolGroupIds(stagedOnly))
    }

    private fun tool(id: String, turnId: String) = message(
        id = id,
        content = """{"arguments":"","result":"ok"}""",
        agentKind = "tool",
        status = "success",
        turnId = turnId,
        toolName = "Read",
    )

    private fun keyOf(vararg messages: ChatMessage): Int =
        displayFeedKey(buildChatDisplayItems(messages.toList(), EmojiCatalog.EMPTY))

    private fun message(
        id: String,
        content: String,
        isMine: Boolean = false,
        isSystem: Boolean = false,
        agentKind: String = "",
        phase: String = "",
        status: String = "",
        turnId: String = "",
        toolName: String = "",
    ) = ChatMessage(
        id = id,
        content = content,
        pic = null,
        isMine = isMine,
        isSystem = isSystem,
        agentKind = agentKind,
        phase = phase,
        status = status,
        turnId = turnId,
        toolName = toolName,
    )
}
