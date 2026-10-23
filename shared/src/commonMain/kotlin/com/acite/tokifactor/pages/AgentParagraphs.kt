package com.acite.tokifactor.pages

/**
 * Split an agent reply into display paragraphs at blank lines.
 *
 * Fenced Markdown blocks are kept intact even when they contain blank lines,
 * and text surrounding a fence becomes its own paragraph. The returned strings
 * preserve the original Markdown and line endings except for the blank-line
 * separators between paragraphs.
 */
internal fun splitAgentParagraphs(content: String): List<String> {
    if (content.isBlank()) return listOf(content)
    val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
    val lines = normalized.split('\n')
    val paragraphs = mutableListOf<String>()
    val current = mutableListOf<String>()
    var pendingBlank = false
    var fence: Fence? = null

    fun flush() {
        if (current.any(String::isNotBlank)) {
            paragraphs += current.joinToString("\n")
        }
        current.clear()
        pendingBlank = false
    }

    for (line in lines) {
        val activeFence = fence
        if (activeFence != null) {
            current += line
            if (isClosingFence(line, activeFence)) {
                fence = null
                flush()
            }
            continue
        }

        val openingFence = openingFence(line)
        if (openingFence != null) {
            if (current.any(String::isNotBlank)) flush()
            current += line
            fence = openingFence
            continue
        }

        if (line.isBlank()) {
            pendingBlank = true
            continue
        }
        if (pendingBlank) flush()
        current += line
    }
    flush()
    return if (paragraphs.size > 1) paragraphs else listOf(content)
}

private data class Fence(val character: Char, val length: Int)

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
