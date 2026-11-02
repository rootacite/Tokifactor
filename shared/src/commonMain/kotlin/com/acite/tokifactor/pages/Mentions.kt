package com.acite.tokifactor.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalDensity
import com.acite.tokifactor.model.AgentStatus
import com.acite.tokifactor.model.ChatMessage
import com.acite.tokifactor.model.SoloCharacter
import com.acite.tokifactor.model.aliasList
import com.acite.tokifactor.model.canonicalMemberName
import com.acite.tokifactor.model.legacyMemberId
import com.acite.tokifactor.ui.theme.TokiPalette
import com.acite.tokifactor.ui.theme.palette
import com.acite.tokifactor.ui.theme.tokiColors

data class MentionTarget(
    val memberId: String,
    val name: String,
    val labels: List<String>,
    val isUser: Boolean = false,
    val character: SoloCharacter? = null,
) {
    fun matches(label: String): Boolean =
        labels.any { canonicalMemberName(it) == canonicalMemberName(label) }
}

internal data class MentionSpan(
    val start: Int,
    val end: Int,
    val label: String,
    val target: MentionTarget,
)

private val mentionPrefixBlocked = "_@\\./%+-?&=#:"
private val inlineCode = Regex("`[^`\\n]*`")
private val fenceLine = Regex("(?m)^\\s*(```|~~~).*$")

internal fun buildMentionTargets(
    members: Collection<AgentStatus>,
    selfId: String = "",
    selfName: String = "",
): List<MentionTarget> {
    val targets = mutableListOf<MentionTarget>()
    members.forEach { member ->
        val labels = buildList {
            add(member.memberName)
            addAll(member.aliasList())
        }.map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(::canonicalMemberName)
        val character = resolveCharacter(member.memberName, member.memberId, labels)
        if (member.memberId.isNotBlank() || member.memberName.isNotBlank()) {
            targets += MentionTarget(
                memberId = member.memberId.ifBlank { legacyMemberId(member.memberName) },
                name = member.memberName.ifBlank { member.memberId },
                labels = labels,
                isUser = member.isUser,
                character = character,
            )
        }
    }
    SoloCharacter.entries.forEach { character ->
        if (targets.none { it.character == character && !it.isUser }) {
            targets += MentionTarget(
                memberId = legacyMemberId(character.label),
                name = character.label,
                labels = character.aliases,
                character = character,
            )
        }
    }
    val trimmedSelf = selfName.trim()
    if (trimmedSelf.isNotEmpty() || selfId.isNotBlank()) {
        val id = selfId.ifBlank { legacyMemberId(trimmedSelf) }
        if (targets.none { it.memberId == id && it.isUser }) {
            targets += MentionTarget(
                memberId = id,
                name = trimmedSelf.ifBlank { id },
                labels = listOfNotNull(trimmedSelf.takeIf(String::isNotBlank)),
                isUser = true,
            )
        }
    }
    return targets
}

private fun resolveCharacter(
    name: String,
    memberId: String,
    labels: List<String>,
): SoloCharacter? =
    SoloCharacter.resolve(name, memberId)
        ?: labels.firstNotNullOfOrNull { SoloCharacter.resolve(it) }

internal fun MentionTarget.color(colors: TokiPalette): Color =
    character?.palette?.accentPink
        ?: if (isUser) colors.accentBlue else colors.accentLilac

internal fun findMentionSpans(
    text: String,
    targets: List<MentionTarget>,
): List<MentionSpan> {
    if (text.isEmpty() || targets.isEmpty() || !text.contains('@')) return emptyList()
    val masked = codeMask(text)
    val aliases = targets.flatMap { target ->
        target.labels.map { label -> label.trim() to target }
    }
        .filter { (label, _) -> label.isNotEmpty() }
        .sortedByDescending { (label, _) -> label.length }
    val occupied = BooleanArray(text.length)
    val spans = mutableListOf<MentionSpan>()
    aliases.forEach { (label, target) ->
        val pattern = Regex(
            "(?<![A-Za-z0-9_])@" + Regex.escape(label),
            RegexOption.IGNORE_CASE,
        )
        for (match in pattern.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] in mentionPrefixBlocked)) {
                continue
            }
            if (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) {
                continue
            }
            if ((start until end).any { occupied[it] || masked[it] }) continue
            for (index in start until end) occupied[index] = true
            spans += MentionSpan(
                start = start,
                end = end,
                label = text.substring(start, end),
                target = target,
            )
        }
    }
    return spans.sortedBy { it.start }
}

internal class MentionVisualTransformation(
    private val targets: List<MentionTarget>,
    private val colors: TokiPalette,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val spans = findMentionSpans(text.text, targets)
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val builder = AnnotatedString.Builder(text)
        spans.forEach { span ->
            val color = span.target.color(colors)
            builder.addStyle(
                SpanStyle(
                    color = color,
                    background = color.copy(alpha = 0.16f),
                    fontWeight = FontWeight.SemiBold,
                ),
                span.start,
                span.end,
            )
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun codeMask(text: String): BooleanArray {
    val masked = BooleanArray(text.length)
    inlineCode.findAll(text).forEach { match ->
        for (index in match.range) masked[index] = true
    }
    var fenceStart = -1
    fenceLine.findAll(text).forEach { match ->
        if (fenceStart < 0) {
            fenceStart = match.range.first
        } else {
            for (index in fenceStart until match.range.last + 1) masked[index] = true
            fenceStart = -1
        }
    }
    if (fenceStart >= 0) {
        for (index in fenceStart until text.length) masked[index] = true
    }
    return masked
}

internal fun lastRespondingAgent(
    messages: List<ChatMessage>,
    targets: List<MentionTarget>,
): MentionTarget? {
    val message = messages.asReversed().firstOrNull {
        it.isAgent && it.agentKind == "turn" && it.content.isNotBlank()
    } ?: return null
    val senderId = message.senderId.ifBlank { legacyMemberId(message.sender) }
    val known = targets.firstOrNull {
        it.memberId == senderId ||
            canonicalMemberName(it.name) == canonicalMemberName(message.sender)
    }
    if (known != null) return known
    val name = message.sender.trim()
    if (name.isEmpty()) return null
    return MentionTarget(
        memberId = senderId,
        name = name,
        labels = listOf(name),
        character = SoloCharacter.resolve(name, senderId),
    )
}

internal data class MentionCompletion(val text: String, val cursor: Int)

internal fun completeLastAgentMention(
    previous: String,
    next: String,
    cursor: Int,
    target: MentionTarget?,
): MentionCompletion? {
    if (target == null || target.name.isBlank()) return null
    if (cursor <= 0 || cursor > next.length) return null
    if (next[cursor - 1] != '@') return null
    if (next.length != previous.length + 1) return null
    if (previous != next.removeRange(cursor - 1, cursor)) return null
    if (cursor >= 2) {
        val before = next[cursor - 2]
        if (before.isLetterOrDigit() || before in mentionPrefixBlocked) return null
    }
    val mention = "@${target.name} "
    val completed = next.substring(0, cursor - 1) + mention + next.substring(cursor)
    return MentionCompletion(completed, cursor - 1 + mention.length)
}

internal fun autoCompleteMention(
    previous: TextFieldValue,
    next: TextFieldValue,
    target: MentionTarget?,
): TextFieldValue {
    if (next.composition != null || !next.selection.collapsed) return next
    val completion = completeLastAgentMention(
        previous = previous.text,
        next = next.text,
        cursor = next.selection.end,
        target = target,
    ) ?: return next
    return TextFieldValue(completion.text, selection = TextRange(completion.cursor))
}

@Composable
internal fun MentionAwareText(
    text: String,
    targets: List<MentionTarget>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val spans = remember(text, targets) { findMentionSpans(text, targets) }
    if (spans.isEmpty()) {
        if (onTextLayout == null) {
            Text(
                text = text,
                modifier = modifier,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = overflow,
            )
        } else {
            Text(
                text = text,
                modifier = modifier,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = overflow,
                onTextLayout = onTextLayout,
            )
        }
        return
    }

    val colors = tokiColors
    val labelStyle = style.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { 10.dp.roundToPx() }
    val verticalPaddingPx = with(density) { 2.dp.roundToPx() }
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val builder = AnnotatedString.Builder()
    var cursor = 0
    spans.forEach { span ->
        val label = span.label
        val mentionColor = span.target.color(colors)
        val id = "mention:${canonicalMemberName(label)}"
        builder.append(text.substring(cursor, span.start))
        if (id !in inlineContent) {
            val measured = measurer.measure(
                text = AnnotatedString(label),
                style = labelStyle,
                maxLines = 1,
            )
            inlineContent[id] = InlineTextContent(
                Placeholder(
                    width = with(density) {
                        (measured.size.width + horizontalPaddingPx).toSp()
                    },
                    height = with(density) {
                        (measured.size.height + verticalPaddingPx).toSp()
                    },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                MentionLabel(label = label, color = mentionColor, style = labelStyle)
            }
        }
        builder.appendInlineContent(id, label)
        cursor = span.end
    }
    builder.append(text.substring(cursor))
    val annotated = builder.toAnnotatedString()
    if (onTextLayout == null) {
        Text(
            text = annotated,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            inlineContent = inlineContent,
        )
    } else {
        Text(
            text = annotated,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            inlineContent = inlineContent,
            onTextLayout = onTextLayout,
        )
    }
}

@Composable
private fun MentionLabel(
    label: String,
    color: Color,
    style: TextStyle,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(7.dp))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}
