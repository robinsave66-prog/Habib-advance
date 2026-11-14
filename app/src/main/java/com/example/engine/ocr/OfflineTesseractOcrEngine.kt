package com.example.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

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
    private data class ModelSpec(val fileName: String, val size: Long, val sha1: String)

    private val requiredModels = listOf(
        ModelSpec("eng.traineddata", 4_113_088L, "31abd495e0f719db4f524c447e9d855124a0b0d6"),
        ModelSpec("ben.traineddata", 855_841L, "c300ca8fbf0543f2fdd3256dbd5a203f404b513d")
    )

    suspend fun openSession(context: Context, language: OcrLanguage): Session =
        withContext(Dispatchers.IO) { Session(context.applicationContext, language) }

    private fun matchesPinnedModel(file: File, model: ModelSpec): Boolean {
        if (!file.isFile || file.length() != model.size) return false
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return actual == model.sha1
    }

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
                requiredModels.forEach { model ->
                    val target = File(tessdataDirectory, model.fileName)
                    if (!matchesPinnedModel(target, model)) {
                        val temporary = File(tessdataDirectory, "${model.fileName}.tmp")
                        temporary.delete()
                        try {
                            context.assets.open("$TESSDATA_DIRECTORY/${model.fileName}").use { input ->
                                FileOutputStream(temporary).use { output -> input.copyTo(output) }
                            }
                            check(matchesPinnedModel(temporary, model)) {
                                "Packaged OCR model failed its integrity check: ${model.fileName}"
                            }
                            if (target.exists() && !target.delete()) {
                                throw IllegalStateException("Unable to replace the damaged OCR model: ${model.fileName}")
                            }
                            check(temporary.renameTo(target)) {
                                "Unable to install the verified OCR model: ${model.fileName}"
                            }
                        } finally {
                            temporary.delete()
                        }
                    }
                    check(matchesPinnedModel(target, model)) {
                        "OCR language model is missing or damaged: ${model.fileName}"
                    }
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
            api.getUTF8Text() // Run recognition before requesting word-level bounding boxes.
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
