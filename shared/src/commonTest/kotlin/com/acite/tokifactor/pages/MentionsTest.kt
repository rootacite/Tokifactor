package com.acite.tokifactor.pages

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.SoloCharacter
import com.acite.tokifactor.model.legacyMemberId
import com.acite.tokifactor.ui.theme.TokiPalette
import com.acite.tokifactor.ui.theme.palette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MentionsTest {
    private val aki = AgentStatus(
        present = true,
        memberId = legacyMemberId("Aki"),
        memberName = "Aki",
        aliases = "秋,小秋",
    )
    private val fuyu = AgentStatus(
        present = true,
        memberId = legacyMemberId("Fuyu"),
        memberName = "Fuyu",
        aliases = "冬",
    )
    private val targets = buildMentionTargets(
        members = listOf(aki, fuyu),
        selfId = "mem_self",
        selfName = "Acite",
    )

    @Test
    fun findsNamedMentionsAndIgnoresEmailAddresses() {
        val spans = findMentionSpans("hi @Aki, ping @fuyu. mail a@Aki.com", targets)
        assertEquals(listOf("@Aki", "@fuyu"), spans.map { it.label })
        assertEquals(aki.memberId, spans[0].target.memberId)
        assertEquals(fuyu.memberId, spans[1].target.memberId)
    }

    @Test
    fun resolvesAliasesAndThemeCharacters() {
        val spans = findMentionSpans("@秋 在吗", targets)
        assertEquals(1, spans.size)
        assertEquals(SoloCharacter.Aki, spans.single().target.character)
    }

    @Test
    fun leavesMentionsInsideCodeLiteral() {
        assertEquals(emptyList(), findMentionSpans("`@Aki`", targets))
        assertEquals(emptyList(), findMentionSpans("```\n@Aki\n```", targets))
    }

    @Test
    fun picksTheLatestAgentTurnReply() {
        val messages = listOf(
            ChatMessage(id = "u1", content = "hello", pic = null),
            ChatMessage(
                id = "a1",
                content = "first",
                pic = null,
                sender = "Aki",
                senderId = aki.memberId,
                agentKind = "turn",
                phase = "final",
            ),
            ChatMessage(
                id = "a2",
                content = "second",
                pic = null,
                sender = "Fuyu",
                senderId = fuyu.memberId,
                agentKind = "turn",
                phase = "progress",
            ),
            ChatMessage(
                id = "t1",
                content = "tool",
                pic = null,
                sender = "Fuyu",
                senderId = fuyu.memberId,
                agentKind = "tool",
            ),
        )
        assertEquals(fuyu.memberId, lastRespondingAgent(messages, targets)?.memberId)
    }

    @Test
    fun completesABareAtSignWithTheLastResponder() {
        val completion = completeLastAgentMention(
            previous = "hello ",
            next = "hello @",
            cursor = 7,
            target = targets.first { it.memberId == aki.memberId },
        )
        assertEquals("hello @Aki ", completion?.text)
        assertEquals(11, completion?.cursor)
    }

    @Test
    fun doesNotCompleteInsideWordsOrWhenNoTargetExists() {
        assertNull(
            completeLastAgentMention(
                previous = "a",
                next = "a@",
                cursor = 2,
                target = targets.first { it.memberId == aki.memberId },
            ),
        )
        assertNull(completeLastAgentMention("hello ", "hello @", 7, null))
    }

    @Test
    fun targetsCarryEachAgentsThemeColorSource() {
        assertTrue(targets.any { it.character == SoloCharacter.Aki })
        assertTrue(targets.any { it.character == SoloCharacter.Fuyu })
        assertTrue(targets.any { it.character == SoloCharacter.Haru })
        assertTrue(targets.any { it.character == SoloCharacter.Natsu })
    }

    @Test
    fun inputMentionsReceiveTheirThemeSpan() {
        val transformed = MentionVisualTransformation(targets, TokiPalette.SkySakura)
            .filter(AnnotatedString("hi @Aki"))
        val style = transformed.text.spanStyles.single()
        assertEquals(3, style.start)
        assertEquals(7, style.end)
        assertEquals(SoloCharacter.Aki.palette.accentPink, style.item.color)
        assertTrue(style.item.background != Color.Unspecified)
    }
}
