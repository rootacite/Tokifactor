package com.acite.tokifactor.model

data class AgentStatus(
    val present: Boolean = false,
    val lastSeenAt: Long = 0L,
    val memberId: String = "",
    val memberName: String = "",
    val description: String = "",
    val memberKind: String = MEMBER_KIND_AGENT,
    val aliases: String = "",
    val model: String = "",
    val reasoning: String = "",
    val contextLimit: Int = 0,
    val contextTokens: Int = 0,
    val backendReady: Boolean = false,
    val channelReady: Boolean = false,
    val sandboxReady: Boolean = false,
    val queueDepth: Int = 0,
    val activeTurn: Boolean = false,
    val pendingApprovals: Int = 0,
    val uptimeS: Long = 0L,
    val memoryTokens: Int = 0,
) {
    val agentId: String
        get() = memberId

    val agentName: String
        get() = memberName

    val isUser: Boolean
        get() = normalizeMemberKind(memberKind) == MEMBER_KIND_USER

    val kindLabel: String
        get() = if (isUser) "User" else "Agent"

    val contextPercent: Float?
        get() = if (contextLimit <= 0) null else {
            (contextTokens.toFloat() / contextLimit.toFloat()).coerceIn(0f, 1f)
        }
}

fun AgentStatus.statusLabel(): String = when {
    pendingApprovals > 0 -> "Approval needed"
    activeTurn -> "Replying…"
    queueDepth > 0 -> "Queued · $queueDepth"
    !backendReady -> "Backend not ready"
    else -> "Online"
}

private val providerPrefix = Regex("^aki(?:-[a-z0-9_-]{1,32})?/")

fun AgentStatus.modelLabel(): String = model.replace(providerPrefix, "")

fun AgentStatus.aliasList(): List<String> =
    aliases.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

fun preferredMember(members: Collection<AgentStatus>): AgentStatus? {
    val online = members.filter { it.present }
    return online.firstOrNull {
        it.memberId == "aki" ||
            canonicalMemberName(it.memberName) == "aki" ||
            it.aliasList().any { alias -> canonicalMemberName(alias) == "aki" }
    }
        ?: online.firstOrNull()
}

fun selectedMember(
    members: Collection<AgentStatus>,
    selectedMemberId: String?,
): AgentStatus? {
    val online = members.filter { it.present }
    return online.firstOrNull { it.memberId == selectedMemberId } ?: preferredMember(online)
}

fun AgentStatus.queueBadgeLabel(): String? =
    if (queueDepth > 0) "Queue $queueDepth" else null

fun AgentStatus.approvalBadgeLabel(): String? =
    if (pendingApprovals > 0) "Approval $pendingApprovals" else null

fun shouldExpireAki(
    present: Boolean,
    lastSeenAt: Long,
    now: Long,
    timeoutMs: Long,
): Boolean = present && now - lastSeenAt > timeoutMs

fun shouldProbeAki(
    present: Boolean,
    lastProbeSentAt: Long,
    now: Long,
    retryMs: Long,
): Boolean = !present && now - lastProbeSentAt >= retryMs
