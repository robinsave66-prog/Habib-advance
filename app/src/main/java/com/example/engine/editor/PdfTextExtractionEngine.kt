package com.example.engine.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

data class DetectedPdfWord(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val bounds: RectF, // PDF points, top-left origin
    val pageIndex: Int,
    val fontSizePt: Float = 14f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val textColor: Long = 0xFF000000
)

data class DetectedPdfLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val bounds: RectF,
    val words: List<DetectedPdfWord>
)

object PdfTextExtractionEngine {
    /**
     * Extract actual native PDF glyph positions first. For scanned pages, fall back
     * to on-device ML Kit Latin OCR. Never return fabricated sample words.
     */
    suspend fun extractWordsFromPage(
        context: Context,
        pageBitmap: Bitmap?,
        pageIndex: Int,
        pageWidthPt: Int,
        pageHeightPt: Int,
        sourceUri: Uri?
    ): List<DetectedPdfWord> = withContext(Dispatchers.IO) {
        require(pageIndex >= 0) { "Page index cannot be negative." }
        require(pageWidthPt > 0 && pageHeightPt > 0) { "Page dimensions must be positive." }

        val nativeWords = if (sourceUri != null) extractNativePdfWords(context, sourceUri, pageIndex) else emptyList()
        if (nativeWords.isNotEmpty()) return@withContext nativeWords
        if (pageBitmap == null || pageBitmap.isRecycled) return@withContext emptyList()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val visionText = recognizer.process(InputImage.fromBitmap(pageBitmap, 0)).await()
            val scaleX = pageWidthPt.toFloat() / pageBitmap.width.toFloat()
            val scaleY = pageHeightPt.toFloat() / pageBitmap.height.toFloat()
            buildList {
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        for (element in line.elements) {
                            val box = element.boundingBox ?: continue
                            val left = box.left * scaleX
                            val top = box.top * scaleY
                            val right = box.right * scaleX
                            val bottom = box.bottom * scaleY
                            add(DetectedPdfWord(
                                text = element.text,
                                bounds = RectF(left, top, right, bottom),
                                pageIndex = pageIndex,
                                fontSizePt = ((bottom - top) * 0.85f).coerceIn(1f, 200f)
                            ))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            recognizer.close()
        }
    }

    private fun extractNativePdfWords(context: Context, uri: Uri, pageIndex: Int): List<DetectedPdfWord> {
        val source = File(context.cacheDir, "pdf-text-${UUID.randomUUID()}.pdf")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(source).use { output -> input.copyTo(output) }
            } ?: return emptyList()
            if (source.length() == 0L) return emptyList()
            PDFBoxResourceLoader.init(context)
            PDDocument.load(source).use { document ->
                if (pageIndex !in 0 until document.numberOfPages) return emptyList()
                val stripper = PositionedTextStripper()
                stripper.sortByPosition = true
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                stripper.getText(document)
                stripper.toWords(pageIndex)
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            source.delete()
        }
    }

    private class PositionedTextStripper : PDFTextStripper() {
        private val positions = mutableListOf<TextPosition>()

        override fun processTextPosition(text: TextPosition) {
            super.processTextPosition(text)
            positions += text
        }

        fun toWords(pageIndex: Int): List<DetectedPdfWord> {
            if (positions.isEmpty()) return emptyList()
            val words = mutableListOf<DetectedPdfWord>()
            val text = StringBuilder()
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = Float.MIN_VALUE
            var bottom = Float.MIN_VALUE
            var fontSize = 14f
            var bold = false
            var italic = false
            var lastBaseline = Float.NaN

            fun flush() {
                val value = text.toString().trim()
                if (value.isNotEmpty() && left.isFinite() && top.isFinite() && right > left && bottom > top) {
                    words += DetectedPdfWord(
                        text = value,
                        bounds = RectF(left, top, right, bottom),
                        pageIndex = pageIndex,
                        fontSizePt = fontSize.coerceIn(1f, 200f),
                        isBold = bold,
                        isItalic = italic
                    )
                }
                text.clear()
                left = Float.MAX_VALUE
                top = Float.MAX_VALUE
                right = Float.MIN_VALUE
                bottom = Float.MIN_VALUE
                fontSize = 14f
                bold = false
                italic = false
            }

            for (position in positions) {
                val glyph = position.unicode.orEmpty()
                if (glyph.isBlank()) {
                    flush()
                    lastBaseline = Float.NaN
                    continue
                }
                val baseline = position.yDirAdj
                val glyphHeight = max(position.heightDir, 1f)
                if (lastBaseline.isFinite() && abs(baseline - lastBaseline) > max(2f, glyphHeight * 0.45f)) flush()
                val glyphLeft = position.xDirAdj
                val glyphRight = glyphLeft + max(position.widthDirAdj, 0.1f)
                left = minOf(left, glyphLeft)
                top = minOf(top, baseline - glyphHeight)
                right = max(right, glyphRight)
                bottom = max(bottom, baseline)
                text.append(glyph)
                fontSize = max(fontSize, position.fontSizeInPt)
                val fontName = position.font?.name.orEmpty().lowercase()
                bold = bold || fontName.contains("bold")
                italic = italic || fontName.contains("italic") || fontName.contains("oblique")
                lastBaseline = baseline
            }
            flush()
            return words
        }
    }

    fun findWordAt(touchPtX: Float, touchPtY: Float, words: List<DetectedPdfWord>): DetectedPdfWord? {
        words.firstOrNull { it.bounds.contains(touchPtX, touchPtY) }?.let { return it }
        val tolerance = 14f
        val nearHit = words.minByOrNull { word ->
            val dx = word.bounds.centerX() - touchPtX
            val dy = word.bounds.centerY() - touchPtY
            dx * dx + dy * dy
        } ?: return null
        return nearHit.takeIf {
            RectF(it.bounds.left - tolerance, it.bounds.top - tolerance,
                it.bounds.right + tolerance, it.bounds.bottom + tolerance).contains(touchPtX, touchPtY)
        }
    }

    fun getWordsInRange(
        startWord: DetectedPdfWord,
        endWord: DetectedPdfWord,
        allWords: List<DetectedPdfWord>
    ): List<DetectedPdfWord> {
        val sorted = allWords.sortedWith(compareBy<DetectedPdfWord> { it.bounds.top }.thenBy { it.bounds.left })
        val firstIndex = sorted.indexOfFirst { it.id == startWord.id }
        val lastIndex = sorted.indexOfFirst { it.id == endWord.id }
        if (firstIndex == -1 || lastIndex == -1) return listOf(startWord)
        return sorted.subList(minOf(firstIndex, lastIndex), maxOf(firstIndex, lastIndex) + 1)
    }
}
