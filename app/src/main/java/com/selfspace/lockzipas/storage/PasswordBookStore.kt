package com.selfspace.lockzipas.storage

import android.content.Context
import android.net.Uri
import com.selfspace.lockzipas.core.PasswordBookLines
import java.nio.charset.StandardCharsets

class PasswordBookStore(
    private val context: Context
) {
    fun readPasswords(uri: Uri): List<String> {
        return readText(uri).let(PasswordBookLines::parse)
    }

    fun appendIfMissing(uri: Uri, password: String): Boolean {
        val existingText = readText(uri)
        val result = PasswordBookLines.appendIfMissing(existingText, password)
        if (!result.appended) return false

        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Cannot open password book for writing" }
            output.write(result.text.toByteArray(StandardCharsets.UTF_8))
        }
        return true
    }

    private fun readText(uri: Uri): String {
        return context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                ""
            } else {
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
        }
    }
}
