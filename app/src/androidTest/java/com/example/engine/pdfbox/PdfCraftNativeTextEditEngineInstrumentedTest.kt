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
import kotlinx.coroutines.runBlocking
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
