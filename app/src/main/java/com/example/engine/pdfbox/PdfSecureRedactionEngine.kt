package com.example.engine.pdfbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Creates a new image-only PDF. Source page content streams, selectable text, annotations,
 * forms, document metadata and embedded files are not copied into the output document.
 */
class PdfSecureRedactionEngine(private val context: Context) {

    suspend fun redact(
        sourceUri: Uri,
        redactionsByPage: Map<Int, List<RectF>>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        require(redactionsByPage.isNotEmpty() && redactionsByPage.values.any { it.isNotEmpty() }) {
            "Select at least one area to redact."
        }
        PDFBoxResourceLoader.init(context)
        val source = File(context.cacheDir, "secure-redaction-source-${System.nanoTime()}.pdf")
        val output = createOutputFile(outputFileName)
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(source).use { destination -> input.copyTo(destination) }
            } ?: throw IllegalArgumentException("Cannot read the source PDF.")
            require(source.isFile && source.length() > 0L) { "The source PDF is empty." }
            descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            require(renderer.pageCount > 0) { "The source PDF has no pages." }
            require(redactionsByPage.keys.all { it in 0 until renderer.pageCount }) {
                "A selected redaction area refers to a page that does not exist."
            }

            PDDocument().use { outputDocument ->
                for (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    var bitmap: Bitmap? = null
                    try {
                        val scale = TARGET_DPI / POINTS_PER_INCH
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        require(width.toLong() * height.toLong() <= MAX_BITMAP_PIXELS) {
                            "Page ${pageIndex + 1} is too large to redact safely at 300 DPI."
                        }
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        val matrix = Matrix().apply {
                            setScale(width.toFloat() / page.width, height.toFloat() / page.height)
                        }
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val boxes = redactionsByPage[pageIndex].orEmpty()
                        if (boxes.isNotEmpty()) {
                            applyRedactionRectangles(bitmap, boxes, page.width.toFloat(), page.height.toFloat())
                        }
                        appendRasterizedPage(outputDocument, bitmap, page.width.toFloat(), page.height.toFloat())
                    } finally {
                        bitmap?.recycle()
                        page.close()
                    }
                }
                outputDocument.save(output)
            }
            require(output.isFile && output.length() > 0L) { "The redacted PDF could not be saved." }
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { descriptor?.close() } catch (_: Exception) {}
            source.delete()
        }
    }

    internal fun applyRedactionRectangles(
        bitmap: Bitmap,
        rectanglesInPoints: List<RectF>,
        pageWidthPoints: Float,
        pageHeightPoints: Float,
        expansionPoints: Float = REDACTION_EXPANSION_POINTS
    ) {
        require(!bitmap.isRecycled) { "Cannot redact a recycled page image." }
        require(pageWidthPoints > 0f && pageHeightPoints > 0f) { "Page dimensions must be positive." }
        val scaleX = bitmap.width / pageWidthPoints
        val scaleY = bitmap.height / pageHeightPoints
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
            alpha = 255
        }
        rectanglesInPoints.forEach { bounds ->
            require(bounds.left.isFinite() && bounds.top.isFinite() &&
                bounds.right.isFinite() && bounds.bottom.isFinite() &&
                bounds.width() > 0f && bounds.height() > 0f) {
                "A redaction area has invalid coordinates."
            }
            val left = ((bounds.left - expansionPoints) * scaleX).coerceIn(0f, bitmap.width.toFloat())
            val top = ((bounds.top - expansionPoints) * scaleY).coerceIn(0f, bitmap.height.toFloat())
            val right = ((bounds.right + expansionPoints) * scaleX).coerceIn(0f, bitmap.width.toFloat())
            val bottom = ((bounds.bottom + expansionPoints) * scaleY).coerceIn(0f, bitmap.height.toFloat())
            require(right > left && bottom > top) {
                "A redaction area does not intersect the rendered page; refusing to save an unredacted result."
            }
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }

    internal fun appendRasterizedPage(
        document: PDDocument,
        bitmap: Bitmap,
        widthPoints: Float,
        heightPoints: Float
    ) {
        require(widthPoints > 0f && heightPoints > 0f) { "Page dimensions must be positive." }
        ByteArrayOutputStream().use { imageBytes ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, imageBytes)) {
                "Unable to encode the redacted page image."
            }
            val page = PDPage(PDRectangle(widthPoints, heightPoints))
            document.addPage(page)
            val image = PDImageXObject.createFromByteArray(document, imageBytes.toByteArray(), "flattened-redacted-page")
            PDPageContentStream(document, page).use { stream ->
                stream.drawImage(image, 0f, 0f, widthPoints, heightPoints)
            }
        }
    }

    private fun createOutputFile(fileName: String): File {
        val directory = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
        val base = fileName.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .removeSuffix(".pdf").ifBlank { "Redacted_Document" }
        var candidate = File(directory, "$base.pdf")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base($suffix).pdf")
            suffix++
        }
        return candidate
    }

    private companion object {
        const val POINTS_PER_INCH = 72f
        const val TARGET_DPI = 300f
        const val MAX_BITMAP_PIXELS = 18_000_000L
        const val REDACTION_EXPANSION_POINTS = 1.5f
    }
}
