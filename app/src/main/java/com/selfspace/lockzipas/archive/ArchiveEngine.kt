package com.selfspace.lockzipas.archive

import com.selfspace.lockzipas.model.ArchiveEntry
import com.selfspace.lockzipas.model.ExtractionSummary

interface ArchiveEngine {
    fun list(password: String? = null): List<ArchiveEntry>
    fun tryPassword(password: String): Boolean
    fun extract(
        password: String?,
        outputWriter: ArchiveOutputWriter,
        onBytesWritten: (Long) -> Unit = {}
    ): ExtractionSummary
}

interface ArchiveOutputWriter : AutoCloseable {
    val outputDirectoryName: String
    fun open(entry: ArchiveEntry): java.io.OutputStream?
}
