package com.acite.tokifactor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.acite.tokifactor.model.AppearanceIds

data class Appearance(
    val id: String,
    val label: String,
    val blurb: String,
    val palette: TokiPalette,
) {
    companion object {
        const val DEFAULT_ID = AppearanceIds.Default

        val SkySakura = Appearance(
            id = DEFAULT_ID,
            label = "Sky & Sakura",
            blurb = "Clear-sky blue and cherry blossom",
            palette = TokiPalette.SkySakura,
        )

        val InkPaper = Appearance(
            id = AppearanceIds.InkPaper,
            label = "Ink & Paper",
            blurb = "Deep indigo and sakura paper",
            palette = TokiPalette.InkPaper,
        )

        val MidnightSnow = Appearance(
            id = AppearanceIds.MidnightSnow,
            label = "Midnight & Snow",
            blurb = "Night charcoal and warm snow",
            palette = TokiPalette.MidnightSnow,
        )

        val LilacPeach = Appearance(
            id = AppearanceIds.LilacPeach,
            label = "Lilac & Peach",
            blurb = "Soft lilac and ripe peach",
            palette = TokiPalette.LilacPeach,
        )

        val all: List<Appearance> = listOf(SkySakura, InkPaper, MidnightSnow, LilacPeach)

        fun byId(id: String?): Appearance {
            val normalized = AppearanceIds.normalize(id)
            return all.firstOrNull { it.id == normalized } ?: SkySakura
        }
    }
}

val LocalTokiAppearance = staticCompositionLocalOf { Appearance.SkySakura }
val LocalTokiPalette = staticCompositionLocalOf { TokiPalette.SkySakura }

val tokiAppearance: Appearance
    @Composable get() = LocalTokiAppearance.current

val tokiColors: TokiPalette
    @Composable get() = LocalTokiPalette.current
