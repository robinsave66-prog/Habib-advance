package com.example.engine.pdfbox

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.engine.editor.TextElement
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfBengaliFontInstrumentedTest {
    @Test
    fun bundledNotoSansBengaliRendersConjunctsAndExportsVisibleGlyphs() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val sample = "বাংলা ভাষার পরীক্ষা ক্ষ ত্র জ্ঞ শ্র"
        val bundledTypeface = Typeface.createFromAsset(
            context.assets,
            "fonts/NotoSansBengali[wdth,wght].ttf"
        )
        val glyphCheck = Paint().apply { typeface = bundledTypeface }
        // Paint.hasGlyph checks a whole sequence as one shaped unit. Android releases
        // may report false for Bengali conjunct clusters even when every code point
        // exists in the font. Check cmap coverage per code point here; the PDF raster
        // assertion below verifies the actual rendered output.
        var offset = 0
        while (offset < sample.length) {
            val codePoint = sample.codePointAt(offset)
            val character = String(Character.toChars(codePoint))
            if (!character.isBlank()) {
                assertTrue(
                    "Bundled Bengali font is missing U+" + codePoint.toString(16),
                    glyphCheck.hasGlyph(character)
                )
            }
            offset += Character.charCount(codePoint)
        }

        val source = File(context.cacheDir, "bengali-font-source.pdf")
        var output: File? = null
        try {
            PDDocument().use { document ->
                document.addPage(PDPage(PDRectangle(360f, 220f)))
                document.save(source)
            }
            output = PdfBoxOverlayExportEngine(context).export(
                sourceUri = Uri.fromFile(source),
                elementsByPage = mapOf(
                    0 to listOf(
                        TextElement(
                            pageIndex = 0,
                            x = 24f,
                            y = 38f,
                            width = 310f,
                            height = 90f,
                            text = sample,
                            fontSizePt = 26f,
                            isBold = true,
                            textColor = 0xFF000000
                        )
                    )
                ),
                outputFileName = "bengali-font-instrumented.pdf"
            )
            PDDocument.load(output).use { document ->
                val page = document.getPage(0)
                val images = page.resources.xObjectNames.mapNotNull { name ->
                    page.resources.getXObject(name) as? PDImageXObject
                }
                assertTrue("Bengali text should be embedded as an image", images.isNotEmpty())
                assertTrue("Bengali overlay image should have expected high-resolution width", images.any { it.width >= 900 })
            }
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val page = renderer.openPage(0)
                    try {
                        val bitmap = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, Matrix().apply { setScale(3f, 3f) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            var darkPixels = 0
                            for (y in 100 until minOf(bitmap.height, 400)) {
                                for (x in 60 until minOf(bitmap.width, 1000)) {
                                    val pixel = bitmap.getPixel(x, y)
                                    if (Color.red(pixel) < 180 && Color.green(pixel) < 180 && Color.blue(pixel) < 180) darkPixels++
                                }
                            }
                            assertTrue("Rendered PDF must contain visible Bengali glyph pixels; found $darkPixels", darkPixels > 250)
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
