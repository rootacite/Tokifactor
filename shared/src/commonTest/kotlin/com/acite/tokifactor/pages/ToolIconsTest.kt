package com.acite.tokifactor.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolIconsTest {
    @Test
    fun comfyuiToolsHaveDedicatedIcons() {
        assertEquals(Icons.Filled.PlayArrow, toolIcon("comfyui_startup"))
        assertEquals(Icons.Filled.Brush, toolIcon("comfyui_generate"))
        assertEquals(Icons.Filled.PowerSettingsNew, toolIcon("comfyui_end"))
        assertEquals(Icons.Filled.Brush, toolIcon("comfyui_future_tool"))
    }

    @Test
    fun comfyuiGroupsUseTheFamilyIcon() {
        assertEquals(
            Icons.Filled.Brush,
            toolGroupIcon(listOf("comfyui_startup", "comfyui_generate", "comfyui_end")),
        )
    }
}
