package com.example.feature.pdfviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.PdfFitMode
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.DocumentRepository
import com.example.engine.core.IPdfRenderer
import com.example.engine.core.PdfDocumentInfo
import com.example.engine.core.PdfLoadResult
import com.example.engine.editor.PdfTextExtractionEngine
import com.example.engine.renderer.AndroidNativePdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class SearchMatch(
    val pageNumber: Int,
    val snippet: String
)

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Loaded(
        val documentInfo: PdfDocumentInfo,
        val currentPage: Int = 1,
        val totalPages: Int,
        val fitMode: PdfFitMode = PdfFitMode.FIT_WIDTH,
        val zoomScale: Float = 1.0f,
        val searchQuery: String = "",
        val searchMatches: List<SearchMatch> = emptyList(),
        val currentMatchIndex: Int = -1,
        val isSearching: Boolean = false
    ) : ViewerUiState
    data class PasswordProtected(val message: String) : ViewerUiState
    data class Error(val message: String) : ViewerUiState
}

class PdfViewerViewModel(
    private val context: Context,
    private val documentRepository: DocumentRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val renderer: IPdfRenderer = AndroidNativePdfRenderer(context)

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var currentDocumentUri: Uri? = null

    fun loadDocument(uri: Uri, documentName: String? = null) {
        currentDocumentUri = uri
        _uiState.value = ViewerUiState.Loading

        viewModelScope.launch {
            when (val result = renderer.openDocument(uri)) {
                is PdfLoadResult.Success -> {
                    val info = result.documentInfo
                    _uiState.value = ViewerUiState.Loaded(
                        documentInfo = info.copy(title = documentName ?: info.title),
                        currentPage = 1,
                        totalPages = info.pageCount
                    )
                    // Register into Room database recents
                    documentRepository.registerOpenedDocument(
                        uri = uri,
                        customName = documentName ?: info.title,
                        pageCount = info.pageCount
                    )
                }
                is PdfLoadResult.PasswordRequired -> {
                    _uiState.value = ViewerUiState.PasswordProtected(result.message)
                }
                is PdfLoadResult.Corrupted -> {
                    _uiState.value = ViewerUiState.Error(result.message)
                }
                is PdfLoadResult.Error -> {
                    _uiState.value = ViewerUiState.Error(
                        result.throwable.localizedMessage ?: "Unknown error opening PDF"
                    )
                }
            }
        }
    }

    suspend fun renderPageBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        return renderer.renderPage(pageIndex, targetWidth, targetHeight)
    }

    fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? {
        return renderer.getPageDimensions(pageIndex)
    }

    fun onPageChanged(pageNumber: Int) {
        val current = _uiState.value
        if (current is ViewerUiState.Loaded) {
            val validPage = pageNumber.coerceIn(1, current.totalPages)
            _uiState.value = current.copy(currentPage = validPage)
        }
    }

    fun setFitMode(fitMode: PdfFitMode) {
        val current = _uiState.value
        if (current is ViewerUiState.Loaded) {
            val scale = when (fitMode) {
                PdfFitMode.FIT_WIDTH -> 1.0f
                PdfFitMode.FIT_PAGE -> 0.85f
                PdfFitMode.ACTUAL_SIZE -> 1.25f
            }
            _uiState.value = current.copy(fitMode = fitMode, zoomScale = scale)
        }
    }

    fun setZoomScale(scale: Float) {
        val current = _uiState.value
        if (current is ViewerUiState.Loaded) {
            _uiState.value = current.copy(zoomScale = scale.coerceIn(0.8f, 3.5f))
        }
    }

    fun searchInPdf(query: String) {
        val current = _uiState.value
        if (current !is ViewerUiState.Loaded || currentDocumentUri == null) return

        if (query.isBlank()) {
            _uiState.value = current.copy(searchQuery = "", searchMatches = emptyList(), currentMatchIndex = -1, isSearching = false)
            return
        }

        _uiState.value = current.copy(searchQuery = query, isSearching = true)

        viewModelScope.launch {
            val matches = withContext(Dispatchers.IO) {
                val list = mutableListOf<SearchMatch>()
                try {
                    val uri = currentDocumentUri ?: return@withContext emptyList()
                    val temp = File(context.cacheDir, "temp_search_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { inStream ->
                        FileOutputStream(temp).use { outStream -> inStream.copyTo(outStream) }
                    }

                    if (temp.exists() && temp.length() > 0) {
                        val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                        val pdfRenderer = PdfRenderer(pfd)
                        val pageCount = pdfRenderer.pageCount

                        for (i in 0 until pageCount) {
                            val words = PdfTextExtractionEngine.extractWordsFromPage(
                                context = context,
                                pageBitmap = null,
                                pageIndex = i,
                                pageWidthPt = 595,
                                pageHeightPt = 842,
                                sourceUri = uri
                            )
                            val pageText = words.joinToString(" ") { it.text }
                            if (pageText.contains(query, ignoreCase = true)) {
                                list.add(SearchMatch(pageNumber = i + 1, snippet = pageText.take(120)))
                            }
                        }
                        pdfRenderer.close()
                        pfd.close()
                    }
                    temp.delete()
                } catch (_: Exception) {}
                list
            }

            val updatedState = _uiState.value
            if (updatedState is ViewerUiState.Loaded) {
                val firstMatchPage = matches.firstOrNull()?.pageNumber ?: updatedState.currentPage
                _uiState.value = updatedState.copy(
                    searchMatches = matches,
                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1,
                    currentPage = firstMatchPage,
                    isSearching = false
                )
            }
        }
    }

    fun nextSearchMatch() {
        val current = _uiState.value
        if (current is ViewerUiState.Loaded && current.searchMatches.isNotEmpty()) {
            val nextIndex = (current.currentMatchIndex + 1) % current.searchMatches.size
            val targetPage = current.searchMatches[nextIndex].pageNumber
            _uiState.value = current.copy(currentMatchIndex = nextIndex, currentPage = targetPage)
        }
    }

    fun previousSearchMatch() {
        val current = _uiState.value
        if (current is ViewerUiState.Loaded && current.searchMatches.isNotEmpty()) {
            val prevIndex = if (current.currentMatchIndex - 1 < 0) current.searchMatches.size - 1 else current.currentMatchIndex - 1
            val targetPage = current.searchMatches[prevIndex].pageNumber
            _uiState.value = current.copy(currentMatchIndex = prevIndex, currentPage = targetPage)
        }
    }

    fun clearSearch() {
        val current = _uiState.value
        if (current is ViewerUiState.Loaded) {
            _uiState.value = current.copy(searchQuery = "", searchMatches = emptyList(), currentMatchIndex = -1, isSearching = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderer.close()
    }
}

class PdfViewerViewModelFactory(
    private val context: Context,
    private val documentRepository: DocumentRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PdfViewerViewModel(context, documentRepository, preferencesRepository) as T
    }
}
