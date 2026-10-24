package com.acite.tokifactor.pages

internal fun defaultAttachmentName(existing: List<String>): String {
    val used = existing.map { it.trim().lowercase() }.toSet()
    var index = 1
    while ("图片$index" in used) index += 1
    return "图片$index"
}

internal fun normalizeAttachmentNames(names: List<String>): List<String> {
    val used = mutableSetOf<String>()
    return names.mapIndexed { index, raw ->
        val base = raw.trim().ifBlank { "图片${index + 1}" }.take(64)
        var candidate = base
        var suffix = 2
        while (candidate.lowercase() in used) {
            candidate = "$base ($suffix)"
            suffix += 1
        }
        used += candidate.lowercase()
        candidate
    }
}
