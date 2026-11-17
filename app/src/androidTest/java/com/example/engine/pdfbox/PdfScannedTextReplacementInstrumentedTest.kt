package com.example.engine.pdfbox

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfScannedTextReplacementInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun flattenedReplacementRemovesOldSearchableTextAndAddsNewTextAtSelectedArea() = runBlocking {
        val source = File(context.cacheDir, "scanned-replacement-source.pdf")
        var flattened: File? = null
        var output: File? = null
        try {
            PDDocument().use { document ->
                val page = PDPage(PDRectangle(420f, 600f))
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                    stream.newLineAtOffset(40f, 540f)
                    stream.showText("OLD SCANNED LABEL")
                    stream.endText()
                }
                document.save(source)
            }

            flattened = PdfSecureRedactionEngine(context).redact(
                sourceUri = Uri.fromFile(source),
                redactionsByPage = mapOf(0 to listOf(RectF(38f, 45f, 205f, 68f))),
                outputFileName = "instrumented-scanned-base.pdf",
                fillColor = Color.WHITE
            )

            output = PdfBoxOverlayExportEngine(context).export(
                sourceUri = Uri.fromFile(flattened),
                elementsByPage = mapOf(
                    0 to listOf(
                        TextElement(
                            pageIndex = 0,
                            x = 40f,
                            y = 48f,
                            width = 220f,
                            height = 30f,
                            text = "NEW SCANNED LABEL",
                            fontSizePt = 16f,
                            isBold = true,
                            textColor = 0xFF000000,
                            backgroundColor = 0xFFFFFFFF
                        )
                    )
                ),
                outputFileName = "instrumented-scanned-replacement.pdf"
            )

            PDDocument.load(output!!).use { result ->
                assertEquals(1, result.numberOfPages)
                val extracted = PDFTextStripper().getText(result)
                assertFalse("The flattened source must not retain the old searchable text",
                    extracted.contains("OLD SCANNED LABEL"))
                assertTrue("The replacement text must be written to the resulting PDF",
                    extracted.contains("NEW SCANNED LABEL"))
            }
        } finally {
            source.delete()
            flattened?.delete()
            output?.delete()
        }
    }
}
