package com.acite.tokifactor.pages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acite.tokifactor.ui.theme.tokiColors
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme

data class MathSpan(
    val start: Int,
    val end: Int,
    val raw: String,
    val formula: String,
    val isBlock: Boolean,
)

sealed class BlockMathSegment {
    data class Text(val text: String) : BlockMathSegment()
    data class Math(val formula: String) : BlockMathSegment()
}

private val BLOCK_MATH_DOUBLE_DOLLAR = Regex("""(?<!\\)\$\$(.+?)(?<!\\)\$\$""", RegexOption.DOT_MATCHES_ALL)
private val BLOCK_MATH_BRACKET = Regex("""(?<!\\)\\\[(.+?)(?<!\\)\\\]""", RegexOption.DOT_MATCHES_ALL)
private val INLINE_MATH_PAREN = Regex("""(?<!\\)\\\((.+?)(?<!\\)\\\)""", RegexOption.DOT_MATCHES_ALL)
private val INLINE_MATH_DOLLAR = Regex("""(?<![\$\\])\$(?!\s)([^$\n]+?)(?<![\s\\])\$(?!\$)""")
private val CODE_SPAN_REGEX = Regex("""(?<!\\)`([^`\n]+?)`""")

/**
 * Remove enclosing math delimiters (e.g. $, $$, \[, \( ) from a LaTeX formula string.
 */
internal fun stripMathDelimiters(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length >= 4 ->
            trimmed.substring(2, trimmed.length - 2).trim()
        trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length >= 4 ->
            trimmed.substring(2, trimmed.length - 2).trim()
        trimmed.startsWith("\\(") && trimmed.endsWith("\\)") && trimmed.length >= 4 ->
            trimmed.substring(2, trimmed.length - 2).trim()
        trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length >= 2 ->
            trimmed.substring(1, trimmed.length - 1).trim()
        else -> trimmed
    }
}

/**
 * Check if the text is entirely a standalone formula (e.g. "$formula$" or "$$formula$$").
 */
internal fun isStandaloneMath(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length < 2) return false
    if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length >= 4) {
        val inner = trimmed.substring(2, trimmed.length - 2)
        return !inner.contains("$$")
    }
    if (trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length >= 4) {
        return true
    }
    if (trimmed.startsWith("\\(") && trimmed.endsWith("\\)") && trimmed.length >= 4) {
        return true
    }
    if (trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length >= 2) {
        val inner = trimmed.substring(1, trimmed.length - 1)
        return !inner.contains("$") && inner.isNotBlank()
    }
    return false
}

/**
 * Find all math spans within text, ignoring any math syntax placed inside inline code spans.
 */
internal fun findMathSpans(text: String): List<MathSpan> {
    if (text.isEmpty() || (!text.contains('$') && !text.contains("\\(") && !text.contains("\\["))) {
        return emptyList()
    }

    val codeRanges = CODE_SPAN_REGEX.findAll(text).map { it.range }.toList()
    fun isInsideCode(range: IntRange): Boolean =
        codeRanges.any { it.first <= range.first && range.last <= it.last }

    val spans = mutableListOf<MathSpan>()

    // 1. Block math $$...$$
    for (match in BLOCK_MATH_DOUBLE_DOLLAR.findAll(text)) {
        if (!isInsideCode(match.range)) {
            val content = match.groupValues[1].trim()
            if (content.isNotEmpty()) {
                spans += MathSpan(
                    start = match.range.first,
                    end = match.range.last + 1,
                    raw = match.value,
                    formula = content,
                    isBlock = true,
                )
            }
        }
    }

    // 2. Block math \[...\]
    for (match in BLOCK_MATH_BRACKET.findAll(text)) {
        val range = match.range
        if (!isInsideCode(range) && spans.none { it.start <= range.first && range.last < it.end }) {
            val content = match.groupValues[1].trim()
            if (content.isNotEmpty()) {
                spans += MathSpan(
                    start = range.first,
                    end = range.last + 1,
                    raw = match.value,
                    formula = content,
                    isBlock = true,
                )
            }
        }
    }

    // 3. Inline math \(...\)
    for (match in INLINE_MATH_PAREN.findAll(text)) {
        val range = match.range
        if (!isInsideCode(range) && spans.none { it.start <= range.first && range.last < it.end }) {
            val content = match.groupValues[1].trim()
            if (content.isNotEmpty()) {
                spans += MathSpan(
                    start = range.first,
                    end = range.last + 1,
                    raw = match.value,
                    formula = content,
                    isBlock = false,
                )
            }
        }
    }

    // 4. Inline math $...$
    for (match in INLINE_MATH_DOLLAR.findAll(text)) {
        val range = match.range
        if (!isInsideCode(range) && spans.none { it.start <= range.first && range.last < it.end }) {
            val content = match.groupValues[1].trim()
            if (content.isNotEmpty()) {
                spans += MathSpan(
                    start = range.first,
                    end = range.last + 1,
                    raw = match.value,
                    formula = content,
                    isBlock = false,
                )
            }
        }
    }

    return spans.sortedBy { it.start }
}

/**
 * Returns true if text contains any recognized math formulas.
 */
internal fun hasMath(text: String): Boolean {
    if (isStandaloneMath(text)) return true
    return findMathSpans(text).isNotEmpty()
}

/**
 * Split text by block math spans into text and block math segments.
 */
internal fun splitBlockMathSegments(text: String): List<BlockMathSegment> {
    val blockSpans = findMathSpans(text).filter { it.isBlock }
    if (blockSpans.isEmpty()) {
        return listOf(BlockMathSegment.Text(text))
    }

    val segments = mutableListOf<BlockMathSegment>()
    var cursor = 0
    for (span in blockSpans) {
        if (span.start > cursor) {
            val leading = text.substring(cursor, span.start)
            if (leading.isNotEmpty()) {
                segments += BlockMathSegment.Text(leading)
            }
        }
        segments += BlockMathSegment.Math(span.formula)
        cursor = span.end
    }
    if (cursor < text.length) {
        val trailing = text.substring(cursor)
        if (trailing.isNotEmpty()) {
            segments += BlockMathSegment.Text(trailing)
        }
    }
    return segments
}

/**
 * Composable that renders a display/block LaTeX formula.
 */
@Composable
internal fun MathBlock(
    latex: String,
    modifier: Modifier = Modifier,
    color: Color = tokiColors.text,
    fontSize: TextUnit = 17.sp,
) {
    val cleaned = remember(latex) { stripMathDelimiters(latex) }
    val theme = remember(color) {
        LatexTheme.dark(
            color = color,
            backgroundColor = Color.Transparent,
        )
    }
    val config = remember(fontSize, theme) {
        LatexConfig(
            fontSize = fontSize,
            theme = theme,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            Latex(
                latex = cleaned,
                config = config,
            )
        }
    }
}
