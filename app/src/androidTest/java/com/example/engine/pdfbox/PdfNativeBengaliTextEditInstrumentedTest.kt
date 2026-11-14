package com.example.engine.pdfbox

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
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
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfNativeBengaliTextEditInstrumentedTest {

    @Test
    fun replacesBengaliInsideOriginalTextStreamAndRemainsSearchableWithoutPageImageOverlay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val oldText = "বাংলা ভাষা"
        val newText = "ভাষা"
        val source = File(context.cacheDir, "native-bengali-source.pdf")
        var output: File? = null
        try {
            PDDocument().use { document ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                val font = PDType0Font.load(
                    document,
                    context.assets.open("fonts/NotoSansBengali[wdth,wght].ttf"),
                    true
                )
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(font, 14f)
                    stream.newLineAtOffset(72f, 700f)
                    stream.showText(oldText)
                    stream.endText()
                }
                document.save(source)
            }

            PDDocument.load(source).use { original ->
                assertTrue(PDFTextStripper().getText(original).contains(oldText))
            }

            output = PdfNativeTextEditEngine(context).replaceText(
                Uri.fromFile(source),
                pageIndex = 0,
                oldText = oldText,
                newText = newText,
                outputFileName = "native-bengali-edited.pdf"
            )

            PDDocument.load(output).use { edited ->
                val extracted = PDFTextStripper().getText(edited)
                assertTrue("Replacement Bengali Unicode must remain searchable", extracted.contains(newText))
                assertFalse("Original Bengali text run must be replaced", extracted.contains(oldText))
                val page = edited.getPage(0)
                val type3Fonts = page.resources.fontNames.mapNotNull { name ->
                    page.resources.getFont(name)
                }.filterIsInstance<PDType3Font>()
                assertEquals("One embedded Type 3 Bengali text glyph should be used", 1, type3Fonts.size)
                assertTrue(
                    "The glyph mask must live inside the Type 3 font, not as a page-level image overlay",
                    page.resources.xObjectNames.none()
                )
            }

            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val page = renderer.openPage(0)
                    try {
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, Matrix().apply { setScale(2f, 2f) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            var darkPixels = 0
                            for (y in 0 until bitmap.height) {
                                for (x in 0 until bitmap.width) {
                                    val pixel = bitmap.getPixel(x, y)
                                    if (Color.red(pixel) < 180 && Color.green(pixel) < 180 && Color.blue(pixel) < 180) {
                                        darkPixels++
                                    }
                                }
                            }
                            assertTrue("Native Bengali Type 3 glyph must render visible pixels", darkPixels > 40)
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
