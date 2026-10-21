package com.acite.tokifactor.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TokiSurface(
    modifier: Modifier = Modifier,
    shape: Shape = tokiTokens.panel,
    color: Color = tokiColors.bgPanel.copy(alpha = 0.72f),
    borderColor: Color = Color.White.copy(alpha = 0.10f),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(color)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun TokiBubbleSurface(
    isMine: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = tokiTokens.bubble,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = tokiColors
    val background = if (isMine) {
        Brush.linearGradient(
            listOf(
                colors.accentPink.copy(alpha = 0.28f),
                colors.accentLilac.copy(alpha = 0.16f),
            ),
        )
    } else {
        SolidColor(colors.bgCard.copy(alpha = 0.72f))
    }
    val border = if (isMine) {
        colors.accentPink.copy(alpha = 0.40f)
    } else {
        colors.stroke.copy(alpha = 0.55f)
    }
    Box(
        modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape),
        content = content,
    )
}

@Composable
fun TokiSection(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = tokiColors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.textDim)
        }
        TokiSurface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.bgPanel.copy(alpha = 0.66f),
            borderColor = Color.White.copy(alpha = 0.09f),
            contentPadding = PaddingValues(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun TokiChoiceRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = tokiColors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) colors.accentPink.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.04f),
            )
            .border(
                1.dp,
                if (selected) colors.accentPink.copy(alpha = 0.34f)
                else Color.White.copy(alpha = 0.07f),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun TokiCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    accent: Color = tokiColors.accentPink,
) {
    val colors = tokiColors
    val shape = tokiTokens.capsule
    val container = when {
        !enabled -> colors.bgCard.copy(alpha = 0.42f)
        emphasized -> accent.copy(alpha = 0.24f)
        else -> Color.White.copy(alpha = 0.05f)
    }
    val border = when {
        !enabled -> colors.stroke.copy(alpha = 0.35f)
        emphasized -> accent.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.11f)
    }
    Box(
        modifier
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) colors.text else colors.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TokiQuietTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = tokiColors.accentLilac,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun tokiTextFieldColors(): TextFieldColors {
    val colors = tokiColors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.text,
        unfocusedTextColor = colors.text,
        disabledTextColor = colors.textDim,
        cursorColor = colors.accentPink,
        focusedBorderColor = colors.accentPink.copy(alpha = 0.72f),
        unfocusedBorderColor = colors.stroke.copy(alpha = 0.78f),
        disabledBorderColor = colors.stroke.copy(alpha = 0.35f),
        errorBorderColor = colors.error,
        focusedLabelColor = colors.accentPink,
        unfocusedLabelColor = colors.textDim,
        focusedPlaceholderColor = colors.textDim,
        unfocusedPlaceholderColor = colors.textDim.copy(alpha = 0.72f),
        focusedSupportingTextColor = colors.textDim,
        unfocusedSupportingTextColor = colors.textDim,
        errorSupportingTextColor = colors.error,
        focusedContainerColor = colors.bgCard.copy(alpha = 0.42f),
        unfocusedContainerColor = colors.bgCard.copy(alpha = 0.28f),
        errorContainerColor = colors.errorContainer.copy(alpha = 0.30f),
    )
}
