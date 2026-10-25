package com.acite.tokifactor.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.aliasList
import com.acite.tokifactor.model.modelLabel
import com.acite.tokifactor.model.relativeTimeLabel
import com.acite.tokifactor.model.statusLabel
import com.acite.tokifactor.ui.theme.TokiCapsuleButton
import com.acite.tokifactor.ui.theme.TokiQuietTextButton
import com.acite.tokifactor.ui.theme.TokiSection
import com.acite.tokifactor.ui.theme.TokiSurface
import com.acite.tokifactor.ui.theme.tokiColors
import kotlin.math.roundToInt

@Composable
fun MemberProfilePage(
    member: AgentStatus,
    avatar: ByteArray?,
    isSelf: Boolean,
    recentMessages: List<ChatMessage>,
    onBack: () -> Unit,
    onEditProfile: (() -> Unit)? = null,
    soloActive: Boolean = false,
    soloTargetId: String = "",
    onSolo: (() -> Unit)? = null,
) {
    val colors = tokiColors
    val now = remember(member.memberId) { System.currentTimeMillis() }
    val aliases = member.aliasList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TokiQuietTextButton(text = "Back", onClick = onBack)
                Spacer(modifier = Modifier.weight(1f))
                if (isSelf && onEditProfile != null) {
                    TokiQuietTextButton(
                        text = "Edit profile",
                        onClick = onEditProfile,
                        color = colors.accentPink,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MemberProfileAvatar(name = member.agentName, photo = avatar, size = 132)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = member.memberName.ifBlank { member.memberId },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = member.memberId,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfilePill(
                        text = member.kindLabel,
                        color = if (member.isUser) colors.accentBlue else colors.accentPink,
                    )
                    ProfilePill(
                        text = if (member.present || isSelf) "Online" else "Offline",
                        color = if (member.present || isSelf) colors.qualityGreen else colors.textDim,
                    )
                }
            }

            TokiSection(title = "Introduction") {
                Text(
                    text = member.description.ifBlank { "No introduction yet." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (member.description.isBlank()) colors.textDim else colors.text,
                )
                Text(
                    text = "Shared with everyone using this channel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textDim,
                )
            }

            if (aliases.isNotEmpty()) {
                TokiSection(title = "Aliases") {
                    Text(
                        text = aliases.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                }
            }

            if (!member.isUser) {
                TokiSection(title = "Agent status") {
                    ProfileInfoRow(
                        label = "State",
                        value = if (member.present) member.statusLabel() else "Offline",
                    )
                    member.modelLabel().takeIf { it.isNotBlank() }?.let { model ->
                        ProfileInfoRow(label = "Model", value = model)
                    }
                    member.reasoning.takeIf { it.isNotBlank() }?.let { reasoning ->
                        ProfileInfoRow(label = "Reasoning", value = reasoning)
                    }
                    member.contextPercent?.let { percent ->
                        ProfileInfoRow(
                            label = "Context",
                            value = "${(percent * 100).roundToInt()}%",
                        )
                    }
                    if (member.queueDepth > 0) {
                        ProfileInfoRow(label = "Queue", value = member.queueDepth.toString())
                    }
                    if (member.pendingApprovals > 0) {
                        ProfileInfoRow(
                            label = "Approvals",
                            value = member.pendingApprovals.toString(),
                        )
                    }
                }
            }

            if (!member.isUser && onSolo != null) {
                TokiCapsuleButton(
                    text = if (soloActive && soloTargetId == member.memberId) {
                        "End solo time"
                    } else {
                        "Solo time"
                    },
                    onClick = onSolo,
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (soloActive && soloTargetId != member.memberId) {
                    Text(
                        text = "Another solo session is active. End it from the main screen first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textDim,
                    )
                }
            }

            TokiSection(
                title = "Recent messages",
                hint = "Latest messages from this member on this device.",
            ) {
                if (recentMessages.isEmpty()) {
                    Text(
                        text = "No messages yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textDim,
                    )
                } else {
                    recentMessages.forEach { message ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = relativeTimeLabel(message.sentAt, now),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textDim,
                            )
                        }
                    }
                }
            }

            if (isSelf && onEditProfile != null) {
                TokiCapsuleButton(
                    text = "Edit profile",
                    onClick = onEditProfile,
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MemberProfileAvatar(
    name: String,
    photo: ByteArray?,
    size: Int,
) {
    val colors = tokiColors
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.accentPink.copy(alpha = 0.24f),
                        colors.accentBlue.copy(alpha = 0.20f),
                        colors.accentLilac.copy(alpha = 0.24f),
                    ),
                ),
            )
            .border(1.dp, colors.accentLilac.copy(alpha = 0.48f), CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                ByteImage(
                    imageBytes = photo,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (name.isNotBlank()) {
                Text(
                    text = name.trim().first().uppercaseChar().toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.text,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = colors.accentLilac,
                    modifier = Modifier.size(54.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfilePill(text: String, color: androidx.compose.ui.graphics.Color) {
    TokiSurface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        borderColor = color.copy(alpha = 0.34f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    val colors = tokiColors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textDim,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
