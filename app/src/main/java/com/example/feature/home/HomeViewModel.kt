package com.example.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.sample.SamplePdfGenerator
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentType
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val recentDocuments: List<DocumentEntity> = emptyList(),
    val searchQuery: String = "",
    val isInitialized: Boolean = false
)

class HomeViewModel(
    private val documentRepository: DocumentRepository,
    private val samplePdfGenerator: SamplePdfGenerator,
    private val context: android.content.Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        documentRepository.recentDocuments,
        _searchQuery
    ) { recents, query ->
        val filtered = if (query.isBlank()) {
            recents
        } else {
            recents.filter { it.name.contains(query, ignoreCase = true) }
        }
        HomeUiState(
            recentDocuments = filtered,
            searchQuery = query,
            isInitialized = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        // Ensure sample PDF exists and is in recent history for immediate exploration
        viewModelScope.launch {
            val sampleFile = SamplePdfGenerator.createSamplePdfIfMissing(context)
            val sampleUri = Uri.fromFile(sampleFile)
            documentRepository.registerOpenedDocument(
                uri = sampleUri,
                customName = "Habib_Advance_Editor_Guide.pdf",
                customType = DocumentType.PDF,
                pageCount = 3,
                isSample = true,
                localPath = sampleFile.absolutePath
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun removeRecentDocument(uri: String) {
        viewModelScope.launch {
            documentRepository.removeDocument(uri)
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.toggleFavorite(doc.uri, doc.isFavorite)
        }
    }
}

class HomeViewModelFactory(
    private val documentRepository: DocumentRepository,
    private val samplePdfGenerator: SamplePdfGenerator,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(documentRepository, samplePdfGenerator, context) as T
    }
}
