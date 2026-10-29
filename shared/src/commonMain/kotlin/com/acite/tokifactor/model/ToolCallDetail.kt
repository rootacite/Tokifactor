package com.acite.tokifactor.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

private val toolDetailJson = Json { ignoreUnknownKeys = true }

data class ToolCallDetail(
    val arguments: String,
    val result: String?,
)

data class ToolCallCounts(
    val running: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
)

data class ToolGroupSummary(
    val text: String,
    val failedStart: Int = -1,
    val failedEnd: Int = -1,
) {
    val hasFailedSpan: Boolean
        get() = failedStart >= 0 && failedEnd > failedStart
}

enum class ToolGroupTone {
    Normal,
    Approval,
}

fun toolCallStatusLabel(status: String): String = when (status) {
    "", "running" -> "Running"
    "success", "complete" -> "Completed"
    "error" -> "Failed"
    else -> status.replaceFirstChar { it.uppercase() }
}

fun toolCallCounts(messages: List<ChatMessage>): ToolCallCounts {
    var running = 0
    var succeeded = 0
    var failed = 0
    for (message in messages) {
        if (message.agentKind != "tool") continue
        when (message.status) {
            "success", "complete" -> succeeded += 1
            "error" -> failed += 1
            else -> running += 1
        }
    }
    return ToolCallCounts(running = running, succeeded = succeeded, failed = failed)
}

/**
 * Group surface stays neutral when a tool fails. Pending approval is the only
 * reason the whole card changes color.
 */
fun toolGroupTone(requiresAction: Boolean): ToolGroupTone =
    if (requiresAction) ToolGroupTone.Approval else ToolGroupTone.Normal

fun toolGroupSummary(
    toolNames: List<String>,
    counts: ToolCallCounts,
    approvalCount: Int,
    requiresAction: Boolean,
): ToolGroupSummary {
    val parts = mutableListOf<Pair<String, Boolean>>()
    toolNames.forEach { parts += it to false }
    when (approvalCount) {
        0 -> Unit
        1 -> parts += "approval" to false
        else -> parts += "$approvalCount approvals" to false
    }
    if (counts.running > 0) parts += "${counts.running} running" to false
    if (counts.succeeded > 0) parts += "${counts.succeeded} succeeded" to false
    if (counts.failed > 0) parts += "${counts.failed} failed" to true
    if (requiresAction) parts += "Approval needed" to false

    val text = StringBuilder()
    var failedStart = -1
    var failedEnd = -1
    for ((part, failed) in parts) {
        if (text.isNotEmpty()) text.append(" · ")
        val start = text.length
        text.append(part)
        if (failed) {
            failedStart = start
            failedEnd = text.length
        }
    }
    return ToolGroupSummary(
        text = text.toString(),
        failedStart = failedStart,
        failedEnd = failedEnd,
    )
}

fun parseToolCallDetail(content: String): ToolCallDetail? {
    val element = try {
        toolDetailJson.parseToJsonElement(content)
    } catch (_: Exception) {
        return null
    }
    val obj = element as? JsonObject ?: return null
    if (!obj.containsKey("arguments")) return null
    val result = if (obj.containsKey("result")) jsonScalar(obj["result"]) else null
    return ToolCallDetail(
        arguments = jsonScalar(obj["arguments"]),
        result = result,
    )
}

fun encodeToolCallContent(arguments: String, result: String?): String {
    return buildJsonObject {
        put("arguments", arguments)
        if (result != null) put("result", result)
    }.toString()
}

fun mergeToolCallContent(previous: String, incoming: String, incomingStatus: String): String {
    val old = parseToolCallDetail(previous)
    val new = parseToolCallDetail(incoming) ?: return if (old != null) previous else incoming
    val arguments = new.arguments.ifBlank { old?.arguments.orEmpty() }
    val result = when {
        incomingStatus == "running" && new.result == null -> old?.result
        new.result != null -> new.result
        else -> old?.result
    }
    return encodeToolCallContent(arguments, result)
}

private fun jsonScalar(element: JsonElement?): String {
    if (element == null || element is JsonNull) return ""
    if (element is JsonPrimitive) {
        return element.contentOrNull ?: element.toString()
    }
    if (element is JsonObject || element is JsonArray) return element.toString()
    return element.toString()
}
