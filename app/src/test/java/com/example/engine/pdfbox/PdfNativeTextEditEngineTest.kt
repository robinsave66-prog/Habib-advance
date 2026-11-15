package com.example.engine.pdfbox

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.runBlocking
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
class PdfNativeTextEditEngineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var source: File
    private lateinit var engine: PdfNativeTextEditEngine

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        engine = PdfNativeTextEditEngine(context)
        source = File(context.cacheDir, "native-text-edit-source.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 540f)
                stream.showText("Start ABC End")
                stream.endText()
            }
            document.save(source)
        }
    }

    @Test
    fun replacesNativeTextOperandAndPreservesSurroundingText() = runBlocking {
        val output = engine.replaceText(
            Uri.fromFile(source), 0, "ABC", "BCA", "native-text-edit-success.pdf"
        )
        PDDocument.load(output).use { edited ->
            val text = PDFTextStripper().getText(edited)
            assertTrue(text.contains("Start BCA End"))
            assertFalse(text.contains("Start ABC End"))
        }
        PDDocument.load(source).use { original ->
            assertTrue(PDFTextStripper().getText(original).contains("Start ABC End"))
        }
        assertTrue(output.length() > 0L)
    }

    @Test
    fun accountsForCharacterSpacingWhenCheckingReplacementAdvance() = runBlocking {
        val spacedSource = File(context.cacheDir, "native-text-edit-character-spacing.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.setCharacterSpacing(-8.664f)
                stream.newLineAtOffset(40f, 540f)
                stream.showText("ABC")
                stream.endText()
            }
            document.save(spacedSource)
        }
        val output = engine.replaceText(Uri.fromFile(spacedSource), 0, "ABC", "AB", "native-character-spacing.pdf")
        PDDocument.load(output).use { assertTrue(PDFTextStripper().getText(it).contains("AB")) }
    }

    @Test
    fun accountsForWordSpacingWhenCheckingReplacementAdvance() = runBlocking {
        val spacedSource = File(context.cacheDir, "native-text-edit-word-spacing.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.setWordSpacing(-3.336f)
                stream.newLineAtOffset(40f, 540f)
                stream.showText("A B")
                stream.endText()
            }
            document.save(spacedSource)
        }
        val output = engine.replaceText(Uri.fromFile(spacedSource), 0, "A B", "AB", "native-word-spacing.pdf")
        PDDocument.load(output).use { assertTrue(PDFTextStripper().getText(it).contains("AB")) }
    }



    @Test
    fun replacesPhraseSplitAcrossAdjacentTjStrings() = runBlocking {
        val splitSource = File(context.cacheDir, "native-text-edit-split-tj.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 540f)
                stream.showTextWithPositioning(arrayOf<Any>("Start A", "BC End"))
                stream.endText()
            }
            document.save(splitSource)
        }

        val output = engine.replaceText(
            Uri.fromFile(splitSource), 0, "ABC", "BCA", "native-split-tj.pdf"
        )
        PDDocument.load(output).use { edited ->
            val text = PDFTextStripper().getText(edited)
            assertTrue(text.contains("Start BCA End"))
            assertFalse(text.contains("Start ABC End"))
        }
        PDDocument.load(splitSource).use { original ->
            assertTrue(PDFTextStripper().getText(original).contains("Start ABC End"))
        }
    }

    @Test
    fun replacesLatinTextWithAccentedLatinCharacterWhenFontHasMatchingAdvance() = runBlocking {
        val output = engine.replaceText(
            Uri.fromFile(source), 0, "A", "À", "native-accented-latin.pdf"
        )
        PDDocument.load(output).use { edited ->
            assertTrue(PDFTextStripper().getText(edited).contains("Start ÀBC End"))
        }
        PDDocument.load(source).use { original ->
            assertTrue(PDFTextStripper().getText(original).contains("Start ABC End"))
        }
    }

    @Test
    fun rejectsBengaliReplacementBeforeEditingAndPreservesSource() = runBlocking {
        val outputName = "native-bengali-rejected.pdf"
        val outputFile = File(context.filesDir, "documents/$outputName")
        outputFile.delete()
        var rejected = false
        try {
            engine.replaceText(
                Uri.fromFile(source), 0, "ABC", "বাংলা", outputName
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(outputFile.exists())
        PDDocument.load(source).use { original ->
            assertTrue(PDFTextStripper().getText(original).contains("Start ABC End"))
        }
    }

    @Test
    fun preservesFollowingGlyphPositionWhenReplacementHasDifferentWidth() = runBlocking {
        val originalEndX = findGlyphX(source, "E")
        val output = engine.replaceText(
            Uri.fromFile(source), 0, "ABC", "WWW", "native-text-edit-variable-width.pdf"
        )
        PDDocument.load(output).use { edited ->
            val text = PDFTextStripper().getText(edited)
            assertTrue(text.contains("Start WWW End"))
            assertFalse(text.contains("Start ABC End"))
        }
        val editedEndX = findGlyphX(output, "E")
        assertTrue(
            "The TJ adjustment must keep the following text at its original x coordinate (old=$originalEndX, new=$editedEndX)",
            kotlin.math.abs(originalEndX - editedEndX) < 0.1f
        )
        PDDocument.load(source).use { original ->
            assertTrue(PDFTextStripper().getText(original).contains("Start ABC End"))
        }
    }

    @Test
    fun preservesFollowingGlyphPositionForDifferentWidthTextSplitAcrossAdjacentTjStrings() = runBlocking {
        val splitSource = File(context.cacheDir, "native-text-edit-variable-split-tj.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(420f, 600f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 540f)
                stream.showTextWithPositioning(arrayOf<Any>("Start A", "BC End"))
                stream.endText()
            }
            document.save(splitSource)
        }
        val originalEndX = findGlyphX(splitSource, "E")
        val output = engine.replaceText(
            Uri.fromFile(splitSource), 0, "ABC", "WWW", "native-variable-split-tj.pdf"
        )
        PDDocument.load(output).use { edited ->
            val text = PDFTextStripper().getText(edited)
            assertTrue(text.contains("Start WWW End"))
            assertFalse(text.contains("Start ABC End"))
        }
        val editedEndX = findGlyphX(output, "E")
        assertTrue(
            "The TJ array rewrite must preserve the suffix position (old=$originalEndX, new=$editedEndX)",
            kotlin.math.abs(originalEndX - editedEndX) < 0.1f
        )
    }

    private fun findGlyphX(file: File, glyph: String): Float {
        var x = Float.NaN
        PDDocument.load(file).use { document ->
            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    if (text.unicode == glyph && !x.isFinite()) x = text.xDirAdj
                    super.processTextPosition(text)
                }
            }
            stripper.getText(document)
        }
        check(x.isFinite()) { "Could not locate glyph '$glyph' in the test PDF." }
        return x
    }
}
