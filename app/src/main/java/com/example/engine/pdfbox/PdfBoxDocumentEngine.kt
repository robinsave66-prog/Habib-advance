package com.example.engine.pdfbox

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Open-source PDF structure operations.
 *
 * Unlike PdfRenderer/PdfDocument bitmap export, these operations work on PDF page
 * objects and retain vector/text content where the underlying PDFBox operation supports it.
 * This engine intentionally does not claim arbitrary inline text-object replacement.
 */
class PdfBoxDocumentEngine(private val context: Context) {

    suspend fun getPageCount(sourceUri: Uri): Int = withContext(Dispatchers.IO) {
        withSource(sourceUri) { document -> document.numberOfPages }
    }

    suspend fun deletePages(
        sourceUri: Uri,
        pageIndices: Set<Int>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        require(pageIndices.isNotEmpty()) { "Select at least one page to delete." }
        editSource(sourceUri, outputFileName) { document ->
            val pages = document.pages.toList()
            require(pageIndices.all { it in pages.indices }) { "A selected page is outside the document." }
            require(pages.size - pageIndices.size >= 1) { "A PDF must contain at least one page." }
            pages.forEachIndexed { index, page ->
                if (index in pageIndices) document.pages.remove(page)
            }
        }
    }

    /**
     * Reorders every page. order must be a complete permutation of 0 until pageCount.
     */
    suspend fun reorderPages(
        sourceUri: Uri,
        order: List<Int>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        editSource(sourceUri, outputFileName) { document ->
            val pages = document.pages.toList()
            require(order.size == pages.size && order.toSet() == pages.indices.toSet()) {
                "Page order must contain every page exactly once."
            }
            pages.forEach { document.pages.remove(it) }
            order.forEach { originalIndex -> document.pages.add(pages[originalIndex]) }
        }
    }

    suspend fun duplicatePage(
        sourceUri: Uri,
        pageIndex: Int,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        editSource(sourceUri, outputFileName) { document ->
            require(pageIndex in 0 until document.numberOfPages) { "Select a valid page to duplicate." }
            val original = document.getPage(pageIndex)
            // PDFBox importPage may append the imported page to the page tree.
            // Remove that appended entry before inserting it at the requested position.
            val duplicate = document.importPage(original)
            document.pages.remove(duplicate)
            document.pages.insertAfter(duplicate, original)
        }
    }

    suspend fun insertBlankPage(
        sourceUri: Uri,
        position: Int,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        editSource(sourceUri, outputFileName) { document ->
            require(position in 0..document.numberOfPages) { "Invalid page insertion position." }
            // Prefer the page immediately before the insertion point; at the
            // beginning, use the first page as the size template.
            val reference = when {
                document.numberOfPages == 0 -> null
                position > 0 -> document.getPage(position - 1)
                else -> document.getPage(0)
            }
            val mediaBox = reference?.mediaBox ?: PDRectangle.LETTER
            val blank = PDPage(PDRectangle(mediaBox.width, mediaBox.height))
            if (position == document.numberOfPages) {
                document.pages.add(blank)
            } else {
                document.pages.insertBefore(blank, document.getPage(position))
            }
        }
    }

    suspend fun rotatePages(
        sourceUri: Uri,
        pageIndices: Set<Int>,
        degrees: Int,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        require(degrees % 90 == 0) { "Rotation must be a multiple of 90 degrees." }
        editSource(sourceUri, outputFileName) { document ->
            val pages = document.pages.toList()
            require(pageIndices.isNotEmpty() && pageIndices.all { it in pages.indices }) {
                "Select valid pages to rotate."
            }
            pageIndices.forEach { index ->
                val page = pages[index]
                page.rotation = ((page.rotation + degrees) % 360 + 360) % 360
            }
        }
    }

    suspend fun extractPages(
        sourceUri: Uri,
        pageIndices: List<Int>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        require(pageIndices.isNotEmpty()) { "Select at least one page to extract." }
        withSource(sourceUri) { source ->
            val pages = source.pages.toList()
            require(pageIndices.distinct().size == pageIndices.size && pageIndices.all { it in pages.indices }) {
                "Page selection contains duplicates or invalid page numbers."
            }
            val output = createOutputFile(outputFileName)
            PDDocument().use { destination ->
                pageIndices.forEach { index -> destination.importPage(pages[index]) }
                destination.save(output)
            }
            output
        }
    }

    suspend fun mergePdfs(
        sourceUris: List<Uri>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        require(sourceUris.size >= 2) { "Choose at least two PDF files to merge." }
        PDFBoxResourceLoader.init(context)
        val output = createOutputFile(outputFileName)
        val temporaryFiles = mutableListOf<File>()
        try {
            val merger = PDFMergerUtility()
            sourceUris.forEach { uri ->
                val temp = copyUriToCache(uri)
                temporaryFiles += temp
                merger.addSource(temp)
            }
            merger.destinationFileName = output.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            temporaryFiles.forEach(File::delete)
        }
    }

    private suspend fun editSource(
        sourceUri: Uri,
        outputFileName: String,
        edit: (PDDocument) -> Unit
    ): File {
        return withSource(sourceUri) { document ->
            edit(document)
            val output = createOutputFile(outputFileName)
            try {
                document.save(output)
                output
            } catch (error: Exception) {
                output.delete()
                throw error
            }
        }
    }

    private suspend fun <T> withSource(sourceUri: Uri, block: (PDDocument) -> T): T {
        PDFBoxResourceLoader.init(context)
        val source = copyUriToCache(sourceUri)
        return try {
            PDDocument.load(source).use(block)
        } finally {
            source.delete()
        }
    }

    private suspend fun copyUriToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        val source = File(context.cacheDir, "pdfbox-source-${UUID.randomUUID()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(source).use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("Cannot open the selected PDF.")
            require(source.length() > 0L) { "The selected PDF is empty." }
            source
        } catch (error: Exception) {
            source.delete()
            throw error
        }
    }

    private fun createOutputFile(fileName: String): File {
        val directory = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
        val safeName = fileName.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .removeSuffix(".pdf")
            .ifBlank { "Edited_Document" } + ".pdf"
        val target = File(directory, safeName)
        require(!target.exists()) { "A file with this name already exists. Choose another name." }
        return target
    }
}
