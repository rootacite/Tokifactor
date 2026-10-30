package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolDisplayTest {
    @Test
    fun mcpNamesBecomeReadableTitles() {
        assertEquals("Tavily Search", toolDisplayName("mcp__tavily__tavily_search"))
        assertEquals("Tavily Search", toolDisplayName("mcp__tavily_tavily_search"))
        assertEquals("Tavily Extract", toolDisplayName("mcp__tavily__tavily_extract"))
        assertEquals("Tavily Crawl", toolDisplayName("mcp__tavily__tavily_crawl"))
        assertEquals("Tavily Map", toolDisplayName("mcp__tavily__tavily_map"))
        assertEquals("Tavily Research", toolDisplayName("mcp__tavily__tavily_research"))
        assertEquals("Nmap Scan", toolDisplayName("mcp__local__nmap_scan"))
        assertEquals("HTTPX Probe", toolDisplayName("mcp__local__httpx_probe"))
        assertEquals("DNSX Resolve", toolDisplayName("mcp__local__dnsx_resolve"))
        assertEquals("WhatWeb Fingerprint", toolDisplayName("mcp__local__whatweb_fingerprint"))
        assertEquals("Wafw00f Detect", toolDisplayName("mcp__local__wafw00f_detect"))
        assertEquals("JSHunter Analyze", toolDisplayName("mcp__local__jshunter_analyze"))
        assertEquals("BBOT Scan", toolDisplayName("mcp__local__bbot_scan"))
        assertEquals("FFUF Fuzz", toolDisplayName("mcp__local__ffuf_fuzz"))
        assertEquals("Recon JS Analyze", toolDisplayName("mcp__local__recon_js_analyze"))
        assertEquals("Recon Job Status", toolDisplayName("mcp__local__recon_job_status"))
        assertEquals("Code Review Precheck", toolDisplayName("mcp__local__code_review_precheck"))
        assertEquals("Summarize", toolDisplayName("mcp__local__summarize"))
        assertEquals("Other Port Scan", toolDisplayName("mcp__other__port_scan"))
    }

    @Test
    fun akiLabelsAndLettaAliasesStayStable() {
        assertEquals("Bash", toolDisplayName("exec_command"))
        assertEquals("Bash", toolDisplayName("write_stdin"))
        assertEquals("Update", toolDisplayName("Edit"))
        assertEquals("Update", toolDisplayName("multi_edit"))
        assertEquals("Search", toolDisplayName("Grep"))
        assertEquals("View Image", toolDisplayName("ViewImage"))
        assertEquals("Read LSP", toolDisplayName("ReadLSP"))
        assertEquals("Working Directory", toolDisplayName("SetWorkingDirectory"))
        assertEquals("Send tokifactor image", toolDisplayName("tokifactor_send_image"))
        assertEquals("Take a desktop screenshot", toolDisplayName("desktop_take_screenshot"))
        assertEquals("Start ComfyUI", toolDisplayName("comfyui_startup"))
        assertEquals("Generate image with ComfyUI", toolDisplayName("comfyui_generate"))
        assertEquals("Stop ComfyUI", toolDisplayName("comfyui_end"))
        assertEquals("ComfyUI Status", toolDisplayName("comfyui_status"))
        assertEquals("Create schedule", toolDisplayName("aki_schedule_create"))
        assertEquals("List schedules", toolDisplayName("aki_schedule_list"))
        assertEquals(
            "Reset another member's agent-to-agent reply budget to zero",
            toolDisplayName("aki_zero_reply_budget"),
        )
        assertEquals("Add life ledger record", toolDisplayName("aki_ledger_add"))
        assertEquals("List life ledger records", toolDisplayName("aki_ledger_list"))
        assertEquals("Search chat history", toolDisplayName("search_chat_history"))
    }

    @Test
    fun unknownAndBlankNamesStayReadable() {
        assertEquals("Tool", toolDisplayName(""))
        assertEquals("Tool", toolDisplayName("   "))
        assertEquals("Some New Tool", toolDisplayName("some_new_tool"))
        assertEquals("Some New Tool", toolDisplayName("SomeNewTool"))
        assertEquals("Read", toolDisplayName("Read"))
    }
}
