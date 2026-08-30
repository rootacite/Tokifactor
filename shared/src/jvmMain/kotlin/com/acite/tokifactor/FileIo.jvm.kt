package com.acite.tokifactor

import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun copyFileToPlatformFile(source: File, dest: PlatformFile) {
    withContext(Dispatchers.IO) {
        source.inputStream().buffered().use { input ->
            dest.file.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }
    }
}
