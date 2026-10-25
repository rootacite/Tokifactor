package com.acite.tokifactor.pages

/**
 * Split a message into display paragraphs at blank lines.
 *
 * Blank lines only separate top-level blocks. Markdown lists, fenced and
 * indented code blocks, and block quotes keep their internal blank lines.
 * The returned strings preserve the original Markdown and line endings except
 * for the blank-line separators between paragraphs.
 */
internal fun splitMessageParagraphs(content: String): List<String> {
    if (content.isBlank()) return listOf(content)
    val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split('\n')
    val paragraphs = mutableListOf<String>()
    val current = mutableListOf<String>()
    val pendingBlanks = mutableListOf<String>()
    var fence: Fence? = null
    var listIndent: Int? = null
    var quoteActive = false
    var indentedCodeActive = false

    fun flush() {
        if (current.any(String::isNotBlank)) {
            paragraphs += current.joinToString("\n")
        }
        current.clear()
        pendingBlanks.clear()
        listIndent = null
        quoteActive = false
        indentedCodeActive = false
    }

    fun continuesProtectedBlock(line: String): Boolean {
        val rootIndent = listIndent
        if (rootIndent != null &&
            (listMarker(line) != null || leadingIndent(line) > rootIndent)
        ) {
            return true
        }
        if (quoteActive && isBlockQuote(line)) return true
        if (indentedCodeActive && isIndentedCode(line)) return true
        return false
    }

    fun updateBlockState(line: String) {
        val marker = listMarker(line)
        if (marker != null) {
            listIndent = minOf(listIndent ?: marker.indent, marker.indent)
        }
        quoteActive = isBlockQuote(line)
        indentedCodeActive = isIndentedCode(line)
    }

    for (line in lines) {
        val activeFence = fence
        if (activeFence != null) {
            current += line
            if (isClosingFence(line, activeFence)) {
                fence = null
            }
            continue
        }

        val openingFence = openingFence(line)
        if (openingFence != null) {
            if (pendingBlanks.isNotEmpty()) {
                if (continuesProtectedBlock(line)) {
                    current += pendingBlanks
                } else {
                    flush()
                }
                pendingBlanks.clear()
            }
            current += line
            fence = openingFence
            continue
        }

        if (line.isBlank()) {
            pendingBlanks += line
            continue
        }

        if (pendingBlanks.isNotEmpty()) {
            if (continuesProtectedBlock(line)) {
                current += pendingBlanks
            } else {
                flush()
            }
            pendingBlanks.clear()
        }
        current += line
        updateBlockState(line)
    }
    flush()
    return if (paragraphs.size > 1) paragraphs else listOf(content)
}

private data class Fence(val character: Char, val length: Int)

private data class ListMarker(val indent: Int)

private fun openingFence(line: String): Fence? {
    val trimmed = line.trimStart()
    val character = trimmed.firstOrNull() ?: return null
    if (character != '`' && character != '~') return null
    val length = trimmed.takeWhile { it == character }.length
    return if (length >= 3) Fence(character, length) else null
}

private fun isClosingFence(line: String, fence: Fence): Boolean {
    val trimmed = line.trimStart()
    val run = trimmed.takeWhile { it == fence.character }.length
    return run >= fence.length && trimmed.drop(run).isBlank()
}

private fun listMarker(line: String): ListMarker? {
    val indent = leadingIndent(line)
    var index = leadingWhitespaceEnd(line)
    if (index >= line.length) return null
    when (val first = line[index]) {
        '-', '*', '+' -> {
            if (index + 1 < line.length && !line[index + 1].isWhitespace()) return null
        }
        in '0'..'9' -> {
            val digitsStart = index
            while (index < line.length && line[index].isDigit()) index += 1
            if (index - digitsStart > 9) return null
            if (index >= line.length || (line[index] != '.' && line[index] != ')')) return null
            if (index + 1 < line.length && !line[index + 1].isWhitespace()) return null
        }
        else -> return null
    }
    return ListMarker(indent)
}

private fun leadingWhitespaceEnd(line: String): Int {
    var index = 0
    while (index < line.length && line[index].isWhitespace()) index += 1
    return index
}

private fun leadingIndent(line: String): Int {
    var width = 0
    var index = 0
    while (index < line.length) {
        when (line[index]) {
            ' ' -> width += 1
            '\t' -> width += 4
            else -> return width
        }
        index += 1
    }
    return width
}

private fun isIndentedCode(line: String): Boolean =
    !line.isBlank() && leadingIndent(line) >= 4

private fun isBlockQuote(line: String): Boolean {
    var index = 0
    while (index < line.length && index < 3 && line[index] == ' ') index += 1
    return index < line.length && line[index] == '>'
}
