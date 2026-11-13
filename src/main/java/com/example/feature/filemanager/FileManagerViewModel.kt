package com.example.feature.filemanager

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

enum class SortOption {
    DATE_DESC,
    NAME_ASC,
    SIZE_DESC
}

enum class FilterCategory {
    ALL,
    PDF,
    WORD,
    IMAGES,
    FAVORITES
}

data class FileManagerUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val filterCategory: FilterCategory = FilterCategory.ALL,
    val selectedDocForInfo: DocumentEntity? = null
)

class FileManagerViewModel(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    private val _filterCategory = MutableStateFlow(FilterCategory.ALL)
    private val _selectedDocForInfo = MutableStateFlow<DocumentEntity?>(null)

    val uiState: StateFlow<FileManagerUiState> = combine(
        documentRepository.allDocuments,
        _searchQuery,
        _sortOption,
        _filterCategory,
        _selectedDocForInfo
    ) { docs, query, sort, filter, selectedDoc ->
        var filtered = docs

        // Apply filter category
        filtered = when (filter) {
            FilterCategory.ALL -> filtered
            FilterCategory.PDF -> filtered.filter { it.documentType == DocumentType.PDF }
            FilterCategory.WORD -> filtered.filter { it.documentType == DocumentType.WORD }
            FilterCategory.IMAGES -> filtered.filter { it.documentType == DocumentType.IMAGE }
            FilterCategory.FAVORITES -> filtered.filter { it.isFavorite }
        }

        // Apply search query
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.name.contains(query, ignoreCase = true) }
        }

        // Apply sort option
        filtered = when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.lastOpenedTimestamp }
            SortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }

        FileManagerUiState(
            documents = filtered,
            searchQuery = query,
            sortOption = sort,
            filterCategory = filter,
            selectedDocForInfo = selectedDoc
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FileManagerUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun setFilterCategory(category: FilterCategory) {
        _filterCategory.value = category
    }

    fun selectDocForInfo(doc: DocumentEntity?) {
        _selectedDocForInfo.value = doc
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.toggleFavorite(doc.uri, doc.isFavorite)
        }
    }

    fun removeDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            documentRepository.removeDocument(doc.uri)
            if (_selectedDocForInfo.value?.uri == doc.uri) {
                _selectedDocForInfo.value = null
            }
        }
    }

    fun registerImportedFile(uri: Uri, name: String? = null) {
        viewModelScope.launch {
            documentRepository.registerOpenedDocument(uri = uri, customName = name)
        }
    }
}

class FileManagerViewModelFactory(
    private val documentRepository: DocumentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FileManagerViewModel(documentRepository) as T
    }
}
