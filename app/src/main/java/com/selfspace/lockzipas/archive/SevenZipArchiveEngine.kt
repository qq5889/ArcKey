package com.selfspace.lockzipas.archive

import com.selfspace.lockzipas.model.ArchiveEntry
import com.selfspace.lockzipas.model.ExtractionSummary
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream

class SevenZipArchiveEngine(
    private val archiveFile: File
) : ArchiveEngine {
    override fun list(password: String?): List<ArchiveEntry> {
        return withArchive(password) { archive ->
            buildList {
                for (index in 0 until archive.numberOfItems) {
                    add(archive.entryAt(index))
                }
            }
        }
    }

    override fun tryPassword(password: String): Boolean {
        return try {
            withArchive(password) { archive ->
                val firstFile = firstFileEntry(archive) ?: return@withArchive true
                archive.extract(
                    intArrayOf(firstFile.index),
                    true,
                    PasswordTestCallback(password)
                )
                true
            }
        } catch (_: SevenZipException) {
            false
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    override fun extract(
        password: String?,
        outputWriter: ArchiveOutputWriter,
        onBytesWritten: (Long) -> Unit
    ): ExtractionSummary {
        return outputWriter.use { writer ->
            withArchive(password) { archive ->
                val entriesByIndex = buildMap {
                    for (index in 0 until archive.numberOfItems) {
                        put(index, archive.entryAt(index))
                    }
                }
                val callback = ExtractCallback(
                    password = password.orEmpty(),
                    entriesByIndex = entriesByIndex,
                    outputWriter = writer,
                    onBytesWritten = onBytesWritten
                )
                archive.extract(null, false, callback)
                ExtractionSummary(
                    filesWritten = callback.filesWritten,
                    bytesWritten = callback.bytesWritten,
                    outputDirectoryName = writer.outputDirectoryName
                )
            }
        }
    }

    private fun firstFileEntry(archive: IInArchive): ArchiveEntry? {
        for (index in 0 until archive.numberOfItems) {
            val entry = archive.entryAt(index)
            if (!entry.isFolder) return entry
        }
        return null
    }

    private fun <T> withArchive(password: String?, block: (IInArchive) -> T): T {
        SevenZipLoader.ensureInitialized()
        RandomAccessFile(archiveFile, "r").use { randomAccessFile ->
            val inStream = RandomAccessFileInStream(randomAccessFile)
            val archive = SevenZip.openInArchive(null, inStream, password.orEmpty())
            return try {
                block(archive)
            } finally {
                archive.close()
                inStream.close()
            }
        }
    }

    private fun IInArchive.entryAt(index: Int): ArchiveEntry {
        val rawPath = getStringProperty(index, PropID.PATH).orEmpty()
        val path = rawPath.ifBlank { "entry-$index" }
        val isFolder = (getProperty(index, PropID.IS_FOLDER) as? Boolean) ?: false
        val size = (getProperty(index, PropID.SIZE) as? Number)?.toLong() ?: 0L
        return ArchiveEntry(index = index, path = path, size = size, isFolder = isFolder)
    }
}

private object SevenZipLoader {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                SevenZip.initSevenZipFromPlatformJAR()
                initialized = true
            }
        }
    }
}

private class PasswordTestCallback(
    private val password: String
) : IArchiveExtractCallback, ICryptoGetTextPassword {
    override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
        return ISequentialOutStream { data -> data.size }
    }

    override fun prepareOperation(extractAskMode: ExtractAskMode) = Unit

    override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
        if (extractOperationResult != ExtractOperationResult.OK) {
            throw SevenZipException("Password check failed: $extractOperationResult")
        }
    }

    override fun setTotal(total: Long) = Unit

    override fun setCompleted(completeValue: Long) = Unit

    override fun cryptoGetTextPassword(): String = password
}

private class ExtractCallback(
    private val password: String,
    private val entriesByIndex: Map<Int, ArchiveEntry>,
    private val outputWriter: ArchiveOutputWriter,
    private val onBytesWritten: (Long) -> Unit
) : IArchiveExtractCallback, ICryptoGetTextPassword {
    var filesWritten: Int = 0
        private set

    var bytesWritten: Long = 0L
        private set

    private var currentEntry: ArchiveEntry? = null
    private var currentStream: OutputStream? = null

    override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
        if (extractAskMode != ExtractAskMode.EXTRACT) return null

        val entry = entriesByIndex[index] ?: return null
        currentEntry = entry
        currentStream = null

        return try {
            val stream = outputWriter.open(entry) ?: return null
            currentStream = stream
            ISequentialOutStream { data ->
                try {
                    stream.write(data)
                    bytesWritten += data.size.toLong()
                    onBytesWritten(data.size.toLong())
                    data.size
                } catch (io: IOException) {
                    throw SevenZipException("Failed to write extracted data: ${io.message}")
                }
            }
        } catch (error: RuntimeException) {
            throw SevenZipException("Unsafe or unwritable archive entry: ${error.message}")
        } catch (io: IOException) {
            throw SevenZipException("Failed to open output stream: ${io.message}")
        }
    }

    override fun prepareOperation(extractAskMode: ExtractAskMode) = Unit

    override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
        currentStream?.close()
        val entry = currentEntry
        currentStream = null
        currentEntry = null

        if (extractOperationResult != ExtractOperationResult.OK) {
            throw SevenZipException("Extraction failed: $extractOperationResult")
        }
        if (entry != null && !entry.isFolder) {
            filesWritten += 1
        }
    }

    override fun setTotal(total: Long) = Unit

    override fun setCompleted(completeValue: Long) = Unit

    override fun cryptoGetTextPassword(): String = password
}
