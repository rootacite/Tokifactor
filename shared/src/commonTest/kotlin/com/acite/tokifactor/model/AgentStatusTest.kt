package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentStatusTest {
    @Test
    fun statusPriorityMatchesHeaderRules() {
        val base = AgentStatus(present = true, backendReady = true)
        assertEquals("Online", base.statusLabel())
        assertEquals("Backend not ready", base.copy(backendReady = false).statusLabel())
        assertEquals("Queued · 2", base.copy(queueDepth = 2).statusLabel())
        assertEquals(
            "Replying…",
            base.copy(queueDepth = 2, activeTurn = true).statusLabel(),
        )
        assertEquals(
            "Approval needed",
            base.copy(
                queueDepth = 2,
                activeTurn = true,
                pendingApprovals = 1,
            ).statusLabel(),
        )
    }

    @Test
    fun modelAndBadgesAreCompact() {
        val status = AgentStatus(
            model = "aki/deepseek-flash",
            queueDepth = 3,
            pendingApprovals = 2,
        )
        assertEquals("deepseek-flash", status.modelLabel())
        assertEquals("Queue 3", status.queueBadgeLabel())
        assertEquals("Approval 2", status.approvalBadgeLabel())
        assertNull(status.copy(queueDepth = 0).queueBadgeLabel())
        assertNull(status.copy(pendingApprovals = 0).approvalBadgeLabel())
    }

    @Test
    fun contextPercentClampsAndHidesWithoutLimit() {
        assertNull(AgentStatus(contextTokens = 10).contextPercent)
        assertEquals(
            0.25f,
            AgentStatus(contextLimit = 100, contextTokens = 25).contextPercent,
        )
        assertEquals(
            1f,
            AgentStatus(contextLimit = 100, contextTokens = 250).contextPercent,
        )
    }

    @Test
    fun timeoutHidesAkiAndResumesProbing() {
        assertEquals(false, shouldExpireAki(true, 1_000L, 30_999L, 30_000L))
        assertEquals(true, shouldExpireAki(true, 1_000L, 31_001L, 30_000L))
        assertEquals(false, shouldExpireAki(false, 1_000L, 99_000L, 30_000L))
        assertEquals(true, shouldProbeAki(false, 1_000L, 16_000L, 15_000L))
        assertEquals(false, shouldProbeAki(false, 1_000L, 15_999L, 15_000L))
        assertEquals(false, shouldProbeAki(true, 1_000L, 99_000L, 15_000L))
    }

    @Test
    fun controlFieldsSurviveEnvelopeRoundTrip() {
        val metadata = mapOf(
            "schema" to "akid.v1",
            "kind" to "status",
            "agent" to "aki",
            "agentName" to "Aki",
            "model" to "aki/deepseek-flash",
            "reasoning" to "high",
            "protocolVersion" to "1",
            "contextLimit" to "400000",
            "contextTokens" to "1000",
            "backendReady" to "true",
            "channelReady" to "true",
            "sandboxReady" to "true",
            "queueDepth" to "2",
            "activeTurn" to "false",
            "pendingApprovals" to "1",
            "uptimeS" to "42",
            "memoryTokens" to "7",
        )
        val envelope = TextEnvelope(
            sender = "akid",
            text = "Aki status",
            senderId = "akid",
            metadata = metadata,
        )
        val decoded = TextEnvelope.decode(envelope.encode())
        assertEquals(metadata, decoded?.metadata)
    }
}
