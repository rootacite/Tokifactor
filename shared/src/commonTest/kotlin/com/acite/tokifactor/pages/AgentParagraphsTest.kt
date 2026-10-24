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
    fun keepsFencedCodeAttachedToTheListItemAboveIt() {
        val content = "Evidence:\n\n" +
            "1. approval passed\n" +
            "2. image tool returned:\n" +
            "```\n" +
            "image generated\n" +
            "```\n" +
            "3. send tool returned:\n" +
            "```\n" +
            "sent image\n" +
            "```\n\n" +
            "Done."

        assertEquals(
            listOf(
                "Evidence:",
                "1. approval passed\n" +
                    "2. image tool returned:\n" +
                    "```\n" +
                    "image generated\n" +
                    "```\n" +
                    "3. send tool returned:\n" +
                    "```\n" +
                    "sent image\n" +
                    "```",
                "Done.",
            ),
            splitAgentParagraphs(content),
        )
    }

    @Test
    fun keepsLooseOrderedListTogether() {
        val content = "Intro\n\n" +
            "1. first\n\n" +
            "2. second\n\n" +
            "   continuation\n\n" +
            "3. third\n\n" +
            "Outro"

        assertEquals(
            listOf(
                "Intro",
                "1. first\n\n" +
                    "2. second\n\n" +
                    "   continuation\n\n" +
                    "3. third",
                "Outro",
            ),
            splitAgentParagraphs(content),
        )
    }

    @Test
    fun keepsLooseBulletListWithNestedCodeTogether() {
        val content = "Intro\n\n" +
            "- first\n\n" +
            "  ```\n" +
            "  code\n\n" +
            "  more code\n" +
            "  ```\n\n" +
            "- second\n\n" +
            "Outro"

        assertEquals(
            listOf(
                "Intro",
                "- first\n\n" +
                    "  ```\n" +
                    "  code\n\n" +
                    "  more code\n" +
                    "  ```\n\n" +
                    "- second",
                "Outro",
            ),
            splitAgentParagraphs(content),
        )
    }

    @Test
    fun keepsBlankLinesInsideIndentedCode() {
        val content = "Before\n\n    first\n\n    second\n\nAfter"

        assertEquals(
            listOf("Before", "    first\n\n    second", "After"),
            splitAgentParagraphs(content),
        )
    }

    @Test
    fun keepsBlankLinesInsideBlockQuote() {
        val content = "Before\n\n> first\n\n> second\n\nAfter"

        assertEquals(
            listOf("Before", "> first\n\n> second", "After"),
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
