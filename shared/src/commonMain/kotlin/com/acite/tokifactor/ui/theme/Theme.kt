package com.acite.tokifactor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun TokiFactorTheme(
    appearance: Appearance = Appearance.SkySakura,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val palette = appearance.palette
    val fontFamily = rememberTokiFontFamily()
    val typography = remember(fontFamily, fontScale) { tokiTypography(fontFamily, fontScale) }
    val colorScheme = remember(palette) {
        darkColorScheme(
            primary = palette.accentPink,
            onPrimary = palette.onAccent,
            primaryContainer = palette.accentPink.copy(alpha = 0.18f),
            onPrimaryContainer = palette.text,
            secondary = palette.accentBlue,
            onSecondary = palette.bgApp,
            secondaryContainer = palette.accentBlue.copy(alpha = 0.15f),
            onSecondaryContainer = palette.text,
            tertiary = palette.accentLilac,
            onTertiary = palette.bgApp,
            tertiaryContainer = palette.accentLilac.copy(alpha = 0.14f),
            onTertiaryContainer = palette.text,
            background = palette.bgApp,
            onBackground = palette.text,
            surface = palette.bgPanel,
            onSurface = palette.text,
            surfaceVariant = palette.bgCard,
            onSurfaceVariant = palette.textDim,
            surfaceTint = Color.Transparent,
            inverseSurface = palette.text,
            inverseOnSurface = palette.bgApp,
            inversePrimary = palette.accentPink,
            error = palette.error,
            onError = palette.onError,
            errorContainer = palette.errorContainer,
            onErrorContainer = palette.onErrorContainer,
            outline = palette.stroke,
            outlineVariant = palette.stroke.copy(alpha = 0.58f),
            scrim = Color.Black,
            surfaceBright = palette.bgCard,
            surfaceDim = palette.bgApp,
            surfaceContainerLowest = palette.bgApp,
            surfaceContainerLow = palette.bgPanel,
            surfaceContainer = palette.bgPanel,
            surfaceContainerHigh = palette.bgCard,
            surfaceContainerHighest = palette.bgCard,
        )
    }

    ApplySystemBars(darkTheme = true)
    CompositionLocalProvider(
        LocalTokiAppearance provides appearance,
        LocalTokiPalette provides palette,
        LocalTokiFontFamily provides fontFamily,
        LocalTokiTokens provides TokiTokens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = TokiShapes,
            content = content,
        )
    }
}

@Composable
internal expect fun ApplySystemBars(darkTheme: Boolean)
