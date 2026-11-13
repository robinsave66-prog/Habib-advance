package com.example.engine.pdfbox

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
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
class PdfBoxDocumentEngineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var source: File
    private lateinit var engine: PdfBoxDocumentEngine

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        engine = PdfBoxDocumentEngine(context)
        source = File(context.cacheDir, "page-operations-source.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(300f, 400f)))
            document.addPage(PDPage(PDRectangle(400f, 500f)))
            document.addPage(PDPage(PDRectangle(500f, 600f)))
            document.save(source)
        }
    }

    @Test
    fun deletePagesKeepsRemainingOriginalPageSizes() = runBlocking {
        val output = engine.deletePages(Uri.fromFile(source), setOf(1), "deleted-page-test.pdf")
        PDDocument.load(output).use { document ->
            assertEquals(2, document.numberOfPages)
            assertEquals(300f, document.getPage(0).mediaBox.width, 0.01f)
            assertEquals(500f, document.getPage(1).mediaBox.width, 0.01f)
        }
    }

    @Test
    fun reorderPagesPreservesPageObjectsAndOrder() = runBlocking {
        val output = engine.reorderPages(Uri.fromFile(source), listOf(2, 0, 1), "reordered-page-test.pdf")
        PDDocument.load(output).use { document ->
            assertEquals(3, document.numberOfPages)
            assertEquals(500f, document.getPage(0).mediaBox.width, 0.01f)
            assertEquals(300f, document.getPage(1).mediaBox.width, 0.01f)
            assertEquals(400f, document.getPage(2).mediaBox.width, 0.01f)
        }
    }

    @Test
    fun duplicatePageInsertsCopyImmediatelyAfterOriginal() = runBlocking {
        val output = engine.duplicatePage(Uri.fromFile(source), 1, "duplicated-page-test.pdf")
        PDDocument.load(output).use { document ->
            assertEquals(4, document.numberOfPages)
            assertEquals(300f, document.getPage(0).mediaBox.width, 0.01f)
            assertEquals(400f, document.getPage(1).mediaBox.width, 0.01f)
            assertEquals(400f, document.getPage(2).mediaBox.width, 0.01f)
            assertEquals(500f, document.getPage(3).mediaBox.width, 0.01f)
        }
    }

    @Test
    fun insertBlankPageUsesNeighborDimensions() = runBlocking {
        val output = engine.insertBlankPage(Uri.fromFile(source), 1, "inserted-page-test.pdf")
        PDDocument.load(output).use { document ->
            assertEquals(4, document.numberOfPages)
            assertEquals(300f, document.getPage(1).mediaBox.width, 0.01f)
            assertTrue(document.getPage(1).annotations.isEmpty())
        }
    }

    @Test
    fun extractPagesPreservesRequestedOrder() = runBlocking {
        val output = engine.extractPages(Uri.fromFile(source), listOf(2, 0), "extracted-page-test.pdf")
        PDDocument.load(output).use { document ->
            assertEquals(2, document.numberOfPages)
            assertEquals(500f, document.getPage(0).mediaBox.width, 0.01f)
            assertEquals(300f, document.getPage(1).mediaBox.width, 0.01f)
        }
    }

    @Test
    fun rotatePagesChangesPageRotationWithoutChangingPageCount() = runBlocking {
        val output = engine.rotatePages(Uri.fromFile(source), setOf(0, 2), 90, "rotated-page-test.pdf")
        PDDocument.load(output).use { document ->
            assertEquals(3, document.numberOfPages)
            assertEquals(90, document.getPage(0).rotation)
            assertEquals(0, document.getPage(1).rotation)
            assertEquals(90, document.getPage(2).rotation)
        }
    }

    @Test
    fun mergePdfsCombinesPagesFromBothSources() = runBlocking {
        val second = File(context.cacheDir, "second-merge-source.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(250f, 350f)))
            document.save(second)
        }
        val output = engine.mergePdfs(
            listOf(Uri.fromFile(source), Uri.fromFile(second)),
            "merged-pdf-test.pdf"
        )
        PDDocument.load(output).use { document ->
            assertEquals(4, document.numberOfPages)
            assertEquals(500f, document.getPage(2).mediaBox.width, 0.01f)
            assertEquals(250f, document.getPage(3).mediaBox.width, 0.01f)
        }
        assertTrue(second.delete())
    }
}
