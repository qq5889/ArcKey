package com.selfspace.lockzipas.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

object UriDisplayName {
    fun resolve(context: Context, uri: Uri): String {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        ).use { cursor: Cursor? ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
        return displayName ?: uri.lastPathSegment ?: "archive"
    }
}
