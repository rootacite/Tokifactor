package com.acite.tokifactor.daemon

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

fun daemonSocketFile(): File {
    val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
    val root = if (runtime != null) {
        File(runtime, "tokifactor")
    } else {
        File(System.getProperty("user.home"), ".cache/tokifactor")
    }
    if (!root.exists()) root.mkdirs()
    setDirectoryPermissions(root)
    return File(root, "tokifactord.sock")
}

internal fun setDirectoryPermissions(dir: File) {
    try {
        Files.setPosixFilePermissions(
            dir.toPath(),
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
        )
    } catch (_: Exception) {
    }
}

internal fun setSocketPermissions(file: File) {
    try {
        Files.setPosixFilePermissions(
            file.toPath(),
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            ),
        )
    } catch (_: Exception) {
    }
}
