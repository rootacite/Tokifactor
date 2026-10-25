package com.acite.tokifactor.pages

/**
 * Assemble locally staged message fragments into one protocol message.
 *
 * Empty fragments are ignored. Every kept fragment is trimmed, then joined
 * with exactly one blank line so the receiver can render separate bubbles
 * while still treating the payload as one message.
 */
internal fun assemblePartialMessage(
    fragments: List<String>,
    trailingInput: String = "",
): String =
    (fragments + trailingInput)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("\n\n")
