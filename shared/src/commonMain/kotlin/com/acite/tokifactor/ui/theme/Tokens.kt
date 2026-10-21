package com.acite.tokifactor.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

data class TokiTokens(
    val radiusCard: Int = 22,
    val radiusPanel: Int = 20,
    val radiusBubble: Int = 18,
    val radiusSheet: Int = 26,
    val motionMs: Int = 160,
) {
    val card = RoundedCornerShape(radiusCard.dp)
    val panel = RoundedCornerShape(radiusPanel.dp)
    val bubble = RoundedCornerShape(radiusBubble.dp)
    val sheet = RoundedCornerShape(radiusSheet.dp)
    val capsule = RoundedCornerShape(50)
}

val LocalTokiTokens = staticCompositionLocalOf { TokiTokens() }

val tokiTokens: TokiTokens
    @Composable get() = LocalTokiTokens.current

val TokiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object TokiMotion {
    fun <T> softSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
