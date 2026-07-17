package com.selfspace.lockzipas.storage

import android.content.Context
import android.net.Uri
import com.selfspace.lockzipas.core.ArchivePathValidator
import java.io.File

class ArchiveCache(
    private val context: Context
) {
    fun copyToCache(archiveUri: Uri, displayName: String): File {
        val cacheDir = File(context.cacheDir, "archives").also { it.mkdirs() }
        val safeName = ArchivePathValidator.sanitizeDirectoryName(displayName)
        val target = File(cacheDir, "${System.currentTimeMillis()}_$safeName.archive")

        context.contentResolver.openInputStream(archiveUri).use { input ->
            requireNotNull(input) { "Cannot open archive input stream" }
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }
}
