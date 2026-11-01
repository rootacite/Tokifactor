package com.acite.tokifactor

/**
 * Remove complete `~[text]` spans from chat text before it is drawn.
 *
 * The first `]` ends a span. An unclosed `~[` stays visible. A span that
 * overlaps inline code or a fenced code block stays literal, so the syntax
 * can be quoted. Callers use this only for display; the stored message
 * keeps the raw span.
 */
fun concealHiddenSpans(content: String): String {
    if (!content.contains("~[")) return content
    val protected = protectedIndexes(content)
    val spans = hiddenSpanRanges(content, protected)
    if (spans.isEmpty()) return content
    val pieces = ArrayList<String>(spans.size + 1)
    var cursor = 0
    for ((start, end) in spans) {
        pieces += content.substring(cursor, start)
        cursor = end
    }
    pieces += content.substring(cursor)
    var joined = pieces[0]
    for (index in 1 until pieces.size) {
        joined = joinAroundRemoval(joined, pieces[index])
    }
    return joined
}

private val inlineCodeRegex = Regex("`[^`\\n]*`")
private val fenceRegex = Regex("(?m)^\\s*(```|~~~).*$")

private fun protectedIndexes(content: String): BooleanArray {
    val protected = BooleanArray(content.length)
    for (match in inlineCodeRegex.findAll(content)) {
        for (index in match.range) protected[index] = true
    }
    var fenceStart = -1
    for (match in fenceRegex.findAll(content)) {
        if (fenceStart < 0) {
            fenceStart = match.range.first
        } else {
            for (index in fenceStart until match.range.last + 1) protected[index] = true
            fenceStart = -1
        }
    }
    if (fenceStart >= 0) {
        for (index in fenceStart until content.length) protected[index] = true
    }
    return protected
}

private fun hiddenSpanRanges(content: String, protected: BooleanArray): List<Pair<Int, Int>> {
    val spans = mutableListOf<Pair<Int, Int>>()
    var index = 0
    while (index < content.length) {
        if (protected[index]) {
            index += 1
            continue
        }
        if (content.startsWith("~[", index)) {
            val close = content.indexOf(']', index + 2)
            if (close < 0) break
            if ((index..close).any { protected[it] }) {
                index += 2
                continue
            }
            spans += index to (close + 1)
            index = close + 1
            continue
        }
        index += 1
    }
    return spans
}

private fun isHorizontalSpace(char: Char): Boolean = char == ' ' || char == '\t'

private fun isLineBreak(char: Char): Boolean = char == '\n' || char == '\r'

private fun trimEndHorizontal(text: String): String {
    var end = text.length
    while (end > 0 && isHorizontalSpace(text[end - 1])) end -= 1
    return text.substring(0, end)
}

private fun trimStartHorizontal(text: String): String {
    var start = 0
    while (start < text.length && isHorizontalSpace(text[start])) start += 1
    return text.substring(start)
}

private fun joinAroundRemoval(left: String, right: String): String {
    val leftTrimmed = trimEndHorizontal(left)
    val rightTrimmed = trimStartHorizontal(right)
    val removedLeft = leftTrimmed.length != left.length
    val removedRight = rightTrimmed.length != right.length
    if (!removedLeft && !removedRight) return left + right
    val needSpace = leftTrimmed.isNotEmpty() &&
        rightTrimmed.isNotEmpty() &&
        !isLineBreak(leftTrimmed.last()) &&
        !isLineBreak(rightTrimmed.first())
    return if (needSpace) "$leftTrimmed $rightTrimmed" else leftTrimmed + rightTrimmed
}
