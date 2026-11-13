package com.example.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class OcrLanguage(val label: String, val tessCode: String) {
    ENGLISH("English", "eng"),
    BENGALI("বাংলা", "ben"),
    BENGALI_AND_ENGLISH("বাংলা + English", "ben+eng")
}

data class OcrWordResult(
    val text: String,
    val bounds: Rect,
    val confidence: Float
)

/** Offline OCR backed by Tesseract 5 and packaged Bengali/English models. */
object OfflineTesseractOcrEngine {
    private const val DATA_DIRECTORY = "tesseract"
    private const val TESSDATA_DIRECTORY = "tessdata"
    private val requiredModels = listOf("eng.traineddata", "ben.traineddata")

    suspend fun openSession(context: Context, language: OcrLanguage): Session =
        withContext(Dispatchers.IO) { Session(context.applicationContext, language) }

    class Session internal constructor(context: Context, language: OcrLanguage) : AutoCloseable {
        private val api = TessBaseAPI()
        private var closed = false

        init {
            val dataDirectory = File(context.filesDir, DATA_DIRECTORY)
            val tessdataDirectory = File(dataDirectory, TESSDATA_DIRECTORY)
            if (!tessdataDirectory.exists() && !tessdataDirectory.mkdirs()) {
                api.recycle()
                throw IllegalStateException("Unable to create private OCR model directory.")
            }
            try {
                requiredModels.forEach { modelName ->
                    val target = File(tessdataDirectory, modelName)
                    if (!target.exists() || target.length() == 0L) {
                        context.assets.open("$TESSDATA_DIRECTORY/$modelName").use { input ->
                            FileOutputStream(target).use { output -> input.copyTo(output) }
                        }
                    }
                    check(target.exists() && target.length() > 0L) { "OCR language model is missing: $modelName" }
                }
            } catch (error: Exception) {
                api.recycle()
                throw IllegalStateException("Offline OCR language models are not packaged correctly.", error)
            }

            if (!api.init(dataDirectory.absolutePath, language.tessCode, TessBaseAPI.OEM_LSTM_ONLY)) {
                api.recycle()
                throw IllegalStateException("Tesseract could not initialize ${language.label} OCR.")
            }
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        }

        fun recognize(bitmap: Bitmap): String {
            check(!closed) { "OCR session is already closed." }
            require(!bitmap.isRecycled) { "Cannot OCR a recycled bitmap." }
            api.setImage(bitmap)
            return api.getUTF8Text().orEmpty().trim()
        }

        fun recognizeWords(bitmap: Bitmap): List<OcrWordResult> {
            check(!closed) { "OCR session is already closed." }
            require(!bitmap.isRecycled) { "Cannot OCR a recycled bitmap." }
            api.setImage(bitmap)
            val iterator = api.getResultIterator() ?: return emptyList()
            val words = mutableListOf<OcrWordResult>()
            try {
                iterator.begin()
                do {
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
                    val bounds = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    val confidence = iterator.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (text.isNotEmpty() && bounds.width() > 0 && bounds.height() > 0 && confidence >= 20f) {
                        words += OcrWordResult(text, Rect(bounds), confidence)
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            } finally {
                iterator.delete()
            }
            return words
        }

        override fun close() {
            if (!closed) {
                closed = true
                api.recycle()
            }
        }
    }
}
