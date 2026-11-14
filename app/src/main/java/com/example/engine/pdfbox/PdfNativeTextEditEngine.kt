package com.example.engine.pdfbox

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Conservative native PDF text replacement.
 *
 * This edits the original Tj/TJ string operand in the page content stream. It supports a
 * unique simple-Latin match in a Tj string or across adjacent strings within one TJ array,
 * encoded by the active page font, with exactly the same glyph advance width. Replacement
 * character count may differ when the rendered advance remains identical.
 * Unsupported layouts are rejected instead of being covered with a white rectangle or
 * silently shifting the rest of the page.
 *
 * Simple Latin replacement edits the original text operand. Bengali replacement uses a
 * searchable Type 3 glyph with an embedded stencil and ToUnicode mapping, replacing one
 * complete Tj run in the original content stream. Form XObjects, scanned pages, custom
 * encodings without ToUnicode, and Bengali text split across TJ runs remain unsupported.
 */
class PdfNativeTextEditEngine(private val context: Context) {

    suspend fun replaceText(
        sourceUri: Uri,
        pageIndex: Int,
        oldText: String,
        newText: String,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        require(pageIndex >= 0) { "Page index cannot be negative." }
        require(oldText.isNotBlank()) { "Select some existing PDF text first." }
        require(newText.isNotEmpty()) { "Replacement text cannot be empty." }
        val bengaliEdit = containsBengali(oldText) || containsBengali(newText)
        if (bengaliEdit) {
            require(containsBengali(oldText) && containsBengali(newText)) {
                "Bengali native editing requires Bengali text in both the selected phrase and replacement."
            }
        } else {
            require(isSupportedSimpleText(oldText) && isSupportedSimpleText(newText)) {
                "This native-edit mode supports simple Latin text and punctuation."
            }
        }

        PDFBoxResourceLoader.init(context)
        val source = File(context.cacheDir, "native-text-edit-${System.currentTimeMillis()}.pdf")
        val output = createOutputFile(outputFileName)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(source).use { destination -> input.copyTo(destination) }
            } ?: throw IllegalArgumentException("Cannot open the source PDF.")

            PDDocument.load(source).use { document ->
                require(pageIndex in 0 until document.numberOfPages) {
                    "The selected page does not exist."
                }
                val page = document.getPage(pageIndex)
                if (bengaliEdit) {
                    PdfBengaliNativeTextEngine(context).replace(document, page, oldText, newText)
                } else {
                val parser = PDFStreamParser(page)
                parser.parse()
                val tokens = parser.tokens
                var activeFont: PDFont? = null
                var activeFontSize = 0f
                var characterSpacing = 0f
                var wordSpacing = 0f
                var replacements = 0

                fun replaceOperand(decoded: String): COSString? {
                    val first = decoded.indexOf(oldText)
                    if (first < 0) return null
                    val font = activeFont
                        ?: throw IllegalArgumentException("The selected text has no supported active PDF font.")
                    require(activeFontSize > 0f) { "The selected text has an invalid font size." }
                    require(decoded.indexOf(oldText, first + oldText.length) < 0) {
                        "The selected text occurs more than once in one PDF text object. Select a more specific phrase."
                    }
                    // Include Tc and Tw in the horizontal text advance.
                    fun textAdvance(value: String): Float {
                        val glyphAdvance = font.getStringWidth(value) * activeFontSize / 1000f
                        val characterAdvance = characterSpacing * value.codePointCount(0, value.length)
                        val wordAdvance = if (font is PDType0Font) 0f
                            else wordSpacing * value.count { it == ' ' }
                        return glyphAdvance + characterAdvance + wordAdvance
                    }
                    require(kotlin.math.abs(textAdvance(oldText) - textAdvance(newText)) <= 0.01f) {
                        "The replacement has a different rendered advance in the original PDF text state. Layout-preserving edit was cancelled."
                    }
                    val updated = decoded.replaceRange(first, first + oldText.length, newText)
                    val encoded = font.encode(updated)
                    replacements += 1
                    return COSString(encoded)
                }

                for (index in tokens.indices) {
                    val token = tokens[index]
                    if (token is Operator) {
                        when (token.name) {
                            "Tc" -> {
                                characterSpacing = (tokens.getOrNull(index - 1) as? COSNumber)?.floatValue() ?: 0f
                            }
                            "Tw" -> {
                                wordSpacing = (tokens.getOrNull(index - 1) as? COSNumber)?.floatValue() ?: 0f
                            }
                            "Tf" -> {
                                val fontName = tokens.getOrNull(index - 2) as? COSName
                                val fontSize = tokens.getOrNull(index - 1) as? COSNumber
                                activeFont = fontName?.let { page.resources?.getFont(it) }
                                activeFontSize = fontSize?.floatValue() ?: 0f
                            }
                            "Tj" -> {
                                val operandIndex = index - 1
                                val string = tokens.getOrNull(operandIndex) as? COSString
                                if (string != null) {
                                    replaceOperand(string.string)?.let { tokens[operandIndex] = it }
                                }
                            }
                            "TJ" -> {
                                val operandIndex = index - 1
                                val array = tokens.getOrNull(operandIndex) as? COSArray
                                if (array != null) {
                                    var itemIndex = 0
                                    while (itemIndex < array.size()) {
                                        if (array.getObject(itemIndex) !is COSString) {
                                            itemIndex++
                                            continue
                                        }
                                        val groupStart = itemIndex
                                        val combined = StringBuilder()
                                        while (itemIndex < array.size()) {
                                            val part = array.getObject(itemIndex) as? COSString ?: break
                                            combined.append(part.string)
                                            itemIndex++
                                        }
                                        val updated = replaceOperand(combined.toString())
                                        if (updated != null) {
                                            // Adjacent strings have no TJ numeric kerning between them.
                                            // Store the replacement in the first slot and leave empty
                                            // strings in the remaining slots to preserve array indexing.
                                            array.set(groupStart, updated)
                                            for (emptyIndex in groupStart + 1 until itemIndex) {
                                                array.set(emptyIndex, COSString(ByteArray(0)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                require(replacements == 1) {
                    when {
                        replacements > 1 -> "The selected text appears in multiple PDF text objects. Edit was cancelled to avoid changing the wrong text."
                        else -> "The selected text could not be found as an editable native PDF text object. It may be scanned, outlined, encoded with a custom font, or split across PDF text runs."
                    }
                }

                val rewritten = PDStream(document)
                rewritten.createOutputStream(COSName.FLATE_DECODE).use { stream ->
                    ContentStreamWriter(stream).writeTokens(tokens)
                }
                page.setContents(rewritten)
                }
                document.save(output)
            }
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            source.delete()
        }
    }

    private fun containsBengali(value: String): Boolean =
        value.any { it.code in 0x0980..0x09FF }

    private fun isSupportedSimpleText(value: String): Boolean {
        if (value.isEmpty()) return false
        var offset = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            val supported = codePoint in 0x20..0x7E ||
                codePoint in 0x00A0..0x024F ||
                codePoint in 0x2000..0x206F
            if (!supported) return false
            offset += Character.charCount(codePoint)
        }
        return true
    }

    private fun createOutputFile(fileName: String): File {
        val directory = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
        val base = fileName.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .removeSuffix(".pdf").ifBlank { "Native_Text_Edited" }
        var candidate = File(directory, "$base.pdf")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base($suffix).pdf")
            suffix++
        }
        return candidate
    }
}
