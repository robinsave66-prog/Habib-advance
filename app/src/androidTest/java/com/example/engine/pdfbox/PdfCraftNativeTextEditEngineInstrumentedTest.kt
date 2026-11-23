package com.example.engine.pdfbox

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDFormContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfCraftNativeTextEditEngineInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var engine: PdfCraftNativeTextEditEngine

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        engine = PdfCraftNativeTextEditEngine(context)
    }

    @Test
    fun replacesParagraphTextAndReopensTheSavedPdf() = runBlocking {
        val source = createPdf("Start original phrase End")
        val output = engine.replaceText(
            sourceUri = Uri.fromFile(source),
            pageIndex = 0,
            oldText = "original phrase",
            newText = "updated paragraph",
            outputFileName = "pdfcraft-native-paragraph-test.pdf"
        )
        PDDocument.load(output).use { edited ->
            val extracted = PDFTextStripper().getText(edited)
            assertTrue(extracted.contains("Start updated paragraph End"))
            assertFalse(extracted.contains("Start original phrase End"))
        }
        PDDocument.load(source).use { original ->
            assertTrue(PDFTextStripper().getText(original).contains("Start original phrase End"))
        }
    }

    @Test
    fun editsTextInsideAFormXObject() = runBlocking {
        val source = File(context.cacheDir, "pdfcraft-form-source-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            val form = PDFormXObject(document)
            form.bBox = PDRectangle(260f, 90f)
            form.resources = PDResources()
            PDFormContentStream(form).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(8f, 45f)
                stream.showText("Form paragraph original")
                stream.endText()
            }
            PDPageContentStream(document, page).use { it.drawForm(form) }
            document.save(source)
        }

        val output = engine.replaceText(
            sourceUri = Uri.fromFile(source),
            pageIndex = 0,
            oldText = "paragraph original",
            newText = "paragraph updated",
            outputFileName = "pdfcraft-form-text-test.pdf"
        )
        PDDocument.load(output).use { edited ->
            val extracted = PDFTextStripper().getText(edited)
            assertTrue(extracted.contains("Form paragraph updated"))
            assertFalse(extracted.contains("Form paragraph original"))
        }
    }


    @Test
    fun preservesSurroundingTextFontSizeAndBaselineAfterParagraphReplacement() = runBlocking {
        val source = File(context.cacheDir, "pdfcraft-style-source-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                stream.newLineAtOffset(52f, 520f)
                stream.showText("Start original phrase End")
                stream.endText()
            }
            document.save(source)
        }

        val before = readGlyphs(source)
        val output = engine.replaceText(
            sourceUri = Uri.fromFile(source),
            pageIndex = 0,
            oldText = "original phrase",
            newText = "a much longer replacement phrase",
            outputFileName = "pdfcraft-style-preservation-test.pdf"
        )
        val after = readGlyphs(output)

        // The unchanged prefix must retain its exact font metrics and placement.
        val beforeStart = before.first { it.unicode == "S" }
        val afterStart = after.first { it.unicode == "S" }
        assertEquals(beforeStart.fontName, afterStart.fontName)
        assertEquals(beforeStart.fontSize, afterStart.fontSize, 0.01f)
        assertEquals(beforeStart.x, afterStart.x, 0.1f)
        assertEquals(beforeStart.y, afterStart.y, 0.1f)

        // The inserted text itself must inherit the selected paragraph's original style.
        val replacementStart = after.first { it.unicode == "a" && it.x > beforeStart.x }
        assertEquals(beforeStart.fontName, replacementStart.fontName)
        assertEquals(beforeStart.fontSize, replacementStart.fontSize, 0.01f)

        // The suffix may move horizontally when the paragraph reflows, but must retain
        // its original font metrics and baseline instead of being rendered as a new style.
        val beforeEnd = before.first { it.unicode == "E" }
        val afterEnd = after.first { it.unicode == "E" }
        assertEquals(beforeEnd.fontName, afterEnd.fontName)
        assertEquals(beforeEnd.fontSize, afterEnd.fontSize, 0.01f)
        assertEquals(beforeEnd.y, afterEnd.y, 0.1f)

        PDDocument.load(output).use { edited ->
            val text = PDFTextStripper().getText(edited)
            assertTrue(text.contains("Start a much longer replacement phrase End"))
            assertFalse(text.contains("Start original phrase End"))
        }
    }

    private data class GlyphMetrics(
        val unicode: String,
        val fontName: String,
        val fontSize: Float,
        val x: Float,
        val y: Float
    )

    private fun readGlyphs(file: File): List<GlyphMetrics> {
        val glyphs = mutableListOf<GlyphMetrics>()
        PDDocument.load(file).use { document ->
            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    glyphs += GlyphMetrics(
                        unicode = text.unicode,
                        fontName = text.font.name,
                        fontSize = text.fontSizeInPt,
                        x = text.xDirAdj,
                        y = text.yDirAdj
                    )
                    super.processTextPosition(text)
                }
            }
            stripper.getText(document)
        }
        return glyphs
    }

    private fun createPdf(text: String): File {
        val source = File(context.cacheDir, "pdfcraft-native-source-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 540f)
                stream.showText(text)
                stream.endText()
            }
            document.save(source)
        }
        return source
    }
}
