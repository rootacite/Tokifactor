package com.acite.tokifactor.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.acite.tokifactor.model.SoloCharacter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tokifactor.shared.generated.resources.Res
import tokifactor.shared.generated.resources.solo_aki
import tokifactor.shared.generated.resources.solo_fuyu
import tokifactor.shared.generated.resources.solo_haru
import tokifactor.shared.generated.resources.solo_natsu

val SoloCharacter.palette: TokiPalette
    get() = when (this) {
        SoloCharacter.Aki -> TokiPalette.SoloAki
        SoloCharacter.Fuyu -> TokiPalette.SoloFuyu
        SoloCharacter.Haru -> TokiPalette.SoloHaru
        SoloCharacter.Natsu -> TokiPalette.SoloNatsu
    }

val SoloCharacter.appearance: Appearance
    get() = Appearance(
        id = "solo_$key",
        label = "Solo · $label",
        blurb = when (this) {
            SoloCharacter.Aki -> "Sakura pink private room"
            SoloCharacter.Fuyu -> "Pale gray-blue winter light"
            SoloCharacter.Haru -> "Fresh green spring studio"
            SoloCharacter.Natsu -> "Deep red night console"
        },
        palette = palette,
    )

val SoloCharacter.artwork: DrawableResource
    get() = when (this) {
        SoloCharacter.Aki -> Res.drawable.solo_aki
        SoloCharacter.Fuyu -> Res.drawable.solo_fuyu
        SoloCharacter.Haru -> Res.drawable.solo_haru
        SoloCharacter.Natsu -> Res.drawable.solo_natsu
    }

@Composable
fun SoloBackgroundImage(
    character: SoloCharacter,
    customBytes: ByteArray?,
    modifier: Modifier = Modifier,
) {
    if (customBytes != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(customBytes)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(character.artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
