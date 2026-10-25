package com.acite.tokifactor.pages

import kotlin.test.Test
import kotlin.test.assertEquals

class PartialComposerTest {
    @Test
    fun joinsFragmentsWithOneBlankLine() {
        assertEquals(
            "第一段\n\n第二段\n\n第三段",
            assemblePartialMessage(listOf("第一段", "第二段"), "第三段"),
        )
    }

    @Test
    fun trimsFragmentsAndSkipsEmptyOnes() {
        assertEquals(
            "one\n\ntwo",
            assemblePartialMessage(listOf("  one  ", "   ", "", "two"), "  "),
        )
    }

    @Test
    fun preservesBlankLinesInsideAFragment() {
        assertEquals(
            "a\n\nb\n\nc",
            assemblePartialMessage(listOf("a\n\nb"), "c"),
        )
    }

    @Test
    fun emptyDraftStaysEmpty() {
        assertEquals("", assemblePartialMessage(emptyList(), "   "))
    }
}
