package com.selfspace.lockzipas.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun saveLastPasswordBook(uri: Uri, label: String) {
        prefs.edit {
            putString(KEY_LAST_PASSWORD_BOOK_URI, uri.toString())
            putString(KEY_LAST_PASSWORD_BOOK_LABEL, label)
        }
    }

    fun lastPasswordBookUri(): Uri? {
        return prefs.getString(KEY_LAST_PASSWORD_BOOK_URI, null)?.let(Uri::parse)
    }

    fun lastPasswordBookLabel(): String {
        return prefs.getString(KEY_LAST_PASSWORD_BOOK_LABEL, "") ?: ""
    }

    private companion object {
        const val KEY_LAST_PASSWORD_BOOK_URI = "last_password_book_uri"
        const val KEY_LAST_PASSWORD_BOOK_LABEL = "last_password_book_label"
    }
}
