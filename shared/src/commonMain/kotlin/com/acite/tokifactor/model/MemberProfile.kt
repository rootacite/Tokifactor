package com.acite.tokifactor.model

const val MEMBER_KIND_USER = "user"
const val MEMBER_KIND_AGENT = "agent"
const val MAX_MEMBER_DESCRIPTION_UNITS = 512

fun memberNameKey(value: String): String = canonicalMemberName(value)

fun normalizeMemberKind(value: String?): String =
    if (value?.trim()?.lowercase() == MEMBER_KIND_USER) MEMBER_KIND_USER else MEMBER_KIND_AGENT

fun memberDescriptionWeight(value: String): Int {
    var index = 0
    var weight = 0
    while (index < value.length) {
        val codeUnit = value[index].code
        val codePoint = if (codeUnit in 0xD800..0xDBFF && index + 1 < value.length) {
            val low = value[index + 1].code
            if (low in 0xDC00..0xDFFF) {
                index += 1
                0x10000 + ((codeUnit - 0xD800) shl 10) + (low - 0xDC00)
            } else {
                codeUnit
            }
        } else {
            codeUnit
        }
        weight += if (codePoint < 128) 1 else 2
        index += 1
    }
    return weight
}

fun truncateMemberDescription(
    value: String,
    maxWeight: Int = MAX_MEMBER_DESCRIPTION_UNITS,
): String {
    if (maxWeight <= 0) return ""
    var index = 0
    var weight = 0
    while (index < value.length) {
        val codeUnit = value[index].code
        val characterLength = if (
            codeUnit in 0xD800..0xDBFF &&
            index + 1 < value.length &&
            value[index + 1].code in 0xDC00..0xDFFF
        ) {
            2
        } else {
            1
        }
        val codePoint = if (characterLength == 2) {
            0x10000 + ((codeUnit - 0xD800) shl 10) + (value[index + 1].code - 0xDC00)
        } else {
            codeUnit
        }
        val nextWeight = if (codePoint < 128) 1 else 2
        if (weight + nextWeight > maxWeight) break
        weight += nextWeight
        index += characterLength
    }
    return value.substring(0, index)
}

fun memberControlMetadata(
    name: String,
    bio: String,
    memberKind: String,
    memberId: String = "",
    sourceId: String = "",
    aliases: List<String> = emptyList(),
): Map<String, String> {
    val displayName = name.trim()
    val key = memberId.ifBlank { legacyMemberId(displayName) }
    return buildMap {
        put("agent", key)
        put("agentId", key)
        put("agentName", displayName)
        put("memberId", key)
        put("memberName", displayName)
        put("sourceId", sourceId)
        put("description", truncateMemberDescription(bio))
        put("memberKind", normalizeMemberKind(memberKind))
        val labels = aliases.map(String::trim).filter(String::isNotEmpty)
        put("aliases", labels.joinToString(","))
    }
}

fun relativeTimeLabel(sentAt: Long, now: Long): String {
    if (sentAt <= 0L) return "Unknown time"
    val delta = (now - sentAt).coerceAtLeast(0L)
    val seconds = delta / 1000L
    return when {
        seconds < 45L -> "Just now"
        seconds < 3_600L -> "${(seconds / 60L).coerceAtLeast(1L)}m ago"
        seconds < 86_400L -> "${seconds / 3_600L}h ago"
        seconds < 604_800L -> "${seconds / 86_400L}d ago"
        else -> "${seconds / 604_800L}w ago"
    }
}

fun recentMemberMessages(
    messages: List<ChatMessage>,
    memberId: String,
    limit: Int = 3,
): List<ChatMessage> {
    if (memberId.isBlank() || limit <= 0) return emptyList()
    return messages
        .asSequence()
        .filter {
            (it.senderId == memberId || memberNameKey(it.sender) == memberId) &&
                !it.isSystem && !it.isTransferring
        }
        .filter { it.content.isNotBlank() }
        .filter { !it.isAgent || it.agentKind == "turn" }
        .toList()
        .takeLast(limit)
}

fun AgentStatus.withControlProfile(
    metadata: Map<String, String>,
    fallbackName: String,
    now: Long,
    updateProfile: Boolean = true,
): AgentStatus {
    val incomingName = metadata["memberName"].orEmpty()
        .ifBlank { metadata["agentName"].orEmpty() }
        .ifBlank { fallbackName }
    if (!updateProfile) {
        return copy(present = true, lastSeenAt = now)
    }
    return copy(
        present = true,
        lastSeenAt = now,
        memberName = incomingName.ifBlank { memberName.ifBlank { memberId } },
        description = metadata["description"]
            ?.let(::truncateMemberDescription)
            ?: description,
        memberKind = metadata["memberKind"]?.let(::normalizeMemberKind) ?: memberKind,
        aliases = metadata["aliases"]?.take(512) ?: aliases,
    )
}
