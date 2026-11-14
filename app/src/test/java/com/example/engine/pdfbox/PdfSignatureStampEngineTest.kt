package com.example.engine.pdfbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfSignatureStampEngineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var source: File
    private lateinit var engine: PdfSignatureStampEngine

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        engine = PdfSignatureStampEngine(context)
        source = File(context.cacheDir, "signature-stamp-source-test.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 540f)
                stream.showText("Original searchable document content")
                stream.endText()
            }
            document.save(source)
        }
    }

    private fun signaturePng(): ByteArray {
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLUE
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(8f, 40f, 45f, 15f, paint)
        canvas.drawLine(45f, 15f, 90f, 35f, paint)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    @Test
    fun stampAppendsSignatureAndPreservesOriginalTextAndPageGeometry() = runBlocking {
        val output = engine.stamp(Uri.fromFile(source), signaturePng(), 1, "signature-preserve-test.pdf")
        PDDocument.load(output).use { result ->
            assertEquals(1, result.numberOfPages)
            assertEquals(420f, result.getPage(0).mediaBox.width, 0.01f)
            assertEquals(600f, result.getPage(0).mediaBox.height, 0.01f)
            assertTrue(PDFTextStripper().getText(result).contains("Original searchable document content"))
            val resources = result.getPage(0).resources
            assertTrue(resources.xObjectNames.any { resources.getXObject(it) is PDImageXObject })
        }
        PDDocument.load(source).use { original ->
            assertTrue(PDFTextStripper().getText(original).contains("Original searchable document content"))
        }
        assertTrue(output.length() > 0L)
        assertTrue(source.exists())
    }

    @Test
    fun placementHonorsPositionAndPreservesImageAspectRatio() {
        val p = calculateSignaturePlacement(420f, 600f, 120f, 60f, 0.5f, 0.25f, 0.4f)
        assertEquals(126f, p.x, 0.01f)
        assertEquals(129f, p.y, 0.01f)
        assertEquals(168f, p.width, 0.01f)
        assertEquals(84f, p.height, 0.01f)
    }

    @Test
    fun placementClampsInsidePageAndRejectsInvalidDimensions() {
        val p = calculateSignaturePlacement(100f, 80f, 100f, 100f, 2f, -1f, 0.7f)
        assertTrue(p.x >= 0f && p.y >= 0f)
        assertTrue(p.x + p.width <= 100f && p.y + p.height <= 80f)
        assertThrows(IllegalArgumentException::class.java) {
            calculateSignaturePlacement(0f, 80f, 10f, 10f, 0.5f, 0.5f, 0.4f)
        }
    }

    @Test
    fun rejectsInvalidPageWithoutCreatingOutput() = runBlocking {
        val outputName = "signature-invalid-page-test.pdf"
        val output = File(context.filesDir, "documents/$outputName")
        output.delete()
        var rejected = false
        try {
            engine.stamp(Uri.fromFile(source), signaturePng(), 2, outputName)
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertFalse(output.exists())
        assertTrue(source.exists())
    }
}
