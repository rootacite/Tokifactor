package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolCallDetailTest {
    @Test
    fun countsRunningSuccessAndFailureSeparately() {
        val counts = toolCallCounts(
            listOf(
                tool("success", "Read"),
                tool("complete", "Grep"),
                tool("error", "Bash"),
                tool("running", "Glob"),
                tool("", "Write"),
                ChatMessage(
                    id = "approval",
                    content = "",
                    pic = null,
                    agentKind = "approval",
                    status = "error",
                ),
            ),
        )

        assertEquals(ToolCallCounts(running = 2, succeeded = 2, failed = 1), counts)
    }

    @Test
    fun summaryCountsFailuresWithoutMarkingTheGroupFailed() {
        val counts = ToolCallCounts(running = 1, succeeded = 2, failed = 1)
        val summary = toolGroupSummary(
            toolNames = listOf("Read", "Bash"),
            counts = counts,
            approvalCount = 0,
            requiresAction = false,
        )

        assertEquals(
            "Read · Bash · 1 running · 2 succeeded · 1 failed",
            summary.text,
        )
        assertEquals("1 failed", summary.text.substring(summary.failedStart, summary.failedEnd))
        assertEquals(ToolGroupTone.Normal, toolGroupTone(requiresAction = false))
    }

    @Test
    fun finishedGroupReportsSucceededCount() {
        val summary = toolGroupSummary(
            toolNames = listOf("Read"),
            counts = ToolCallCounts(succeeded = 4),
            approvalCount = 0,
            requiresAction = false,
        )

        assertEquals("Read · 4 succeeded", summary.text)
        assertFalse(summary.hasFailedSpan)
        assertEquals(ToolGroupTone.Normal, toolGroupTone(requiresAction = false))
    }

    @Test
    fun pendingApprovalKeepsItsOwnToneAndLabel() {
        val summary = toolGroupSummary(
            toolNames = listOf("Bash"),
            counts = ToolCallCounts(failed = 1),
            approvalCount = 2,
            requiresAction = true,
        )

        assertEquals(
            "Bash · 2 approvals · 1 failed · Approval needed",
            summary.text,
        )
        assertEquals(ToolGroupTone.Approval, toolGroupTone(requiresAction = true))
    }

    @Test
    fun parsesArgumentsAndResult() {
        val encoded = encodeToolCallContent(
            arguments = "say \"hi\"\nnext",
            result = "line\n2",
        )
        val detail = parseToolCallDetail(encoded)

        assertEquals("say \"hi\"\nnext", detail?.arguments)
        assertEquals("line\n2", detail?.result)
    }

    @Test
    fun legacyStatusLineHasNoDetail() {
        assertNull(parseToolCallDetail("tool: Bash success"))
    }

    @Test
    fun resultEventKeepsArgumentsRecordedOnTheRunningEvent() {
        val running = encodeToolCallContent(
            arguments = "{\n  \"path\": \"MEMORY.md\"\n}",
            result = null,
        )
        val incoming = encodeToolCallContent(arguments = "", result = "tool output")

        val merged = mergeToolCallContent(running, incoming, incomingStatus = "success")
        val detail = parseToolCallDetail(merged)

        assertEquals("{\n  \"path\": \"MEMORY.md\"\n}", detail?.arguments)
        assertEquals("tool output", detail?.result)
    }

    @Test
    fun runningUpdateDoesNotDropAResultAlreadyPresent() {
        val finished = encodeToolCallContent(arguments = "args", result = "done")
        val runningAgain = encodeToolCallContent(arguments = "args", result = null)

        val merged = mergeToolCallContent(finished, runningAgain, incomingStatus = "running")

        assertEquals("done", parseToolCallDetail(merged)?.result)
    }

    @Test
    fun legacyResultKeepsPreviousDetail() {
        val running = encodeToolCallContent(arguments = "args", result = null)

        assertEquals(
            running,
            mergeToolCallContent(running, "tool: Bash success", incomingStatus = "success"),
        )
    }

    @Test
    fun twoLegacyLinesKeepTheIncomingLine() {
        assertEquals(
            "tool: Bash success",
            mergeToolCallContent("tool: Bash running", "tool: Bash success", "success"),
        )
    }

    @Test
    fun emptyFinishedResultStaysEmpty() {
        val running = encodeToolCallContent(arguments = "args", result = null)
        val finished = encodeToolCallContent(arguments = "args", result = "")

        val detail = parseToolCallDetail(
            mergeToolCallContent(running, finished, incomingStatus = "success"),
        )

        assertEquals("", detail?.result)
        assertTrue(detail?.result != null)
    }
}

private fun tool(status: String, name: String) = ChatMessage(
    id = "$name-$status",
    content = "",
    pic = null,
    agentKind = "tool",
    toolName = name,
    status = status,
)
