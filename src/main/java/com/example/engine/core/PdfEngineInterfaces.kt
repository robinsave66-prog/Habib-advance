package com.example.engine.core

import android.graphics.Bitmap
import android.net.Uri

sealed interface PdfLoadResult {
    data class Success(val documentInfo: PdfDocumentInfo) : PdfLoadResult
    data class PasswordRequired(val message: String = "This document is password protected") : PdfLoadResult
    data class Corrupted(val message: String = "Unable to read PDF file. File may be corrupted.") : PdfLoadResult
    data class Error(val throwable: Throwable) : PdfLoadResult
}

data class PdfDocumentInfo(
    val uri: Uri,
    val title: String,
    val pageCount: Int,
    val fileSize: Long,
    val isEncrypted: Boolean = false
)

data class PageRenderRequest(
    val pageIndex: Int,
    val targetWidth: Int,
    val targetHeight: Int,
    val zoomFactor: Float = 1.0f
)

interface IPdfRenderer {
    suspend fun openDocument(uri: Uri): PdfLoadResult
    suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap?
    fun getPageCount(): Int
    fun getPageDimensions(pageIndex: Int): Pair<Int, Int>?
    fun close()
}

interface IPdfDocumentLoader {
    suspend fun load(uri: Uri): PdfLoadResult
}

interface IPdfPageManager {
    suspend fun extractPages(sourceUri: Uri, pageIndices: List<Int>, outputUri: Uri): Boolean
    suspend fun deletePages(sourceUri: Uri, pageIndices: List<Int>, outputUri: Uri): Boolean
    suspend fun rotatePages(sourceUri: Uri, pageIndices: List<Int>, degrees: Int, outputUri: Uri): Boolean
}

interface IPdfMerger {
    suspend fun mergePdfs(sourceUris: List<Uri>, outputUri: Uri): Boolean
}

interface IPdfSplitter {
    suspend fun splitPdf(sourceUri: Uri, splitEveryNPages: Int, outputDirectoryUri: Uri): List<Uri>
}

interface IPdfCompressor {
    suspend fun compressPdf(sourceUri: Uri, outputUri: Uri, qualityLevel: Int): Boolean
}

interface IPdfExporter {
    suspend fun exportAsPdf(pages: List<Bitmap>, outputUri: Uri): Boolean
}
