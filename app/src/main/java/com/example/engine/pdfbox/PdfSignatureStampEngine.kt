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
        outputFileName: String,
        horizontalPosition: Float = 0.78f,
        verticalPosition: Float = 0.08f,
        widthFraction: Float = 0.38f
    ): File = withContext(Dispatchers.IO) {
        require(signaturePng.isNotEmpty()) { "The signature image is empty." }
        PDFBoxResourceLoader.init(context)
        val source = File(context.cacheDir, "signature-stamp-source-${System.nanoTime()}.pdf")
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
                val placement = calculateSignaturePlacement(
                    pageWidth = box.width, pageHeight = box.height,
                    imageWidth = image.width.toFloat(), imageHeight = image.height.toFloat(),
                    horizontalPosition = horizontalPosition, verticalPosition = verticalPosition,
                    widthFraction = widthFraction
                )
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)
                    .use { it.drawImage(image, placement.x, placement.y, placement.width, placement.height) }
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


/** PDF-space placement. Positions are normalized from the left and bottom edges. */
data class SignaturePlacement(val x: Float, val y: Float, val width: Float, val height: Float)

internal fun calculateSignaturePlacement(
    pageWidth: Float, pageHeight: Float, imageWidth: Float, imageHeight: Float,
    horizontalPosition: Float, verticalPosition: Float, widthFraction: Float
): SignaturePlacement {
    require(pageWidth.isFinite() && pageHeight.isFinite() && pageWidth > 0f && pageHeight > 0f) {
        "The selected PDF page has invalid dimensions."
    }
    require(imageWidth.isFinite() && imageHeight.isFinite() && imageWidth > 0f && imageHeight > 0f) {
        "The signature image has invalid dimensions."
    }
    require(horizontalPosition.isFinite() && verticalPosition.isFinite() && widthFraction.isFinite()) {
        "Signature placement values must be finite."
    }
    val horizontal = horizontalPosition.coerceIn(0f, 1f)
    val vertical = verticalPosition.coerceIn(0f, 1f)
    val aspectRatio = imageHeight / imageWidth
    val width = minOf(pageWidth * widthFraction.coerceIn(0.10f, 0.70f), pageHeight * 0.45f / aspectRatio, pageWidth)
    val height = width * aspectRatio
    return SignaturePlacement((pageWidth - width) * horizontal, (pageHeight - height) * vertical, width, height)
}
