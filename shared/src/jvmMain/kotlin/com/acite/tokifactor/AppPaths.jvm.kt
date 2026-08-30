package com.acite.tokifactor

import java.io.File

actual fun appConfigFile(): File {
    val dir = File(System.getProperty("user.home"), ".tokifactor")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return File(dir, "config.toml")
}

actual fun defaultDisplayName(): String {
    val host = try {
        java.net.InetAddress.getLocalHost().hostName
            ?.substringBefore('.')
            ?.takeIf { it.isNotBlank() && !it.equals("localhost", ignoreCase = true) }
    } catch (_: Exception) {
        null
    }
    val user = System.getProperty("user.name")?.takeIf { it.isNotBlank() }
    return host ?: user ?: "Desktop"
}
