package com.acite.tokifactor.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TokiBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = tokiColors
    Box(
        modifier
            .fillMaxSize()
            .background(colors.bgApp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun orb(center: Offset, radius: Float, color: Color, core: Float, mid: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = core),
                            color.copy(alpha = mid),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
            orb(
                center = Offset(60.dp.toPx(), 80.dp.toPx()),
                radius = 250.dp.toPx(),
                color = colors.accentPink,
                core = 0.26f,
                mid = 0.08f,
            )
            orb(
                center = Offset(size.width - 40.dp.toPx(), 190.dp.toPx()),
                radius = 230.dp.toPx(),
                color = colors.accentBlue,
                core = 0.22f,
                mid = 0.07f,
            )
            orb(
                center = Offset(90.dp.toPx(), size.height - 70.dp.toPx()),
                radius = 220.dp.toPx(),
                color = colors.accentLilac,
                core = 0.18f,
                mid = 0.06f,
            )
        }
        content()
    }
}
