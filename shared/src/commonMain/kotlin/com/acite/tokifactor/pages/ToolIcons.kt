package com.acite.tokifactor.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.acite.tokifactor.model.toolDisplayName
import com.acite.tokifactor.model.toolIconLookupKey

fun toolIcon(rawName: String): ImageVector =
    toolIcons[toolIconLookupKey(rawName)] ?: Icons.Filled.Extension

fun toolGroupIcon(rawNames: List<String>): ImageVector {
    val named = rawNames.filter { it.isNotBlank() }
    if (named.isEmpty()) return Icons.Filled.Widgets
    val labels = named.map { toolDisplayName(it) }.distinct()
    return if (labels.size == 1) toolIcon(named.first()) else Icons.Filled.Widgets
}

private val toolIcons: Map<String, ImageVector> = buildMap {
    fun icon(image: ImageVector, vararg keys: String) {
        keys.forEach { put(it, image) }
    }

    icon(Icons.Filled.Description, "read", "readfile", "read_file", "readlsp", "read_artifact_file")
    icon(Icons.AutoMirrored.Filled.NoteAdd, "write")
    icon(Icons.Filled.Edit, "edit", "multiedit", "multi_edit")
    icon(Icons.Filled.Difference, "applypatch")
    icon(Icons.Filled.Image, "viewimage", "tokifactor_send_image")
    icon(Icons.Filled.AutoAwesome, "generate_image", "skill")
    icon(
        Icons.Filled.Terminal,
        "bash",
        "shell",
        "shellcommand",
        "shell_command",
        "exec_command",
        "write_stdin",
        "bashoutput",
        "taskoutput",
    )
    icon(Icons.Filled.Cancel, "killbash", "recon_job_cancel")
    icon(Icons.Filled.Search, "grep", "grep_files", "grepfiles")
    icon(Icons.Filled.TravelExplore, "websearch", "web_search", "tavily_search")
    icon(Icons.AutoMirrored.Filled.ManageSearch, "search_chat_history")
    icon(Icons.Filled.FilterAlt, "glob")
    icon(Icons.Filled.Folder, "ls", "list_dir", "listdir")
    icon(Icons.Filled.FolderOpen, "setworkingdirectory")
    icon(Icons.Filled.Psychology, "memory", "memory_apply_patch")
    icon(Icons.Filled.Checklist, "todowrite", "todo_write")
    icon(
        Icons.Filled.AccountTree,
        "task",
        "agent",
        "taskcreate",
        "taskget",
        "tasklist",
        "taskupdate",
        "workflow",
    )
    icon(Icons.Filled.StopCircle, "taskstop")
    icon(Icons.Filled.QuestionAnswer, "askuserquestion", "askuserquestionasync")
    icon(Icons.Filled.HourglassBottom, "wait_user")
    icon(Icons.Filled.HourglassTop, "wait_other")
    icon(Icons.Filled.Language, "fetchwebpage", "fetch_webpage", "tavily_crawl", "recon_web_active")
    icon(Icons.AutoMirrored.Filled.EventNote, "updateplan")
    icon(Icons.AutoMirrored.Filled.Send, "sendagentmessage")
    icon(Icons.Filled.AttachFile, "tokifactor_send_file")
    icon(Icons.Filled.MonitorHeart, "monitor")
    icon(Icons.Filled.Visibility, "watchpr")
    icon(Icons.Filled.Notifications, "wake")
    icon(Icons.Filled.Code, "codemode", "jshunter_analyze")
    icon(Icons.AutoMirrored.Filled.Login, "enterworktree")
    icon(Icons.AutoMirrored.Filled.Logout, "exitworktree")
    icon(Icons.Filled.PhotoCamera, "camera_take_photo")
    icon(Icons.Filled.Screenshot, "desktop_take_screenshot")
    icon(Icons.Filled.Lock, "acquire_execution_lock")
    icon(Icons.Filled.LockOpen, "release_execution_lock")
    icon(Icons.Filled.VoiceOverOff, "aki_zero_reply_budget")
    icon(Icons.Filled.Event, "aki_schedule_create")
    icon(Icons.Filled.EditCalendar, "aki_schedule_update")
    icon(Icons.Filled.EventBusy, "aki_schedule_delete")
    icon(Icons.Filled.CalendarMonth, "aki_schedule_list")
    icon(Icons.Filled.PostAdd, "aki_ledger_add")
    icon(Icons.Filled.DeleteSweep, "aki_ledger_delete")
    icon(Icons.AutoMirrored.Filled.MenuBook, "aki_ledger_list")
    icon(Icons.Filled.FindInPage, "tavily_extract", "extract")
    icon(Icons.Filled.Map, "tavily_map")
    icon(Icons.Filled.School, "tavily_research")
    icon(Icons.Filled.Radar, "nmap_scan")
    icon(Icons.Filled.SettingsEthernet, "naabu_scan")
    icon(Icons.Filled.Http, "httpx_probe")
    icon(Icons.Filled.Dns, "dnsx_resolve")
    icon(Icons.Filled.Hub, "subfinder_enum")
    icon(Icons.Filled.Public, "amass_passive", "amass_intel", "recon_domain_passive")
    icon(Icons.Filled.Policy, "bbot_scan")
    icon(Icons.Filled.Fingerprint, "whatweb_fingerprint")
    icon(Icons.Filled.Security, "wafw00f_detect")
    icon(Icons.AutoMirrored.Filled.AltRoute, "katana_crawl")
    icon(Icons.Filled.DataObject, "restringer_deobfuscate")
    icon(Icons.Filled.Memory, "nuclei_tech")
    icon(Icons.Filled.BugReport, "nuclei_vuln")
    icon(Icons.Filled.Shuffle, "ffuf_fuzz")
    icon(Icons.Filled.Pending, "recon_job_status")
    icon(Icons.AutoMirrored.Filled.Article, "recon_job_logs")
    icon(Icons.Filled.HealthAndSafety, "recon_health")
    icon(Icons.Filled.Javascript, "recon_js_analyze")
    icon(Icons.AutoMirrored.Filled.Subject, "summarize")
    icon(Icons.Filled.Category, "classify")
    icon(Icons.AutoMirrored.Filled.FactCheck, "batch_analyze")
    icon(Icons.Filled.RateReview, "code_review_precheck")
    icon(Icons.Filled.Compress, "compress_context")
    icon(Icons.AutoMirrored.Filled.Chat, "ask")
}
