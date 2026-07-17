package com.selfspace.lockzipas.service

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.selfspace.lockzipas.core.CheckpointCodec
import com.selfspace.lockzipas.model.CrackCheckpoint
import com.selfspace.lockzipas.model.CrackConfig
import java.nio.charset.StandardCharsets

class CrackRequestStore(context: Context) {
    private val prefs = context.getSharedPreferences("crack_request", Context.MODE_PRIVATE)

    fun saveConfig(config: CrackConfig) {
        prefs.edit {
            putString(KEY_ARCHIVE_URI, config.archiveUri)
            putString(KEY_ARCHIVE_NAME, config.archiveDisplayName)
            putString(KEY_OUTPUT_URI, config.outputTreeUri)
            putString(KEY_BOOK_URI, config.passwordBookUri)
            putString(KEY_MANUAL, encodeList(config.manualPasswords))
            putString(KEY_CHARSET, config.charset)
            putInt(KEY_MIN_LENGTH, config.minLength)
            putInt(KEY_MAX_LENGTH, config.maxLength)
            putInt(KEY_WORKERS, config.workerCount)
        }
    }

    fun loadConfig(): CrackConfig? {
        val archiveUri = prefs.getString(KEY_ARCHIVE_URI, null) ?: return null
        val outputUri = prefs.getString(KEY_OUTPUT_URI, null) ?: return null
        return CrackConfig(
            archiveUri = archiveUri,
            archiveDisplayName = prefs.getString(KEY_ARCHIVE_NAME, "archive") ?: "archive",
            outputTreeUri = outputUri,
            passwordBookUri = prefs.getString(KEY_BOOK_URI, null),
            manualPasswords = decodeList(prefs.getString(KEY_MANUAL, "")),
            charset = prefs.getString(KEY_CHARSET, "0123456789") ?: "0123456789",
            minLength = prefs.getInt(KEY_MIN_LENGTH, 1),
            maxLength = prefs.getInt(KEY_MAX_LENGTH, 6),
            workerCount = prefs.getInt(KEY_WORKERS, CrackForegroundService.defaultWorkerCount()).coerceIn(1, 8),
            resumeCheckpoint = loadCheckpoint()
        )
    }

    fun saveCheckpoint(checkpoint: CrackCheckpoint) {
        prefs.edit {
            putString(KEY_CHECKPOINT, CheckpointCodec.encode(checkpoint))
        }
    }

    fun loadCheckpoint(): CrackCheckpoint? {
        return CheckpointCodec.decode(prefs.getString(KEY_CHECKPOINT, null))
    }

    fun clearCheckpoint() {
        prefs.edit {
            remove(KEY_CHECKPOINT)
        }
    }

    fun clearConfig() {
        prefs.edit {
            clear()
        }
    }

    private fun encodeList(values: List<String>): String {
        return values.joinToString("\n") { value ->
            Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decodeList(value: String?): List<String> {
        return value.orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching {
                    String(Base64.decode(line, Base64.DEFAULT), StandardCharsets.UTF_8)
                }.getOrNull()
            }
            .toList()
    }

    private companion object {
        const val KEY_ARCHIVE_URI = "archive_uri"
        const val KEY_ARCHIVE_NAME = "archive_name"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_BOOK_URI = "book_uri"
        const val KEY_MANUAL = "manual"
        const val KEY_CHARSET = "charset"
        const val KEY_MIN_LENGTH = "min_length"
        const val KEY_MAX_LENGTH = "max_length"
        const val KEY_WORKERS = "workers"
        const val KEY_CHECKPOINT = "checkpoint"
    }
}
