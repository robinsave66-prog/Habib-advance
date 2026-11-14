package com.example.engine.pdfbox

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Adds a transparent PNG signature while retaining all original PDF page content. */
class PdfSignatureStampEngine(private val context: Context) {
    suspend fun stamp(
        sourceUri: Uri,
        signaturePng: ByteArray,
        targetPageOneBased: Int,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        require(signaturePng.isNotEmpty()) { "The signature image is empty." }
        PDFBoxResourceLoader.init(context)
        val source = File(context.cacheDir, "signature-stamp-source-\${System.nanoTime()}.pdf")
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val safeName = outputFileName.substringAfterLast('/').substringAfterLast('\\')
            .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        val output = File(directory, safeName)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(source).use { destination -> input.copyTo(destination) }
            } ?: throw IllegalArgumentException("Cannot open the source PDF.")
            require(source.isFile && source.length() > 0L) { "The source PDF is empty." }
            PDDocument.load(source).use { document ->
                require(document.numberOfPages > 0) { "The source PDF has no pages." }
                require(targetPageOneBased in 1..document.numberOfPages) {
                    "Page $targetPageOneBased is outside this document."
                }
                val page = document.getPage(targetPageOneBased - 1)
                val image = PDImageXObject.createFromByteArray(document, signaturePng, "signature")
                val box = page.mediaBox
                val width = 160f.coerceAtMost(box.width - 20f)
                val height = (width * image.height.toFloat() / image.width.toFloat()).coerceAtMost(100f)
                require(width > 0f && height > 0f && box.width > 0f && box.height > 0f) {
                    "The selected PDF page has invalid dimensions."
                }
                val x = (box.width - width - 40f).coerceAtLeast(10f)
                val y = (box.height - height - 40f).coerceAtLeast(10f)
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    .use { it.drawImage(image, x, y, width, height) }
                document.save(output)
            }
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            source.delete()
        }
    }
}
