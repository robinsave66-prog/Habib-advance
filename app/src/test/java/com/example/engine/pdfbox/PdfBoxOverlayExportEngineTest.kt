package com.example.engine.pdfbox

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.engine.editor.TextElement
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfBoxOverlayExportEngineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var source: File
    private lateinit var engine: PdfBoxOverlayExportEngine

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        engine = PdfBoxOverlayExportEngine(context)
        source = File(context.cacheDir, "vector-export-source.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 540f)
                stream.showText("Original searchable text")
                stream.endText()
            }
            document.save(source)
        }
    }

    @Test
    fun exportMapsOverlayCoordinatesForClockwiseRotatedPage() {
        runBlocking {
        val rotatedSource = File(context.cacheDir, "vector-export-rotated-source.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            page.rotation = 90
            document.addPage(page)
            document.save(rotatedSource)
        }

        // Coordinates are expressed in the rotated page view (600 x 420 points).
        val overlay = TextElement(
            pageIndex = 0,
            x = 100f,
            y = 50f,
            width = 120f,
            height = 24f,
            text = "Rotated overlay text",
            fontSizePt = 12f
        )
        val output = engine.export(
            Uri.fromFile(rotatedSource),
            mapOf(0 to listOf(overlay)),
            "rotated-vector-overlay-test.pdf"
        )

        PDDocument.load(output).use { result ->
            assertEquals(90, result.getPage(0).rotation)
            assertEquals(420f, result.getPage(0).mediaBox.width, 0.01f)
            assertEquals(600f, result.getPage(0).mediaBox.height, 0.01f)
            assertTrue(PDFTextStripper().getText(result).contains("Rotated overlay text"))
        }
        assertTrue(rotatedSource.exists())
        assertTrue(output.length() > 0L)
        rotatedSource.delete()
        }
    }

    @Test
    fun exportAppendsTextAndPreservesOriginalSearchablePageContent() {
        runBlocking {
        val overlay = TextElement(
            pageIndex = 0,
            x = 40f,
            y = 80f,
            width = 220f,
            height = 28f,
            text = "Added vector text",
            fontSizePt = 14f,
            textColor = 0xFF123456
        )
        val output = engine.export(
            Uri.fromFile(source),
            mapOf(0 to listOf(overlay)),
            "vector-preserving-test.pdf"
        )
        PDDocument.load(output).use { result ->
            assertEquals(1, result.numberOfPages)
            assertEquals(420f, result.getPage(0).mediaBox.width, 0.01f)
            val extracted = PDFTextStripper().getText(result)
            assertTrue(extracted.contains("Original searchable text"))
            assertTrue(extracted.contains("Added vector text"))
        }
        assertTrue(output.length() > 0L)
        assertTrue(source.exists())
        }
    }
}
