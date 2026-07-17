package com.selfspace.lockzipas.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.selfspace.lockzipas.archive.ArchiveOutputWriter
import com.selfspace.lockzipas.core.ArchivePathValidator
import com.selfspace.lockzipas.model.ArchiveEntry
import java.io.IOException
import java.io.OutputStream

class DocumentTreeArchiveWriter(
    private val context: Context,
    outputTreeUri: Uri,
    archiveDisplayName: String
) : ArchiveOutputWriter {
    private val treeRoot: DocumentFile = requireNotNull(
        DocumentFile.fromTreeUri(context, outputTreeUri)
    ) { "Cannot open output tree" }

    private val outputRoot: DocumentFile = createUniqueOutputDirectory(
        ArchivePathValidator.sanitizeDirectoryName(archiveDisplayName)
    )

    override val outputDirectoryName: String = requireNotNull(outputRoot.name)

    override fun open(entry: ArchiveEntry): OutputStream? {
        val segments = ArchivePathValidator.segmentsFor(entry.path)
        val directory = segments.dropLast(1).fold(outputRoot) { current, segment ->
            current.findFile(segment) ?: current.createDirectory(segment)
                ?: throw IOException("Cannot create directory $segment")
        }

        if (entry.isFolder) {
            directory.findFile(segments.last()) ?: directory.createDirectory(segments.last())
            return null
        }

        if (directory.findFile(segments.last()) != null) {
            throw IOException("Refusing to overwrite ${entry.path}")
        }

        val document = directory.createFile("application/octet-stream", segments.last())
            ?: throw IOException("Cannot create output file ${entry.path}")
        return context.contentResolver.openOutputStream(document.uri)
            ?: throw IOException("Cannot open output stream ${entry.path}")
    }

    override fun close() = Unit

    private fun createUniqueOutputDirectory(baseName: String): DocumentFile {
        for (suffix in 0..999) {
            val candidate = if (suffix == 0) baseName else "$baseName-$suffix"
            if (treeRoot.findFile(candidate) == null) {
                return treeRoot.createDirectory(candidate)
                    ?: throw IOException("Cannot create output directory $candidate")
            }
        }
        throw IOException("Cannot allocate unique output directory")
    }
}
