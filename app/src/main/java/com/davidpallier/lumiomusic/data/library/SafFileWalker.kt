package com.davidpallier.lumiomusic.data.library

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SafAudioFile(
    val uri: Uri,
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long
)

/**
 * Recursively lists audio files under a SAF tree using raw [DocumentsContract] queries.
 * Deliberately avoids [androidx.documentfile.provider.DocumentFile.listFiles], which issues
 * one extra content-provider round trip per child per property and is too slow for real
 * libraries (hundreds to thousands of files).
 */
object SafFileWalker {

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    suspend fun walk(context: Context, treeUri: Uri): List<SafAudioFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SafAudioFile>()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val pendingDirs = ArrayDeque<String>()
        pendingDirs.addLast(rootDocumentId)

        val resolver = context.contentResolver
        while (pendingDirs.isNotEmpty()) {
            val parentDocumentId = pendingDirs.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

            val cursor: Cursor = try {
                resolver.query(childrenUri, PROJECTION, null, null, null) ?: continue
            } catch (e: Exception) {
                continue
            }

            cursor.use {
                val idxId = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val idxName = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idxMime = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val idxSize = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val idxModified = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (it.moveToNext()) {
                    val documentId = it.getString(idxId) ?: continue
                    val displayName = it.getString(idxName) ?: continue
                    val mimeType = it.getString(idxMime) ?: ""
                    val size = it.getLong(idxSize)
                    val lastModified = it.getLong(idxModified)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pendingDirs.addLast(documentId)
                    } else if (AudioFileFilter.matches(displayName, mimeType)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                        results += SafAudioFile(docUri, documentId, displayName, mimeType, size, lastModified)
                    }
                }
            }
        }
        results
    }
}
