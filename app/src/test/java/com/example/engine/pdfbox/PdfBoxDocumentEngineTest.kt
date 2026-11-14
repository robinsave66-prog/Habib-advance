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
    fun passwordProtectionAndUnlockPreservePdfPages() = runBlocking {
        val protected = engine.protectPdf(
            sourceUri = Uri.fromFile(source),
            userPassword = "user-pass-123",
            ownerPassword = "owner-pass-456",
            outputFileName = "protected-pdf-test.pdf"
        )
        PDDocument.load(protected, "user-pass-123").use { document ->
            assertEquals(3, document.numberOfPages)
        }

        val unlocked = engine.removePassword(
            sourceUri = Uri.fromFile(protected),
            password = "user-pass-123",
            outputFileName = "unlocked-pdf-test.pdf"
        )
        PDDocument.load(unlocked).use { document ->
            assertEquals(3, document.numberOfPages)
            assertEquals(300f, document.getPage(0).mediaBox.width, 0.01f)
        }
    }

    @Test
    fun passwordProtectionRejectsWeakOrIdenticalPasswords() = runBlocking {
        var weakRejected = false
        try {
            engine.protectPdf(Uri.fromFile(source), "123", "owner-password", "weak-password-test.pdf")
        } catch (_: IllegalArgumentException) {
            weakRejected = true
        }
        assertTrue(weakRejected)

        var identicalRejected = false
        try {
            engine.protectPdf(Uri.fromFile(source), "same-password", "same-password", "same-password-test.pdf")
        } catch (_: IllegalArgumentException) {
            identicalRejected = true
        }
        assertTrue(identicalRejected)
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

    @Test
    fun reorderPagesRejectsIncompleteDuplicateAndOutOfRangeOrders() = runBlocking {
        val invalidOrders = listOf(
            listOf(0, 1),       // incomplete permutation
            listOf(0, 0, 2),    // duplicate page
            listOf(0, 1, 3)     // out of range
        )
        invalidOrders.forEachIndexed { index, order ->
            var rejected = false
            try {
                engine.reorderPages(
                    Uri.fromFile(source), order, "invalid-page-order-$index.pdf"
                )
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Expected invalid page order to be rejected: $order", rejected)
        }
        PDDocument.load(source).use { original ->
            assertEquals(3, original.numberOfPages)
            assertEquals(300f, original.getPage(0).mediaBox.width, 0.01f)
            assertEquals(400f, original.getPage(1).mediaBox.width, 0.01f)
            assertEquals(500f, original.getPage(2).mediaBox.width, 0.01f)
        }
    }


    @Test
    fun passwordProtectionEnforcesUserPermissionsAndAllowsOwnerAccess() = runBlocking {
        val protected = engine.protectPdf(
            sourceUri = Uri.fromFile(source),
            userPassword = "restricted-user-123",
            ownerPassword = "document-owner-456",
            outputFileName = "permission-protected-test.pdf"
        )

        PDDocument.load(protected, "restricted-user-123").use { userDocument ->
            val permission = userDocument.currentAccessPermission
            assertTrue("Printing should remain allowed", permission.canPrint())
            assertTrue("Content modification should be disabled", !permission.canModify())
            assertTrue("Text extraction should be disabled", !permission.canExtractContent())
            assertTrue("Annotation changes should be disabled", !permission.canModifyAnnotations())
            assertTrue("Form filling should be disabled", !permission.canFillInForm())
            assertTrue("Page assembly should be disabled", !permission.canAssembleDocument())
        }

        PDDocument.load(protected, "document-owner-456").use { ownerDocument ->
            assertTrue("Owner password should grant owner permissions",
                ownerDocument.currentAccessPermission.isOwnerPermission)
            assertTrue(ownerDocument.currentAccessPermission.canModify())
        }
    }

    @Test
    fun passwordUnlockRejectsAnIncorrectPasswordAndKeepsSourceIntact() = runBlocking {
        val protected = engine.protectPdf(
            sourceUri = Uri.fromFile(source),
            userPassword = "correct-user-123",
            ownerPassword = "correct-owner-456",
            outputFileName = "wrong-password-source.pdf"
        )

        var rejected = false
        try {
            engine.removePassword(
                sourceUri = Uri.fromFile(protected),
                password = "incorrect-password",
                outputFileName = "must-not-be-created.pdf"
            )
        } catch (_: Exception) {
            rejected = true
        }

        assertTrue("Incorrect password must be rejected", rejected)
        assertTrue("Protected source must remain available", protected.exists())
        PDDocument.load(protected, "correct-user-123").use { document ->
            assertEquals(3, document.numberOfPages)
        }
    }

}
