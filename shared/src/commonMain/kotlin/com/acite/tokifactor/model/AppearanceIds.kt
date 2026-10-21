package com.acite.tokifactor.model

object AppearanceIds {
    const val SkySakura = "sky_sakura"
    const val InkPaper = "ink_paper"
    const val MidnightSnow = "midnight_snow"
    const val LilacPeach = "lilac_peach"
    const val Default = SkySakura

    val all: Set<String> = setOf(SkySakura, InkPaper, MidnightSnow, LilacPeach)

    fun normalize(value: String?): String = value?.takeIf { it in all } ?: Default
}
