package com.acite.tokifactor.model

data class AgentStatus(
    val present: Boolean = false,
    val lastSeenAt: Long = 0L,
    val agentId: String = "",
    val agentName: String = "",
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

fun AgentStatus.modelLabel(): String = model.removePrefix("aki/")

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
