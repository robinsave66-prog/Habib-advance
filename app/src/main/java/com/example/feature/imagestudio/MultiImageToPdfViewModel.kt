package com.example.feature.imagestudio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface MultiImagePdfState {
    data object Idle : MultiImagePdfState
    data class Generating(val progress: String) : MultiImagePdfState
    data class Success(val generatedFile: File, val pageCount: Int) : MultiImagePdfState
    data class Error(val message: String) : MultiImagePdfState
}

class MultiImageToPdfViewModel(
    private val context: Context
) : ViewModel() {

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images.asStateFlow()

    private val _settings = MutableStateFlow(PdfPageSettings())
    val settings: StateFlow<PdfPageSettings> = _settings.asStateFlow()

    private val _uiState = MutableStateFlow<MultiImagePdfState>(MultiImagePdfState.Idle)
    val uiState: StateFlow<MultiImagePdfState> = _uiState.asStateFlow()

    fun addImages(uris: List<Uri>) {
        val newItems = uris.map { uri ->
            ImageItem(
                uri = uri,
                name = queryFileName(uri) ?: "Image_${System.currentTimeMillis() % 1000}"
            )
        }
        _images.update { it + newItems }
    }

    fun removeImage(id: String) {
        _images.update { current -> current.filterNot { it.id == id } }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        _images.update { list ->
            if (fromIndex !in list.indices || toIndex !in list.indices) return@update list
            val mutable = list.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable
        }
    }

    fun rotateImage(id: String) {
        _images.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(rotationDegrees = (item.rotationDegrees + 90f) % 360f)
                } else item
            }
        }
    }

    fun updateImageCaption(
        id: String,
        caption: String,
        textColor: Long = 0xFF0F3B66,
        bgColor: Long? = 0x88FFFFFF,
        fontSizePt: Float = 14f
    ) {
        _images.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(
                        caption = caption,
                        captionTextColor = textColor,
                        captionBgColor = bgColor,
                        captionFontSizePt = fontSizePt
                    )
                } else item
            }
        }
    }

    fun clearAll() {
        _images.value = emptyList()
        _uiState.value = MultiImagePdfState.Idle
    }

    fun updateSettings(newSettings: PdfPageSettings) {
        _settings.value = newSettings
    }

    fun generatePdf(fileName: String, onSuccess: (File) -> Unit) {
        val currentImages = _images.value
        if (currentImages.isEmpty()) {
            _uiState.value = MultiImagePdfState.Error("Please select at least one image.")
            return
        }

        _uiState.value = MultiImagePdfState.Generating("Assembling ${currentImages.size} pages...")

        viewModelScope.launch {
            try {
                val pdfFile = MultiImageToPdfEngine.generatePdfFromImages(
                    context = context,
                    images = currentImages,
                    settings = _settings.value,
                    outputFileName = fileName
                )
                _uiState.value = MultiImagePdfState.Success(pdfFile, currentImages.size)
                onSuccess(pdfFile)
            } catch (e: Exception) {
                _uiState.value = MultiImagePdfState.Error(e.localizedMessage ?: "Failed to generate PDF")
            }
        }
    }

    fun resetState() {
        _uiState.value = MultiImagePdfState.Idle
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }
}

class MultiImageToPdfViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MultiImageToPdfViewModel(context) as T
    }
}
