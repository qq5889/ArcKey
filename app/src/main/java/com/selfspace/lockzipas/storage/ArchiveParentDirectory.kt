package com.selfspace.lockzipas.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class ParentDirectorySuggestion(
    val treeUri: Uri,
    val label: String
)

object ArchiveParentDirectory {
    fun infer(context: Context, archiveUri: Uri): ParentDirectorySuggestion? {
        if (!DocumentsContract.isDocumentUri(context, archiveUri)) return null
        if (archiveUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null

        val documentId = runCatching { DocumentsContract.getDocumentId(archiveUri) }.getOrNull()
            ?: return null
        val parentId = parentDocumentId(documentId) ?: return null
        val treeUri = DocumentsContract.buildTreeDocumentUri(archiveUri.authority, parentId)
        return ParentDirectorySuggestion(
            treeUri = treeUri,
            label = "默认：压缩包所在目录 ${friendlyLabel(parentId)}"
        )
    }

    fun hasPersistedWritePermission(context: Context, treeUri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isWritePermission
        }
    }

    private fun parentDocumentId(documentId: String): String? {
        val slash = documentId.lastIndexOf('/')
        if (slash > 0) return documentId.substring(0, slash)

        val colon = documentId.indexOf(':')
        if (colon >= 0) return documentId.substring(0, colon + 1)

        return null
    }

    private fun friendlyLabel(documentId: String): String {
        val afterRoot = documentId.substringAfter(':', "")
        return if (afterRoot.isBlank()) "/" else "/$afterRoot"
    }

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
}
