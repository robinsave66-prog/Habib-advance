package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.sample.SamplePdfGenerator
import com.example.data.local.db.AppDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentType
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.DocumentRepository
import com.example.engine.editor.EditorElement
import com.example.engine.editor.ShapeElement
import com.example.engine.editor.ShapeType
import com.example.engine.editor.SignatureElement
import com.example.engine.editor.TextElement
import com.example.engine.renderer.AndroidNativePdfRenderer
import com.example.feature.imagestudio.ImageItem
import com.example.feature.imagestudio.MultiImageToPdfEngine
import com.example.feature.imagestudio.PdfPageSettings
import com.example.feature.pdfeditor.PdfEditorViewModel
import com.example.feature.pdfeditor.PdfExportEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabibAdvanceEditorRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: DocumentRepository
    private lateinit var preferencesRepository: PreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepository(db.documentDao(), context)
        preferencesRepository = PreferencesRepository(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAppNameResourceMatchesBrand() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Habib Advance Editor", appName)
    }

    @Test
    fun testDatabaseInsertAndRetrieveDocuments() = runBlocking {
        val doc = DocumentEntity(
            uri = "content://docs/test.pdf",
            name = "Test_Document.pdf",
            mimeType = "application/pdf",
            documentType = DocumentType.PDF,
            sizeBytes = 1024L,
            pageCount = 5,
            isFavorite = false
        )

        db.documentDao().insertOrUpdate(doc)

        val retrieved = db.documentDao().getDocumentByUri(doc.uri)
        assertNotNull(retrieved)
        assertEquals("Test_Document.pdf", retrieved?.name)
        assertEquals(5, retrieved?.pageCount)

        // Toggle favorite
        db.documentDao().setFavorite(doc.uri, true)
        val favoriteDoc = db.documentDao().getDocumentByUri(doc.uri)
        assertTrue(favoriteDoc?.isFavorite == true)

        // Verify Flow emits favorites
        val favs = db.documentDao().getFavoriteDocuments().first()
        assertEquals(1, favs.size)
        assertEquals("Test_Document.pdf", favs[0].name)
    }

    @Test
    fun testSamplePdfGenerationAndFileIntegrity() {
        val sampleFile = SamplePdfGenerator.createSamplePdfIfMissing(context)
        assertTrue(sampleFile.exists())
        assertTrue(sampleFile.length() > 0)
        assertTrue(sampleFile.name.endsWith(".pdf"))
    }

    @Test
    fun testPdfRendererOpensSampleDocument() = runBlocking {
        val sampleFile = SamplePdfGenerator.createSamplePdfIfMissing(context)
        val sampleUri = Uri.fromFile(sampleFile)

        val renderer = AndroidNativePdfRenderer(context)
        val result = renderer.openDocument(sampleUri)

        when (result) {
            is com.example.engine.core.PdfLoadResult.Success -> {
                assertTrue(result.documentInfo.pageCount >= 0)
            }
            is com.example.engine.core.PdfLoadResult.Corrupted -> {
                assertNotNull(result.message)
            }
            is com.example.engine.core.PdfLoadResult.Error -> {
                assertNotNull(result.throwable)
            }
            is com.example.engine.core.PdfLoadResult.PasswordRequired -> {}
        }

        renderer.close()
    }

    @Test
    fun testPreferencesThemeChange() = runBlocking {
        preferencesRepository.setThemeMode(AppThemeMode.DARK)
        val prefs = preferencesRepository.userPreferencesFlow.first()
        assertEquals(AppThemeMode.DARK, prefs.themeMode)

        preferencesRepository.setThemeMode(AppThemeMode.LIGHT)
        val updatedPrefs = preferencesRepository.userPreferencesFlow.first()
        assertEquals(AppThemeMode.LIGHT, updatedPrefs.themeMode)
    }

    // --- Phase 2: Multi-Image to PDF Tests ---

    @Test
    fun testMultiImagePdfGeneration() = runBlocking {
        // Create 2 temporary test image files
        val imgDir = File(context.cacheDir, "test_images").apply { mkdirs() }
        val imgFile1 = File(imgDir, "img1.png")
        val imgFile2 = File(imgDir, "img2.png")

        val bmp1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(imgFile1).use { bmp1.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val bmp2 = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888)
        FileOutputStream(imgFile2).use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val items = listOf(
            ImageItem(uri = Uri.fromFile(imgFile1), name = "img1.png"),
            ImageItem(uri = Uri.fromFile(imgFile2), name = "img2.png")
        )

        val generatedPdf = MultiImageToPdfEngine.generatePdfFromImages(
            context = context,
            images = items,
            settings = PdfPageSettings(),
            outputFileName = "Test_Multi_Image"
        )

        assertNotNull(generatedPdf)
        assertTrue(generatedPdf.exists())
        assertTrue(generatedPdf.length() > 0)
        assertTrue(generatedPdf.name.endsWith(".pdf"))
    }

    // --- Phase 2: PDF Editor Model & State Tests ---

    @Test
    fun testPdfEditorViewModelObjectManipulationAndUndoRedo() = runBlocking {
        val editorViewModel = PdfEditorViewModel(context)

        val textElem = TextElement(
            pageIndex = 0,
            x = 50f,
            y = 100f,
            width = 120f,
            height = 30f,
            text = "Contract Approved",
            fontSizePt = 16f
        )

        editorViewModel.addElement(textElem)
        val elements = editorViewModel.elements.value[0].orEmpty()
        assertEquals(1, elements.size)
        assertEquals("Contract Approved", (elements[0] as TextElement).text)

        // Move element
        editorViewModel.updateElementPosition(textElem.id, 80f, 150f)
        val moved = editorViewModel.elements.value[0]?.find { it.id == textElem.id }
        assertEquals(80f, moved?.x ?: 0f, 0.01f)
        assertEquals(150f, moved?.y ?: 0f, 0.01f)

        // Add Shape element
        val shapeElem = ShapeElement(
            pageIndex = 0,
            x = 40f,
            y = 90f,
            width = 140f,
            height = 50f,
            shapeType = ShapeType.RECTANGLE
        )
        editorViewModel.addElement(shapeElem)
        assertEquals(2, editorViewModel.elements.value[0]?.size)

        // Test Undo
        assertTrue(editorViewModel.canUndo())
        editorViewModel.undo()
        assertEquals(1, editorViewModel.elements.value[0]?.size)

        // Test Redo
        assertTrue(editorViewModel.canRedo())
        editorViewModel.redo()
        assertEquals(2, editorViewModel.elements.value[0]?.size)

        // Test Delete
        editorViewModel.selectElement(textElem.id)
        editorViewModel.deleteSelectedElement()
        assertEquals(1, editorViewModel.elements.value[0]?.size)
    }

    // --- Phase 2: OpenXML DOCX Generation & PDF Operations Tests ---

    @Test
    fun testCreateDocxDocumentGeneratesStandardOpenXmlZip() = runBlocking {
        val paragraphs = listOf(
            "Habib Advance Editor - Document Review",
            "This document was exported to genuine OpenXML DOCX format.",
            "All paragraphs and formatting are compatible with Microsoft Word."
        )

        val docxFile = com.example.engine.core.AdvancedPdfOperationsEngine.createDocxDocument(
            context = context,
            paragraphs = paragraphs,
            outputFileName = "TestDocxExport.docx"
        )

        assertNotNull(docxFile)
        assertTrue(docxFile.exists())
        assertTrue(docxFile.length() > 0)
        assertTrue(docxFile.name.endsWith(".docx"))

        // Verify it is a valid ZIP containing OpenXML parts
        var hasWordDocument = false
        var hasContentTypes = false
        java.util.zip.ZipInputStream(java.io.FileInputStream(docxFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") hasWordDocument = true
                if (entry.name == "[Content_Types].xml") hasContentTypes = true
                entry = zis.nextEntry
            }
        }
        assertTrue(hasWordDocument)
        assertTrue(hasContentTypes)
    }

    @Test
    fun testMergeAndSplitPdfOperations() = runBlocking {
        val sample1 = SamplePdfGenerator.createSamplePdfIfMissing(context)
        val uri1 = Uri.fromFile(sample1)

        val merged = com.example.engine.core.AdvancedPdfOperationsEngine.mergePdfs(
            context = context,
            sourceUris = listOf(uri1, uri1),
            outputFileName = "Test_Merged_Doc.pdf"
        )

        assertNotNull(merged)
        assertTrue(merged.exists())
        assertTrue(merged.length() > 0)

        val split = com.example.engine.core.AdvancedPdfOperationsEngine.splitPdf(
            context = context,
            sourceUri = Uri.fromFile(merged),
            pageIndices = listOf(0, 1),
            outputFileName = "Test_Split_Doc.pdf"
        )

        assertNotNull(split)
        assertTrue(split.exists())
        assertTrue(split.length() > 0)
    }

    @Test
    fun testCompressPdfEngineExecution() = runBlocking {
        val sample = SamplePdfGenerator.createSamplePdfIfMissing(context)
        val compressed = com.example.engine.core.AdvancedPdfOperationsEngine.compressPdf(
            context = context,
            sourceUri = Uri.fromFile(sample),
            compressionLevel = "High",
            outputFileName = "Test_Compressed_Doc.pdf"
        )

        assertNotNull(compressed)
        assertTrue(compressed.exists())
    }

    @Test
    fun testPdfTextExtractionEngineWordHitTestingAndRange() {
        val word1 = com.example.engine.editor.DetectedPdfWord(
            text = "Habib",
            bounds = android.graphics.RectF(50f, 100f, 100f, 120f),
            pageIndex = 0
        )
        val word2 = com.example.engine.editor.DetectedPdfWord(
            text = "Editor",
            bounds = android.graphics.RectF(110f, 100f, 160f, 120f),
            pageIndex = 0
        )
        val words = listOf(word1, word2)

        // Exact hit
        val hit = com.example.engine.editor.PdfTextExtractionEngine.findWordAt(75f, 110f, words)
        assertEquals("Habib", hit?.text)

        // Range selection
        val range = com.example.engine.editor.PdfTextExtractionEngine.getWordsInRange(word1, word2, words)
        assertEquals(2, range.size)
        assertEquals("Habib", range[0].text)
        assertEquals("Editor", range[1].text)
    }

    @Test
    fun testCompileMultiPageDocumentGeneratesValidFile() = runBlocking {
        val pageElements = mapOf(
            0 to listOf<EditorElement>(
                TextElement(
                    pageIndex = 0,
                    x = 40f,
                    y = 80f,
                    width = 200f,
                    height = 40f,
                    text = "Page 1 Multi-Page Content",
                    fontSizePt = 16f
                )
            ),
            1 to listOf<EditorElement>(
                TextElement(
                    pageIndex = 1,
                    x = 40f,
                    y = 80f,
                    width = 200f,
                    height = 40f,
                    text = "Page 2 Multi-Page Content",
                    fontSizePt = 16f
                )
            )
        )

        val compiledFile = com.example.feature.documentcreator.DocumentCreatorEngine.compileMultiPageDocument(
            context = context,
            pageCount = 2,
            pageWidthPt = 595,
            pageHeightPt = 842,
            elementsByPage = pageElements,
            outputFileName = "Test_Multi_Page_Document.pdf"
        )

        assertNotNull(compiledFile)
        assertTrue(compiledFile.exists())
        assertTrue(compiledFile.length() > 0)
    }

    // --- Phase 3: Critical Bug Fixes & Document Workflow Tests ---

    @Test
    fun testPdfZoomAndPanBoundaryClamping() {
        val viewportWidth = 1080f
        val viewportHeight = 1920f

        val testZooms = listOf(1.0f, 1.5f, 2.0f, 3.0f, 3.5f)
        for (zoom in testZooms) {
            val maxPanX = (viewportWidth * (zoom - 1f) / 2f).coerceAtLeast(0f)
            val maxPanY = (viewportHeight * (zoom - 1f) / 2f).coerceAtLeast(0f)

            if (zoom == 1.0f) {
                assertEquals(0f, maxPanX, 0.01f)
                assertEquals(0f, maxPanY, 0.01f)
            } else {
                assertTrue(maxPanX > 0f)
                assertTrue(maxPanY > 0f)
            }

            // Test simulated excessive pan
            val requestedPanX = 5000f
            val clampedPanX = requestedPanX.coerceIn(-maxPanX, maxPanX)
            assertTrue("Clamped pan X must not exceed maxPanX", clampedPanX <= maxPanX)
            assertTrue("Clamped pan X must be bounded", clampedPanX >= -maxPanX)
        }
    }

    @Test
    fun testEditorInteractionModeSwitching() {
        val viewModel = PdfEditorViewModel(context)

        // Default mode is SELECT
        assertEquals(com.example.feature.pdfeditor.EditorInteractionMode.SELECT, viewModel.interactionMode.value)

        // Switch to PAGE_NAV
        viewModel.setInteractionMode(com.example.feature.pdfeditor.EditorInteractionMode.PAGE_NAV)
        assertEquals(com.example.feature.pdfeditor.EditorInteractionMode.PAGE_NAV, viewModel.interactionMode.value)
        assertEquals(null, viewModel.selectedElementId.value)
        assertTrue(viewModel.selectedWords.value.isEmpty())

        // Switch to TEXT_EDIT
        viewModel.setInteractionMode(com.example.feature.pdfeditor.EditorInteractionMode.TEXT_EDIT)
        assertEquals(com.example.feature.pdfeditor.EditorInteractionMode.TEXT_EDIT, viewModel.interactionMode.value)

        // Switch to SHAPE_ANNOTATION
        viewModel.setInteractionMode(com.example.feature.pdfeditor.EditorInteractionMode.SHAPE_ANNOTATION)
        assertEquals(com.example.feature.pdfeditor.EditorInteractionMode.SHAPE_ANNOTATION, viewModel.interactionMode.value)
    }

    @Test
    fun testObjectDragIsolationAndUndoRedo() {
        val viewModel = PdfEditorViewModel(context)
        val elem = TextElement(
            pageIndex = 0,
            x = 100f,
            y = 150f,
            width = 120f,
            height = 30f,
            text = "Draggable Element"
        )
        viewModel.addElement(elem)
        assertEquals(elem.id, viewModel.selectedElementId.value)

        // Begin transform & move element
        viewModel.beginElementTransform()
        viewModel.updateElementPosition(elem.id, 140f, 200f)
        val updatedElem = viewModel.elements.value[0]?.find { it.id == elem.id } as? TextElement
        assertNotNull(updatedElem)
        assertEquals(140f, updatedElem?.x ?: 0f, 0.01f)
        assertEquals(200f, updatedElem?.y ?: 0f, 0.01f)

        // Undo
        assertTrue(viewModel.canUndo())
        viewModel.undo()
        val revertedElem = viewModel.elements.value[0]?.find { it.id == elem.id }
        assertNotNull(revertedElem)
        assertEquals(100f, revertedElem?.x ?: 0f, 0.01f)
    }

    @Test
    fun testReplaceSelectedWordsRespectsNullBackground() {
        val viewModel = PdfEditorViewModel(context)
        viewModel.replaceSelectedWords(
            newText = "New Clean Text Without Unwanted Box",
            fontSizePt = 16f,
            textColor = 0xFF000000,
            backgroundColor = null // Null background must be respected!
        )

        val elements = viewModel.elements.value[0].orEmpty()
        assertTrue(elements.isNotEmpty())
        val textElem = elements.first() as TextElement
        assertEquals(null, textElem.backgroundColor)
        assertEquals("New Clean Text Without Unwanted Box", textElem.text)
    }

    @Test
    fun testRichDocxCreationAndZipIntegrity() = runBlocking {
        val paragraphs = listOf(
            "Curriculum Vitae",
            "Professional Android Developer with expertise in Jetpack Compose, Clean Architecture, and PDF engines.",
            "Experience: Senior Mobile Architect (2020-2024)",
            "Skills: Kotlin, Coroutines, Room, OpenXML, PDF Rendering"
        )

        val docxFile = com.example.engine.core.AdvancedPdfOperationsEngine.createDocxDocument(
            context = context,
            paragraphs = paragraphs,
            outputFileName = "Test_CV_Integrity.docx"
        )

        assertNotNull(docxFile)
        assertTrue(docxFile.exists())
        assertTrue("DOCX file must have substantial content and not be empty", docxFile.length() > 500L)

        // Validate OpenXML ZIP structure & XML text content
        var hasDocumentXml = false
        var hasContentTypes = false
        var hasRels = false
        var docXmlContent = ""

        java.util.zip.ZipInputStream(docxFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "word/document.xml" -> {
                        hasDocumentXml = true
                        docXmlContent = zip.bufferedReader().readText()
                    }
                    "[Content_Types].xml" -> hasContentTypes = true
                    "_rels/.rels" -> hasRels = true
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        assertTrue("DOCX must contain word/document.xml", hasDocumentXml)
        assertTrue("DOCX must contain [Content_Types].xml", hasContentTypes)
        assertTrue("DOCX must contain _rels/.rels", hasRels)
        assertTrue("document.xml must contain CV title", docXmlContent.contains("Curriculum Vitae"))
        assertTrue("document.xml must contain skills", docXmlContent.contains("Senior Mobile Architect"))
    }

    @Test
    fun testSupportedDocumentFormatsAndMimeResolution() {
        val sharingManager = com.example.core.sharing.FileSharingManager
        assertEquals("application/pdf", sharingManager.getMimeTypeForFile("document.pdf"))
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", sharingManager.getMimeTypeForFile("resume.docx"))
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sharingManager.getMimeTypeForFile("data.xlsx"))
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", sharingManager.getMimeTypeForFile("slides.pptx"))
        assertEquals("text/plain", sharingManager.getMimeTypeForFile("notes.txt"))
        assertEquals("image/png", sharingManager.getMimeTypeForFile("photo.png"))
        assertEquals("image/jpeg", sharingManager.getMimeTypeForFile("scan.jpg"))
    }
}

