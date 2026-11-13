package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DocumentType {
    PDF,
    WORD,
    IMAGE,
    EXCEL,
    POWERPOINT,
    TEXT,
    OTHER
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val mimeType: String,
    val documentType: DocumentType,
    val sizeBytes: Long,
    val pageCount: Int = 0,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isSample: Boolean = false,
    val localFilePath: String? = null
)
