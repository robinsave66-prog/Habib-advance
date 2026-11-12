package com.example.feature.pdfeditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.engine.core.IPdfRenderer
import com.example.engine.core.PdfDocumentInfo
import com.example.engine.core.PdfLoadResult
import com.example.engine.editor.DetectedPdfWord
import com.example.engine.editor.EditorElement
import com.example.engine.editor.ImageElement
import com.example.engine.editor.PdfTextExtractionEngine
import com.example.engine.editor.ShapeElement
import com.example.engine.editor.ShapeType
import com.example.engine.editor.SignatureElement
import com.example.engine.editor.TextElement
import com.example.engine.renderer.AndroidNativePdfRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Stack
import java.util.UUID

enum class EditorInteractionMode {
    SELECT,
    TEXT_EDIT,
    IMAGE_EDIT,
    SHAPE_ANNOTATION,
    PAGE_NAV
}

enum class ActiveEditorTool {
    SELECT,
    TEXT,
    IMAGE,
    SHAPE,
    PEN,
    HIGHLIGHTER,
    SIGNATURE
}

sealed interface EditorUiStatus {
    data object Idle : EditorUiStatus
    data class Loading(val message: String) : EditorUiStatus
    data class Loaded(val documentInfo: PdfDocumentInfo) : EditorUiStatus
    data class Exporting(val progress: String) : EditorUiStatus
    data class ExportSuccess(val exportedFile: File) : EditorUiStatus
    data class Error(val message: String) : EditorUiStatus
}

class PdfEditorViewModel(
    private val context: Context
) : ViewModel() {

    private val renderer: IPdfRenderer = AndroidNativePdfRenderer(context)

    private val _status = MutableStateFlow<EditorUiStatus>(EditorUiStatus.Idle)
    val status: StateFlow<EditorUiStatus> = _status.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageDimensions = MutableStateFlow<Pair<Int, Int>>(Pair(595, 842))
    val pageDimensions: StateFlow<Pair<Int, Int>> = _pageDimensions.asStateFlow()

    private val _elements = MutableStateFlow<Map<Int, List<EditorElement>>>(emptyMap())
    val elements: StateFlow<Map<Int, List<EditorElement>>> = _elements.asStateFlow()

    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId.asStateFlow()

    private val _interactionMode = MutableStateFlow(EditorInteractionMode.SELECT)
    val interactionMode: StateFlow<EditorInteractionMode> = _interactionMode.asStateFlow()

    private val _activeTool = MutableStateFlow(ActiveEditorTool.SELECT)
    val activeTool: StateFlow<ActiveEditorTool> = _activeTool.asStateFlow()

    // Detected words on underlying PDF page for long-press selection & direct text editing
    private val _detectedWords = MutableStateFlow<Map<Int, List<DetectedPdfWord>>>(emptyMap())
    val detectedWords: StateFlow<Map<Int, List<DetectedPdfWord>>> = _detectedWords.asStateFlow()

    private val _selectedWords = MutableStateFlow<List<DetectedPdfWord>>(emptyList())
    val selectedWords: StateFlow<List<DetectedPdfWord>> = _selectedWords.asStateFlow()

    // Undo / Redo history stacks
    private val undoStack = Stack<Map<Int, List<EditorElement>>>()
    private val redoStack = Stack<Map<Int, List<EditorElement>>>()

    private var sourceDocUri: Uri? = null

    // System clipboard & formatted text clipboard cache
    private var clipboardElement: EditorElement? = null
    var cachedClipboardText: String? = null
        private set
    var cachedClipboardFontSizePt: Float = 16f
        private set
    var cachedClipboardTextColor: Long = 0xFF000000
        private set
    var cachedClipboardIsBold: Boolean = false
        private set
    var cachedClipboardIsItalic: Boolean = false
        private set

    fun loadDocument(uri: Uri, documentName: String? = null) {
        sourceDocUri = uri
        _status.value = EditorUiStatus.Loading("Loading PDF for editing...")

        viewModelScope.launch {
            when (val result = renderer.openDocument(uri)) {
                is PdfLoadResult.Success -> {
                    val info = result.documentInfo
                    _status.value = EditorUiStatus.Loaded(info.copy(title = documentName ?: info.title))
                    _currentPage.value = 0
                    val dims = renderer.getPageDimensions(0) ?: Pair(595, 842)
                    _pageDimensions.value = dims
                }
                is PdfLoadResult.PasswordRequired -> {
                    _status.value = EditorUiStatus.Error(result.message)
                }
                is PdfLoadResult.Corrupted -> {
                    _status.value = EditorUiStatus.Error(result.message)
                }
                is PdfLoadResult.Error -> {
                    _status.value = EditorUiStatus.Error(result.throwable.localizedMessage ?: "Failed to open PDF")
                }
            }
        }
    }

    suspend fun renderPageBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bmp = renderer.renderPage(pageIndex, targetWidth, targetHeight)
        if (bmp != null && !_detectedWords.value.containsKey(pageIndex)) {
            val (wPt, hPt) = _pageDimensions.value
            val words = PdfTextExtractionEngine.extractWordsFromPage(
                context = context,
                pageBitmap = bmp,
                pageIndex = pageIndex,
                pageWidthPt = wPt,
                pageHeightPt = hPt,
                sourceUri = sourceDocUri
            )
            _detectedWords.update { it + (pageIndex to words) }
        }
        return bmp
    }

    fun setPage(index: Int) {
        val total = renderer.getPageCount()
        if (index in 0 until total) {
            _currentPage.value = index
            val dims = renderer.getPageDimensions(index) ?: Pair(595, 842)
            _pageDimensions.value = dims
            _selectedElementId.value = null
            _selectedWords.value = emptyList()
        }
    }

    fun setInteractionMode(mode: EditorInteractionMode) {
        _interactionMode.value = mode
        when (mode) {
            EditorInteractionMode.PAGE_NAV -> {
                _selectedElementId.value = null
                _selectedWords.value = emptyList()
            }
            EditorInteractionMode.TEXT_EDIT -> {
                _activeTool.value = ActiveEditorTool.TEXT
            }
            EditorInteractionMode.IMAGE_EDIT -> {
                _activeTool.value = ActiveEditorTool.IMAGE
            }
            EditorInteractionMode.SHAPE_ANNOTATION -> {
                _activeTool.value = ActiveEditorTool.SHAPE
            }
            EditorInteractionMode.SELECT -> {
                _activeTool.value = ActiveEditorTool.SELECT
            }
        }
    }

    fun selectElement(elementId: String?) {
        _selectedElementId.value = elementId
        if (elementId != null) {
            _selectedWords.value = emptyList()
        }
    }

    fun setActiveTool(tool: ActiveEditorTool) {
        _activeTool.value = tool
        when (tool) {
            ActiveEditorTool.SELECT -> _interactionMode.value = EditorInteractionMode.SELECT
            ActiveEditorTool.TEXT -> _interactionMode.value = EditorInteractionMode.TEXT_EDIT
            ActiveEditorTool.IMAGE -> _interactionMode.value = EditorInteractionMode.IMAGE_EDIT
            ActiveEditorTool.SHAPE, ActiveEditorTool.PEN, ActiveEditorTool.HIGHLIGHTER, ActiveEditorTool.SIGNATURE ->
                _interactionMode.value = EditorInteractionMode.SHAPE_ANNOTATION
        }
        if (tool != ActiveEditorTool.SELECT) {
            _selectedElementId.value = null
            _selectedWords.value = emptyList()
        }
    }

    // --- PDF Text Hit-Testing, Selection & Contextual Operations ---

    fun onLongPressAtPoint(touchPtX: Float, touchPtY: Float) {
        val page = _currentPage.value
        val words = _detectedWords.value[page].orEmpty()
        val hitWord = PdfTextExtractionEngine.findWordAt(touchPtX, touchPtY, words)
        if (hitWord != null) {
            _selectedWords.value = listOf(hitWord)
            _selectedElementId.value = null // Deselect overlay elements
        }
    }

    fun expandSelectionToWord(targetWord: DetectedPdfWord) {
        val current = _selectedWords.value
        if (current.isEmpty()) {
            _selectedWords.value = listOf(targetWord)
            return
        }
        val startWord = current.first()
        val page = _currentPage.value
        val words = _detectedWords.value[page].orEmpty()
        _selectedWords.value = PdfTextExtractionEngine.getWordsInRange(startWord, targetWord, words)
    }

    fun selectAllWordsOnCurrentPage() {
        val page = _currentPage.value
        val words = _detectedWords.value[page].orEmpty()
        _selectedWords.value = words
        _selectedElementId.value = null
    }

    fun clearWordSelection() {
        _selectedWords.value = emptyList()
    }

    fun copySelectedWords(context: Context): String {
        val words = _selectedWords.value
        val text = words.joinToString(" ") { it.text }
        if (text.isNotBlank()) {
            cachedClipboardText = text
            if (words.isNotEmpty()) {
                cachedClipboardFontSizePt = words.first().fontSizePt
                cachedClipboardTextColor = words.first().textColor
                cachedClipboardIsBold = words.first().isBold
            }
            // Put on system clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied PDF Text", text)
            clipboard.setPrimaryClip(clip)
        }
        return text
    }

    fun cutSelectedWords(context: Context): String {
        val copied = copySelectedWords(context)
        deleteSelectedWords()
        return copied
    }

    fun deleteSelectedWords() {
        val words = _selectedWords.value
        if (words.isEmpty()) return

        val page = _currentPage.value
        pushUndo()

        // Create clean opaque redaction cover matching page background (white)
        val minX = words.minOf { it.bounds.left } - 2f
        val minY = words.minOf { it.bounds.top } - 1f
        val maxX = words.maxOf { it.bounds.right } + 2f
        val maxY = words.maxOf { it.bounds.bottom } + 1f

        val redactionShape = ShapeElement(
            pageIndex = page,
            x = minX,
            y = minY,
            width = (maxX - minX).coerceAtLeast(10f),
            height = (maxY - minY).coerceAtLeast(10f),
            shapeType = ShapeType.RECTANGLE,
            strokeColor = 0xFFFFFFFF,
            fillColor = 0xFFFFFFFF,
            strokeWidthPt = 0f
        )
        addElement(redactionShape)
        _selectedWords.value = emptyList()
    }

    fun replaceSelectedWords(
        newText: String,
        fontSizePt: Float,
        textColor: Long,
        backgroundColor: Long? = null,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        isUnderline: Boolean = false,
        textAlign: String = "LEFT"
    ) {
        val words = _selectedWords.value
        val page = _currentPage.value
        pushUndo()

        val minX = if (words.isNotEmpty()) words.minOf { it.bounds.left } else 60f
        val minY = if (words.isNotEmpty()) words.minOf { it.bounds.top } else 100f
        val origW = if (words.isNotEmpty()) (words.maxOf { it.bounds.right } - minX).coerceAtLeast(40f) else 140f
        val origH = if (words.isNotEmpty()) (words.maxOf { it.bounds.bottom } - minY).coerceAtLeast(20f) else 30f

        val approxWidth = maxOf(origW, newText.length * fontSizePt * 0.65f)

        val replacementElem = TextElement(
            pageIndex = page,
            x = minX,
            y = minY,
            width = approxWidth,
            height = origH.coerceAtLeast(fontSizePt + 6f),
            text = newText,
            fontSizePt = fontSizePt,
            textColor = textColor,
            backgroundColor = backgroundColor, // Respects null! No unwanted background box
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            textAlign = textAlign
        )

        addElement(replacementElem)
        _selectedWords.value = emptyList()
    }

    fun pasteTextAtPosition(
        x: Float,
        y: Float,
        systemText: String? = null
    ) {
        val textToPaste = systemText ?: cachedClipboardText ?: "Pasted Text"
        val page = _currentPage.value
        val approxW = (textToPaste.length * cachedClipboardFontSizePt * 0.65f).coerceIn(80f, 350f)

        val newTextElem = TextElement(
            pageIndex = page,
            x = x,
            y = y,
            width = approxW,
            height = cachedClipboardFontSizePt + 12f,
            text = textToPaste,
            fontSizePt = cachedClipboardFontSizePt,
            textColor = cachedClipboardTextColor,
            isBold = cachedClipboardIsBold,
            isItalic = cachedClipboardIsItalic,
            backgroundColor = null
        )
        addElement(newTextElem)
    }

    // --- Object Movement, Resizing & Mutation ---

    private fun pushUndo() {
        undoStack.push(_elements.value)
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        if (canUndo()) {
            redoStack.push(_elements.value)
            _elements.value = undoStack.pop()
            _selectedElementId.value = null
        }
    }

    fun redo() {
        if (canRedo()) {
            undoStack.push(_elements.value)
            _elements.value = redoStack.pop()
        }
    }

    fun addElement(element: EditorElement) {
        pushUndo()
        val page = element.pageIndex
        _elements.update { map ->
            val pageList = map[page].orEmpty() + element
            map + (page to pageList)
        }
        _selectedElementId.value = element.id
        _activeTool.value = ActiveEditorTool.SELECT
    }

    fun beginElementTransform() {
        pushUndo()
    }

    fun updateElementPosition(elementId: String, newX: Float, newY: Float) {
        val page = _currentPage.value
        _elements.update { map ->
            val pageList = map[page].orEmpty().map { elem ->
                if (elem.id == elementId) elem.copyWithPosition(newX, newY) else elem
            }
            map + (page to pageList)
        }
    }

    fun updateElementSize(elementId: String, newWidth: Float, newHeight: Float) {
        val page = _currentPage.value
        _elements.update { map ->
            val pageList = map[page].orEmpty().map { elem ->
                if (elem.id == elementId) elem.copyWithSize(newWidth.coerceAtLeast(20f), newHeight.coerceAtLeast(20f)) else elem
            }
            map + (page to pageList)
        }
    }

    fun updateElementRotation(elementId: String, degrees: Float) {
        val page = _currentPage.value
        _elements.update { map ->
            val pageList = map[page].orEmpty().map { elem ->
                if (elem.id == elementId) elem.copyWithRotation(degrees) else elem
            }
            map + (page to pageList)
        }
    }

    fun updateTextElement(
        elementId: String,
        newText: String,
        fontSizePt: Float,
        textColor: Long,
        isBold: Boolean,
        isItalic: Boolean,
        isUnderline: Boolean = false,
        backgroundColor: Long? = null,
        textAlign: String = "LEFT"
    ) {
        pushUndo()
        val page = _currentPage.value
        _elements.update { map ->
            val pageList = map[page].orEmpty().map { elem ->
                if (elem.id == elementId && elem is TextElement) {
                    elem.copy(
                        text = newText,
                        fontSizePt = fontSizePt,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        isBold = isBold,
                        isItalic = isItalic,
                        isUnderline = isUnderline,
                        textAlign = textAlign
                    )
                } else elem
            }
            map + (page to pageList)
        }
    }

    fun copyElement(elementId: String) {
        val page = _currentPage.value
        clipboardElement = _elements.value[page]?.find { it.id == elementId }
        if (clipboardElement is TextElement) {
            val t = clipboardElement as TextElement
            cachedClipboardText = t.text
            cachedClipboardFontSizePt = t.fontSizePt
            cachedClipboardTextColor = t.textColor
            cachedClipboardIsBold = t.isBold
            cachedClipboardIsItalic = t.isItalic
            // System clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Copied Element", t.text))
        }
    }

    fun hasClipboard(): Boolean = clipboardElement != null || cachedClipboardText != null

    fun pasteElementAt(x: Float = 60f, y: Float = 100f) {
        val target = clipboardElement
        if (target != null) {
            val page = _currentPage.value
            val pasted = when (target) {
                is TextElement -> target.copy(id = UUID.randomUUID().toString(), pageIndex = page, x = x, y = y)
                is ShapeElement -> target.copy(id = UUID.randomUUID().toString(), pageIndex = page, x = x, y = y)
                is SignatureElement -> target.copy(id = UUID.randomUUID().toString(), pageIndex = page, x = x, y = y)
                is ImageElement -> target.copy(id = UUID.randomUUID().toString(), pageIndex = page, x = x, y = y)
            }
            addElement(pasted)
        } else if (cachedClipboardText != null) {
            pasteTextAtPosition(x, y)
        }
    }

    fun duplicateElement(elementId: String) {
        val page = _currentPage.value
        val target = _elements.value[page]?.find { it.id == elementId } ?: return
        pushUndo()
        val duplicated = when (target) {
            is TextElement -> target.copy(id = UUID.randomUUID().toString(), x = target.x + 20f, y = target.y + 20f)
            is ShapeElement -> target.copy(id = UUID.randomUUID().toString(), x = target.x + 20f, y = target.y + 20f)
            is SignatureElement -> target.copy(id = UUID.randomUUID().toString(), x = target.x + 20f, y = target.y + 20f)
            is ImageElement -> target.copy(id = UUID.randomUUID().toString(), x = target.x + 20f, y = target.y + 20f)
        }
        addElement(duplicated)
    }

    fun deleteSelectedElement() {
        val id = _selectedElementId.value ?: return
        val page = _currentPage.value
        pushUndo()
        _elements.update { map ->
            val pageList = map[page].orEmpty().filterNot { it.id == id }
            map + (page to pageList)
        }
        _selectedElementId.value = null
    }

    fun bringForward(elementId: String) {
        pushUndo()
        val page = _currentPage.value
        _elements.update { map ->
            val pageList = map[page].orEmpty().map { elem ->
                if (elem.id == elementId) elem.copyWithPosition(elem.x, elem.y).let {
                    when (it) {
                        is TextElement -> it.copy(zIndex = it.zIndex + 1)
                        is ShapeElement -> it.copy(zIndex = it.zIndex + 1)
                        is ImageElement -> it.copy(zIndex = it.zIndex + 1)
                        is SignatureElement -> it.copy(zIndex = it.zIndex + 1)
                    }
                } else elem
            }
            map + (page to pageList)
        }
    }

    fun sendBackward(elementId: String) {
        pushUndo()
        val page = _currentPage.value
        _elements.update { map ->
            val pageList = map[page].orEmpty().map { elem ->
                if (elem.id == elementId) elem.copyWithPosition(elem.x, elem.y).let {
                    when (it) {
                        is TextElement -> it.copy(zIndex = (it.zIndex - 1).coerceAtLeast(0))
                        is ShapeElement -> it.copy(zIndex = (it.zIndex - 1).coerceAtLeast(0))
                        is ImageElement -> it.copy(zIndex = (it.zIndex - 1).coerceAtLeast(0))
                        is SignatureElement -> it.copy(zIndex = (it.zIndex - 1).coerceAtLeast(0))
                    }
                } else elem
            }
            map + (page to pageList)
        }
    }

    // --- Export & Save ---

    fun exportEditedPdf(outputName: String, onSuccess: (File) -> Unit) {
        val uri = sourceDocUri ?: return
        val total = renderer.getPageCount()
        _status.value = EditorUiStatus.Exporting("Compiling document edits into new PDF...")

        viewModelScope.launch {
            try {
                val exportedFile = PdfExportEngine.exportPdfWithEdits(
                    context = context,
                    sourceUri = uri,
                    pageCount = total,
                    elementsByPage = _elements.value,
                    outputFileName = outputName
                )
                _status.value = EditorUiStatus.ExportSuccess(exportedFile)
                onSuccess(exportedFile)
            } catch (e: Exception) {
                _status.value = EditorUiStatus.Error(e.localizedMessage ?: "Failed to export PDF")
            }
        }
    }

    fun resetExportStatus() {
        val current = _status.value
        if (current is EditorUiStatus.ExportSuccess || current is EditorUiStatus.Error) {
            val total = renderer.getPageCount()
            _status.value = EditorUiStatus.Loaded(
                PdfDocumentInfo(
                    uri = sourceDocUri ?: Uri.EMPTY,
                    title = sourceDocUri?.lastPathSegment ?: "Document",
                    pageCount = total,
                    fileSize = 0L
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderer.close()
    }
}

class PdfEditorViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PdfEditorViewModel(context) as T
    }
}
