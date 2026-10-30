package com.acite.tokifactor.model

/**
 * Turns a wire tool name into the label a person reads.
 *
 * First-party names use Aki's schema labels and Letta's display aliases.
 * MCP names are `mcp__<server>__<tool>`. A tool token that repeats the server
 * name is dropped, and the `local` server name is omitted because it is only
 * the bucket those tools are registered under.
 */
fun toolDisplayName(rawName: String): String {
    val trimmed = rawName.trim()
    if (trimmed.isEmpty()) return "Tool"
    exactDisplayNames[trimmed]?.let { return it }
    if (trimmed.startsWith(MCP_PREFIX)) {
        return mcpDisplayName(trimmed.removePrefix(MCP_PREFIX))
    }
    val tokens = if ('_' in trimmed) trimmed.split('_') else splitPascal(trimmed)
    return titleCaseTokens(tokens).ifBlank { trimmed }
}

internal fun toolIconLookupKey(rawName: String): String {
    val trimmed = rawName.trim()
    if (trimmed.startsWith(MCP_PREFIX)) {
        return splitMcpRest(trimmed.removePrefix(MCP_PREFIX)).second.lowercase()
    }
    return trimmed.lowercase()
}

private const val MCP_PREFIX = "mcp__"
private const val LOCAL_MCP_SERVER = "local"

private val exactDisplayNames = mapOf(
    "Read" to "Read",
    "read" to "Read",
    "ReadFile" to "Read",
    "read_file" to "Read",
    "Edit" to "Update",
    "edit" to "Update",
    "MultiEdit" to "Update",
    "multi_edit" to "Update",
    "Write" to "Write",
    "write" to "Write",
    "ViewImage" to "View Image",
    "Bash" to "Bash",
    "bash" to "Bash",
    "shell" to "Bash",
    "Shell" to "Bash",
    "ShellCommand" to "Bash",
    "shell_command" to "Bash",
    "exec_command" to "Bash",
    "write_stdin" to "Bash",
    "Grep" to "Search",
    "grep" to "Search",
    "grep_files" to "Search",
    "GrepFiles" to "Search",
    "Glob" to "Glob",
    "glob" to "Glob",
    "LS" to "LS",
    "ls" to "LS",
    "list_dir" to "LS",
    "ListDir" to "LS",
    "Memory" to "Memory",
    "memory" to "Memory",
    "memory_apply_patch" to "Memory",
    "TodoWrite" to "TODO",
    "todo_write" to "TODO",
    "Task" to "Task",
    "task" to "Task",
    "Agent" to "Task",
    "agent" to "Task",
    "TaskCreate" to "Task Create",
    "TaskGet" to "Task Get",
    "TaskList" to "Task List",
    "TaskUpdate" to "Task Update",
    "TaskStop" to "Stop Task",
    "AskUserQuestion" to "Question",
    "AskUserQuestionAsync" to "Question",
    "WebSearch" to "Web Search",
    "web_search" to "Web Search",
    "webSearch" to "Web Search",
    "FetchWebpage" to "Fetch Webpage",
    "fetch_webpage" to "Fetch Webpage",
    "UpdatePlan" to "Planning",
    "ApplyPatch" to "Patch",
    "KillBash" to "Kill Bash",
    "BashOutput" to "Shell Output",
    "TaskOutput" to "Task Output",
    "EnterWorktree" to "Enter Worktree",
    "ExitWorktree" to "Exit Worktree",
    "SetWorkingDirectory" to "Working Directory",
    "SendAgentMessage" to "Send Message",
    "Monitor" to "Monitor",
    "WatchPR" to "Watch PR",
    "Wake" to "Wake",
    "Skill" to "Skill",
    "CodeMode" to "Code Mode",
    "Workflow" to "Workflow",
    "ReadLSP" to "Read LSP",
    "tokifactor_send_image" to "Send tokifactor image",
    "tokifactor_send_file" to "Send tokifactor file",
    "camera_take_photo" to "Take a camera photo",
    "desktop_take_screenshot" to "Take a desktop screenshot",
    "generate_image" to "Generate image",
    "comfyui_startup" to "Start ComfyUI",
    "comfyui_generate" to "Generate image with ComfyUI",
    "comfyui_end" to "Stop ComfyUI",
    "acquire_execution_lock" to "Acquire execution lock",
    "release_execution_lock" to "Release execution lock",
    "wait_other" to "Wait for earlier agents",
    "wait_user" to "Wait for the user to continue",
    "search_chat_history" to "Search chat history",
    "aki_schedule_create" to "Create schedule",
    "aki_schedule_update" to "Update schedule",
    "aki_schedule_delete" to "Delete schedule",
    "aki_schedule_list" to "List schedules",
    "aki_zero_reply_budget" to "Reset another member's agent-to-agent reply budget to zero",
    "aki_ledger_add" to "Add life ledger record",
    "aki_ledger_delete" to "Delete life ledger record",
    "aki_ledger_list" to "List life ledger records",
)

private val tokenWords = mapOf(
    "tavily" to "Tavily",
    "nmap" to "Nmap",
    "naabu" to "Naabu",
    "dnsx" to "DNSX",
    "httpx" to "HTTPX",
    "bbot" to "BBOT",
    "ffuf" to "FFUF",
    "js" to "JS",
    "whatweb" to "WhatWeb",
    "wafw00f" to "Wafw00f",
    "jshunter" to "JSHunter",
    "subfinder" to "Subfinder",
    "amass" to "Amass",
    "katana" to "Katana",
    "restringer" to "Restringer",
    "nuclei" to "Nuclei",
    "aki" to "Aki",
    "tokifactor" to "TokiFactor",
    "dns" to "DNS",
    "http" to "HTTP",
    "waf" to "WAF",
    "ip" to "IP",
    "url" to "URL",
    "api" to "API",
    "pr" to "PR",
    "lsp" to "LSP",
    "id" to "ID",
    "ai" to "AI",
    "llm" to "LLM",
    "comfyui" to "ComfyUI",
)

private fun mcpDisplayName(rest: String): String {
    val (server, tool) = splitMcpRest(rest)
    val toolTokens = tool.split('_').filter { it.isNotBlank() }
    val serverToken = server.trim('_')
    val repeatsServer = toolTokens.firstOrNull()?.equals(serverToken, ignoreCase = true) == true
    val body = if (repeatsServer) toolTokens.drop(1) else toolTokens
    val words = buildList {
        if (serverToken.isNotEmpty() && !serverToken.equals(LOCAL_MCP_SERVER, ignoreCase = true)) {
            add(serverToken)
        }
        addAll(body)
    }
    return titleCaseTokens(words).ifBlank { "Tool" }
}

internal fun splitMcpRest(rest: String): Pair<String, String> {
    val separator = rest.indexOf("__")
    if (separator >= 0) {
        return rest.substring(0, separator) to rest.substring(separator + 2)
    }
    val tokens = rest.split('_').filter { it.isNotBlank() }
    if (tokens.size >= 2 && tokens[0].equals(tokens[1], ignoreCase = true)) {
        return tokens[0] to tokens.drop(1).joinToString("_")
    }
    return "" to rest
}

private fun splitPascal(name: String): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    for (index in name.indices) {
        val char = name[index]
        if (index > 0 && char.isUpperCase()) {
            val previous = name[index - 1]
            val nextIsLower = index + 1 < name.length && name[index + 1].isLowerCase()
            if (previous.isLowerCase() || (previous.isUpperCase() && nextIsLower)) {
                words += current.toString()
                current.clear()
            }
        }
        current.append(char)
    }
    if (current.isNotEmpty()) words += current.toString()
    return words
}

private fun titleCaseTokens(tokens: List<String>): String =
    tokens
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { token ->
            tokenWords[token.lowercase()] ?: token.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
