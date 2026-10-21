package com.acite.tokifactor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tokifactor.shared.generated.resources.Res
import tokifactor.shared.generated.resources.nunito_bold
import tokifactor.shared.generated.resources.nunito_medium
import tokifactor.shared.generated.resources.nunito_regular
import tokifactor.shared.generated.resources.nunito_semibold

val LocalTokiFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun rememberTokiFontFamily(): FontFamily {
    val regular = Font(Res.font.nunito_regular, FontWeight.Normal)
    val medium = Font(Res.font.nunito_medium, FontWeight.Medium)
    val semibold = Font(Res.font.nunito_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.nunito_bold, FontWeight.Bold)
    return remember(regular, medium, semibold, bold) {
        FontFamily(regular, medium, semibold, bold)
    }
}

fun tokiTypography(fontFamily: FontFamily, scale: Float): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.style(fontFamily, FontWeight.SemiBold, 42, 46, (-0.8).sp).scaled(scale),
        displayMedium = base.displayMedium.style(fontFamily, FontWeight.SemiBold, 36, 42).scaled(scale),
        displaySmall = base.displaySmall.style(fontFamily, FontWeight.SemiBold, 30, 36).scaled(scale),
        headlineLarge = base.headlineLarge.style(fontFamily, FontWeight.SemiBold, 28, 34).scaled(scale),
        headlineMedium = base.headlineMedium.style(fontFamily, FontWeight.SemiBold, 24, 30).scaled(scale),
        headlineSmall = base.headlineSmall.style(fontFamily, FontWeight.SemiBold, 20, 26).scaled(scale),
        titleLarge = base.titleLarge.style(fontFamily, FontWeight.SemiBold, 18, 24).scaled(scale),
        titleMedium = base.titleMedium.style(fontFamily, FontWeight.Medium, 16, 22).scaled(scale),
        titleSmall = base.titleSmall.style(fontFamily, FontWeight.Medium, 14, 20).scaled(scale),
        bodyLarge = base.bodyLarge.style(fontFamily, FontWeight.Normal, 16, 23).scaled(scale),
        bodyMedium = base.bodyMedium.style(fontFamily, FontWeight.Normal, 14, 20).scaled(scale),
        bodySmall = base.bodySmall.style(fontFamily, FontWeight.Normal, 12, 17).scaled(scale),
        labelLarge = base.labelLarge.style(fontFamily, FontWeight.Medium, 14, 18).scaled(scale),
        labelMedium = base.labelMedium.style(fontFamily, FontWeight.Medium, 12, 16, 0.2.sp).scaled(scale),
        labelSmall = base.labelSmall.style(fontFamily, FontWeight.Medium, 11, 15, 0.4.sp).scaled(scale),
    )
}

private fun TextStyle.style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: TextUnit = this.letterSpacing,
): TextStyle = copy(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing,
)

private fun TextStyle.scaled(scale: Float): TextStyle {
    if (scale == 1f) return this
    return copy(
        fontSize = fontSize.scaled(scale),
        lineHeight = lineHeight.scaled(scale),
        letterSpacing = letterSpacing.scaled(scale),
    )
}

private fun TextUnit.scaled(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * scale
