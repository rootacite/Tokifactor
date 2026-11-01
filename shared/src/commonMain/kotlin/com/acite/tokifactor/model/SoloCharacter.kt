package com.acite.tokifactor.model

enum class SoloCharacter(
    val key: String,
    val label: String,
    val aliases: List<String>,
) {
    Aki("aki", "Aki", listOf("Aki", "aki", "秋", "小秋", "秋（aki）")),
    Fuyu("fuyu", "Fuyu", listOf("Fuyu", "fuyu", "冬")),
    Haru("haru", "Haru", listOf("Haru", "haru", "春")),
    Natsu("natsu", "Natsu", listOf("Natsu", "natsu", "夏"));

    companion object {
        fun resolve(name: String, id: String = ""): SoloCharacter? {
            val canonicalName = canonicalMemberName(name)
            if (canonicalName.isNotEmpty()) {
                entries.firstOrNull { character ->
                    character.aliases.any { canonicalMemberName(it) == canonicalName }
                }?.let { return it }
            }
            val targetId = id.trim()
            if (targetId.isNotEmpty()) {
                return entries.firstOrNull { legacyMemberId(it.label) == targetId }
            }
            return null
        }
    }
}
