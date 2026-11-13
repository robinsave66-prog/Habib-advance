package com.example.engine.pdfbox

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfSecureRedactionInstrumentedTest {
    @Test
    fun redactionCreatesImageOnlyPdfAndLeavesSourceUntouched() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val source = File(context.cacheDir, "redaction-instrumented-source.pdf")
        var output: File? = null
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(300f, 400f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 18f)
                stream.newLineAtOffset(100f, 205f)
                stream.showText("CONFIDENTIAL 12345")
                stream.endText()
            }
            document.save(source)
        }

        try {
            PDDocument.load(source).use { original ->
                assertTrue(PDFTextStripper().getText(original).contains("CONFIDENTIAL 12345"))
            }
            output = PdfSecureRedactionEngine(context).redact(
                Uri.fromFile(source),
                mapOf(0 to listOf(RectF(95f, 175f, 240f, 215f))),
                "instrumented-redacted"
            )
            val redactedFile = requireNotNull(output)
            PDDocument.load(redactedFile).use { redacted ->
                assertEquals(1, redacted.numberOfPages)
                assertTrue(PDFTextStripper().getText(redacted).isBlank())
                val resources = redacted.getPage(0).resources
                assertTrue(resources.xObjectNames.any { name -> resources.getXObject(name) is PDImageXObject })
            }
            PDDocument.load(source).use { original ->
                assertTrue(PDFTextStripper().getText(original).contains("CONFIDENTIAL 12345"))
            }

            ParcelFileDescriptor.open(redactedFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val page = renderer.openPage(0)
                    try {
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val x = (165f / 300f * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                            val y = (195f / 400f * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                            assertEquals(Color.BLACK, bitmap.getPixel(x, y))
                        } finally {
                            bitmap.recycle()
                        }
                    } finally {
                        page.close()
                    }
                }
            }
        } finally {
            source.delete()
            output?.delete()
        }
    }
}
