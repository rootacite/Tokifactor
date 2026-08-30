package com.acite.tokifactor

import android.content.Context
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual suspend fun copyFileToPlatformFile(source: File, dest: PlatformFile) {
    withContext(Dispatchers.IO) {
        val ctx = requireAppContext()
        ctx.contentResolver.openOutputStream(dest.uri)?.use { output ->
            if (output is FileOutputStream) {
                output.channel.truncate(source.length())
            }
            source.inputStream().buffered().use { input ->
                input.copyTo(output)
            }
        } ?: error("Failed to write file")
    }
}
