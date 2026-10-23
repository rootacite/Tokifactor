package com.acite.tokifactor.pages

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentParagraphsTest {
    @Test
    fun splitsAtBlankLines() {
        assertEquals(
            listOf("First paragraph.", "Second paragraph."),
            splitAgentParagraphs("First paragraph.\n\nSecond paragraph."),
        )
    }

    @Test
    fun collapsesConsecutiveBlankLines() {
        assertEquals(
            listOf("One", "Two", "Three"),
            splitAgentParagraphs("One\n\n\nTwo\n  \nThree"),
        )
    }

    @Test
    fun normalizesCrLfOnlyWhenSplitting() {
        assertEquals(
            listOf("One", "Two"),
            splitAgentParagraphs("One\r\n\r\nTwo"),
        )
    }

    @Test
    fun keepsBlankLinesInsideFencedCode() {
        val content = "Before\n\n```kotlin\nval one = 1\n\nval two = 2\n```\n\nAfter"

        assertEquals(
            listOf(
                "Before",
                "```kotlin\nval one = 1\n\nval two = 2\n```",
                "After",
            ),
            splitAgentParagraphs(content),
        )
    }

    @Test
    fun keepsSingleParagraphAndFenceExactlyAsReceived() {
        val single = "A single paragraph with  spacing."
        val code = "```\nfirst\n\nsecond\n```"
        val longerFence = "````\n```\n\nstill code\n````"

        assertEquals(listOf(single), splitAgentParagraphs(single))
        assertEquals(listOf(code), splitAgentParagraphs(code))
        assertEquals(listOf(longerFence), splitAgentParagraphs(longerFence))
    }
}
