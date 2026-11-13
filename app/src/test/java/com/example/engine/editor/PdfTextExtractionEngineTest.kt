package com.example.engine.editor

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfTextExtractionEngineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var source: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        source = File(context.cacheDir, "native-text-extraction-test.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                stream.newLineAtOffset(40f, 540f)
                stream.showText("Native searchable words")
                stream.endText()
            }
            document.save(source)
        }
    }

    @Test
    fun extractsNativePdfTextAndRealGlyphBoundsWithoutBitmap() {\n        runBlocking {
        val words = PdfTextExtractionEngine.extractWordsFromPage(
            context = context,
            pageBitmap = null,
            pageIndex = 0,
            pageWidthPt = 420,
            pageHeightPt = 600,
            sourceUri = Uri.fromFile(source)
        )

        assertFalse("Native PDF text should be extracted without OCR bitmap.", words.isEmpty())
        assertTrue(words.any { it.text.contains("Native") })
        assertTrue(words.all { it.pageIndex == 0 })
        assertTrue(words.all {
            it.bounds.left >= 0f && it.bounds.top >= 0f &&
                it.bounds.right <= 420f && it.bounds.bottom <= 600f
        })
        assertTrue(words.any { it.isBold })
    }

    @Test
    fun returnsNoFabricatedWordsForBlankPdfPage() {\n        runBlocking {
        val blank = File(context.cacheDir, "blank-text-extraction-test.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(420f, 600f)))
            document.save(blank)
        }

        val words = PdfTextExtractionEngine.extractWordsFromPage(
            context = context,
            pageBitmap = null,
            pageIndex = 0,
            pageWidthPt = 420,
            pageHeightPt = 600,
            sourceUri = Uri.fromFile(blank)
        )

        assertTrue("A blank PDF page must return an empty result, not demo text.", words.isEmpty())
        blank.delete()
    }

    @Test
    fun invalidPageIndexReturnsNoText() {\n        runBlocking {
        val words = PdfTextExtractionEngine.extractWordsFromPage(
            context = context,
            pageBitmap = null,
            pageIndex = 4,
            pageWidthPt = 420,
            pageHeightPt = 600,
            sourceUri = Uri.fromFile(source)
        )
        assertEquals(0, words.size)
    }
}
