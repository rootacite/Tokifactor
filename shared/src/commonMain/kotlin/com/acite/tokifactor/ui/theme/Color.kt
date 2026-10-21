package com.acite.tokifactor.ui.theme

import androidx.compose.ui.graphics.Color

data class TokiPalette(
    val bgApp: Color,
    val bgPanel: Color,
    val bgCard: Color,
    val stroke: Color,
    val text: Color,
    val textDim: Color,
    val accentPink: Color,
    val accentBlue: Color,
    val accentLilac: Color,
    val onAccent: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val qualityMint: Color = Color(0xFF7BC67E),
    val qualityGreen: Color = Color(0xFF3DAA6D),
    val qualityYellow: Color = Color(0xFFF2C14E),
    val qualityOrange: Color = Color(0xFFF08A3A),
    val qualityRed: Color = Color(0xFFE85D4C),
) {
    companion object {
        val SkySakura = TokiPalette(
            bgApp = Color(0xFF12101A),
            bgPanel = Color(0xFF1B1730),
            bgCard = Color(0xFF252042),
            stroke = Color(0xFF3A3460),
            text = Color(0xFFF4F0FF),
            textDim = Color(0xFFA89BC8),
            accentPink = Color(0xFFFF6BA8),
            accentBlue = Color(0xFF7AB8FF),
            accentLilac = Color(0xFFC9B6FF),
            onAccent = Color(0xFF1B1028),
            error = Color(0xFFFF8B9C),
            onError = Color(0xFF310811),
            errorContainer = Color(0xFF511525),
            onErrorContainer = Color(0xFFFFDCE1),
        )

        val InkPaper = TokiPalette(
            bgApp = Color(0xFF100E18),
            bgPanel = Color(0xFF1A1630),
            bgCard = Color(0xFF262044),
            stroke = Color(0xFF3E3662),
            text = Color(0xFFF4F0FF),
            textDim = Color(0xFFB0A3C8),
            accentPink = Color(0xFFE8B7D2),
            accentBlue = Color(0xFF7A8ED4),
            accentLilac = Color(0xFF8E7AC8),
            onAccent = Color(0xFF21152C),
            error = Color(0xFFFF9A9F),
            onError = Color(0xFF35090D),
            errorContainer = Color(0xFF541A20),
            onErrorContainer = Color(0xFFFFDADB),
        )

        val MidnightSnow = TokiPalette(
            bgApp = Color(0xFF0E0D12),
            bgPanel = Color(0xFF16151C),
            bgCard = Color(0xFF22212A),
            stroke = Color(0xFF3A3848),
            text = Color(0xFFF6F1EA),
            textDim = Color(0xFFA8A0B8),
            accentPink = Color(0xFFE8D5B5),
            accentBlue = Color(0xFF8AA0C8),
            accentLilac = Color(0xFFA8A0B8),
            onAccent = Color(0xFF241C11),
            error = Color(0xFFFF9E9E),
            onError = Color(0xFF390B0B),
            errorContainer = Color(0xFF561C1C),
            onErrorContainer = Color(0xFFFFDAD9),
        )

        val LilacPeach = TokiPalette(
            bgApp = Color(0xFF14101C),
            bgPanel = Color(0xFF221A30),
            bgCard = Color(0xFF2E2444),
            stroke = Color(0xFF4A3A62),
            text = Color(0xFFF8F0FF),
            textDim = Color(0xFFC4B0D4),
            accentPink = Color(0xFFFF9B7A),
            accentBlue = Color(0xFFB39BFF),
            accentLilac = Color(0xFFFFC4A8),
            onAccent = Color(0xFF2B130C),
            error = Color(0xFFFF97A4),
            onError = Color(0xFF3A0910),
            errorContainer = Color(0xFF581723),
            onErrorContainer = Color(0xFFFFDCE0),
        )
    }
}
