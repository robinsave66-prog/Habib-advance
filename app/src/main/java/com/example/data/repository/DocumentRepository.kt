package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.dao.DocumentDao
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentType
import kotlinx.coroutines.flow.Flow
import java.io.File

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val context: Context
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val recentDocuments: Flow<List<DocumentEntity>> = documentDao.getRecentDocuments(15)
    val favoriteDocuments: Flow<List<DocumentEntity>> = documentDao.getFavoriteDocuments()

    fun getDocumentsByType(type: DocumentType): Flow<List<DocumentEntity>> =
        documentDao.getDocumentsByType(type)

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> =
        documentDao.searchDocuments(query)

    suspend fun getDocumentByUri(uri: String): DocumentEntity? =
        documentDao.getDocumentByUri(uri)

    suspend fun registerOpenedDocument(
        uri: Uri,
        customName: String? = null,
        customType: DocumentType? = null,
        pageCount: Int = 0,
        isSample: Boolean = false,
        localPath: String? = null
    ): DocumentEntity {
        val uriString = uri.toString()
        val existing = documentDao.getDocumentByUri(uriString)

        val resolvedName = customName ?: getFileName(uri) ?: "Untitled Document"
        val resolvedMime = context.contentResolver.getType(uri) ?: "application/pdf"
        val resolvedType = customType ?: inferDocumentType(resolvedName, resolvedMime)
        val resolvedSize = getFileSize(uri)

        val entity = DocumentEntity(
            uri = uriString,
            name = resolvedName,
            mimeType = resolvedMime,
            documentType = resolvedType,
            sizeBytes = if (resolvedSize > 0) resolvedSize else (existing?.sizeBytes ?: 0L),
            pageCount = if (pageCount > 0) pageCount else (existing?.pageCount ?: 0),
            lastOpenedTimestamp = System.currentTimeMillis(),
            isFavorite = existing?.isFavorite ?: false,
            isSample = isSample,
            localFilePath = localPath ?: existing?.localFilePath
        )

        documentDao.insertOrUpdate(entity)
        return entity
    }

    suspend fun toggleFavorite(uri: String, currentStatus: Boolean) {
        documentDao.setFavorite(uri, !currentStatus)
    }

    suspend fun removeDocument(uri: String) {
        documentDao.deleteByUri(uri)
    }

    suspend fun updatePageCount(uri: String, pages: Int) {
        val existing = documentDao.getDocumentByUri(uri)
        if (existing != null) {
            documentDao.update(existing.copy(pageCount = pages, lastOpenedTimestamp = System.currentTimeMillis()))
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").name
        }
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }

    private fun getFileSize(uri: Uri): Long {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").length()
        }
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        return size
    }

    private fun inferDocumentType(name: String, mimeType: String): DocumentType {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".pdf") || mimeType == "application/pdf" -> DocumentType.PDF
            lower.endsWith(".docx") || lower.endsWith(".doc") || mimeType.contains("word") -> DocumentType.WORD
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || mimeType.startsWith("image/") -> DocumentType.IMAGE
            lower.endsWith(".xlsx") || lower.endsWith(".xls") || mimeType.contains("excel") || mimeType.contains("spreadsheet") -> DocumentType.EXCEL
            lower.endsWith(".pptx") || lower.endsWith(".ppt") || mimeType.contains("presentation") -> DocumentType.POWERPOINT
            lower.endsWith(".txt") || mimeType.startsWith("text/") -> DocumentType.TEXT
            else -> DocumentType.OTHER
        }
    }
}
