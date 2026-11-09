package com.acite.tokifactor.pages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MathRenderingTest {
    @Test
    fun testVanDerWaalsStandalone() {
        val formula = "$(P + \\frac{a}{V^2})(V - b) = RT$"
        assertTrue(isStandaloneMath(formula))
        assertEquals("(P + \\frac{a}{V^2})(V - b) = RT", stripMathDelimiters(formula))

        val spans = findMathSpans(formula)
        assertEquals(1, spans.size)
        assertEquals("(P + \\frac{a}{V^2})(V - b) = RT", spans[0].formula)
        assertEquals(false, spans[0].isBlock)
    }

    @Test
    fun testVanDerWaalsDoubleDollar() {
        val formula = "$$(P + \\frac{a}{V^2})(V - b) = RT$$"
        assertTrue(isStandaloneMath(formula))
        assertEquals("(P + \\frac{a}{V^2})(V - b) = RT", stripMathDelimiters(formula))

        val spans = findMathSpans(formula)
        assertEquals(1, spans.size)
        assertEquals("(P + \\frac{a}{V^2})(V - b) = RT", spans[0].formula)
        assertEquals(true, spans[0].isBlock)
    }

    @Test
    fun testMixedTextWithInlineMath() {
        val text = "范德华方程是 $(P + \\frac{a}{V^2})(V - b) = RT$ ，由范德华提出。"
        assertFalse(isStandaloneMath(text))
        assertTrue(hasMath(text))

        val spans = findMathSpans(text)
        assertEquals(1, spans.size)
        assertEquals("(P + \\frac{a}{V^2})(V - b) = RT", spans[0].formula)
        assertEquals(false, spans[0].isBlock)
    }

    @Test
    fun testMixedTextWithBlockMath() {
        val text = "这是范德华方程：\n$$(P + \\frac{a}{V^2})(V - b) = RT$$\n这是结论。"
        val segments = splitBlockMathSegments(text)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is BlockMathSegment.Text)
        assertEquals("这是范德华方程：\n", (segments[0] as BlockMathSegment.Text).text)
        assertTrue(segments[1] is BlockMathSegment.Math)
        assertEquals("(P + \\frac{a}{V^2})(V - b) = RT", (segments[1] as BlockMathSegment.Math).formula)
        assertTrue(segments[2] is BlockMathSegment.Text)
        assertEquals("\n这是结论。", (segments[2] as BlockMathSegment.Text).text)
    }

    @Test
    fun testCurrencyIsNotMath() {
        val text = "价格是 $100 美元，不是 $200 美元。"
        assertFalse(hasMath(text))
        val spans = findMathSpans(text)
        assertEquals(0, spans.size)
    }

    @Test
    fun testCodeSpanIsNotMath() {
        val text = "在 LaTeX 中，我们输入 `$(P + \\frac{a}{V^2})(V - b) = RT$` 作为公式。"
        val spans = findMathSpans(text)
        assertEquals(0, spans.size)
    }

    @Test
    fun testMultipleInlineFormulas() {
        val text = "当 \$x > 0\$ 且 \$y < 0\$ 时，方程 \$x + y = 1\$ 成立。"
        val spans = findMathSpans(text)
        assertEquals(3, spans.size)
        assertEquals("x > 0", spans[0].formula)
        assertEquals("y < 0", spans[1].formula)
        assertEquals("x + y = 1", spans[2].formula)
    }
}
