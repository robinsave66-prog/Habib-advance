package com.example.engine.pdfbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
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
class PdfSecureRedactionEngineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var engine: PdfSecureRedactionEngine

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        engine = PdfSecureRedactionEngine(context)
    }

    @Test
    fun redactionPaintsOpaqueBlackPixelsAndExpandsBeyondSelectedBounds() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            engine.applyRedactionRectangles(bitmap, listOf(RectF(20f, 20f, 40f, 40f)), 100f, 100f)
            assertEquals(Color.BLACK, bitmap.getPixel(18, 18))
            assertEquals(Color.BLACK, bitmap.getPixel(41, 41))
            assertEquals(Color.WHITE, bitmap.getPixel(17, 17))
            assertEquals(Color.WHITE, bitmap.getPixel(42, 42))
            assertEquals(Color.WHITE, bitmap.getPixel(10, 10))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun rasterizedReplacementPageContainsImageButNoSearchableOriginalText() {
        val output = File(context.cacheDir, "secure-redaction-flattened-test.pdf")
        val bitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            PDDocument().use { source ->
                val sourcePage = PDPage(PDRectangle(120f, 160f))
                source.addPage(sourcePage)
                PDPageContentStream(source, sourcePage).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(10f, 80f)
                    stream.showText("CONFIDENTIAL ORIGINAL TEXT")
                    stream.endText()
                }
                PDDocument().use { flattened ->
                    engine.appendRasterizedPage(flattened, bitmap, 120f, 160f)
                    flattened.save(output)
                }
            }
            PDDocument.load(output).use { flattened ->
                assertEquals(1, flattened.numberOfPages)
                assertTrue("Original text must not remain searchable", PDFTextStripper().getText(flattened).isBlank())
                val resources = flattened.getPage(0).resources
                assertTrue("Replacement page must contain a raster image",
                    resources.xObjectNames.any { resources.getXObject(it) is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject })
            }
        } finally {
            bitmap.recycle()
            output.delete()
        }
    }
}
