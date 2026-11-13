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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Conservative native PDF text replacement.
 *
 * This edits the original Tj/TJ string operand in the page content stream. It deliberately
 * supports only a unique, printable-ASCII match contained in one text-show string, encoded
 * by the active page font, with exactly the same glyph advance width and character count.
 * Unsupported layouts are rejected instead of being covered with a white rectangle or
 * silently shifting the rest of the page.
 *
 * Form XObjects, Type 3 fonts, text split across multiple TJ strings, scanned pages,
 * subset/custom encodings, and complex-script shaping are not supported by this first slice.
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
        require(oldText.length == newText.length) {
            "This safe edit currently requires the replacement to have the same number of characters."
        }
        require(isSupportedSimpleText(oldText) && isSupportedSimpleText(newText)) {
            "This first native-edit mode supports printable English/ASCII text only. Bengali and Arabic support is being developed separately."
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
                val parser = PDFStreamParser(page)
                parser.parse()
                val tokens = parser.tokens
                var activeFont: PDFont? = null
                var activeFontSize = 0f
                var replacements = 0

                fun replaceOperand(original: COSString): COSString {
                    val decoded = original.string
                    val first = decoded.indexOf(oldText)
                    if (first < 0) return original
                    val font = activeFont
                        ?: throw IllegalArgumentException("The selected text has no supported active PDF font.")
                    require(activeFontSize > 0f) { "The selected text has an invalid font size." }
                    require(decoded.indexOf(oldText, first + oldText.length) < 0) {
                        "The selected text occurs more than once in one PDF text object. Select a more specific phrase."
                    }
                    val originalWidth = font.getStringWidth(oldText)
                    val replacementWidth = font.getStringWidth(newText)
                    require(kotlin.math.abs(originalWidth - replacementWidth) <= 0.01f) {
                        "The replacement has a different width in the original font. Layout-preserving edit was cancelled."
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
                                    tokens[operandIndex] = replaceOperand(string)
                                }
                            }
                            "TJ" -> {
                                val operandIndex = index - 1
                                val array = tokens.getOrNull(operandIndex) as? COSArray
                                if (array != null) {
                                    for (itemIndex in 0 until array.size()) {
                                        val string = array.getObject(itemIndex) as? COSString ?: continue
                                        val updated = replaceOperand(string)
                                        if (updated !== string) array.set(itemIndex, updated)
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

    private fun isSupportedSimpleText(value: String): Boolean =
        value.all { it.code in 0x20..0x7E }

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
