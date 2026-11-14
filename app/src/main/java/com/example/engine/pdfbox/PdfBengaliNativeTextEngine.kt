package com.example.engine.pdfbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType3Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.DeflaterOutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * Replaces one complete Bengali Tj run in-place with a Type 3 text glyph.
 * The glyph uses a high-resolution 1-bit stencil rendered by Android's Bengali
 * shaper, while the Type 3 font's ToUnicode map preserves searchable/selectable
 * Unicode text. No page redaction, page rasterization, or page-level image overlay
 * is used. One Tj run is required so its original text matrix can be preserved.
 */
internal class PdfBengaliNativeTextEngine(private val context: Context) {

    fun replace(
        document: PDDocument,
        page: PDPage,
        oldText: String,
        newText: String
    ) {
        require(oldText.any { it.code in 0x0980..0x09FF } &&
            newText.any { it.code in 0x0980..0x09FF }) {
            "Both the selected text and replacement must contain Bengali characters."
        }
        require(newText.none { it.code in 0x0600..0x06FF }) {
            "Bengali native editing does not include Arabic shaping."
        }

        val parser = PDFStreamParser(page)
        parser.parse()
        val sourceTokens = parser.tokens
        val rewritten = ArrayList<Any>(sourceTokens.size + 8)
        var activeFont: PDFont? = null
        var activeFontName: COSName? = null
        var activeFontSize = 0f
        var characterSpacing = 0f
        var horizontalScale = 100f
        var textRenderingMode = 0
        var replacements = 0

        for (index in sourceTokens.indices) {
            val token = sourceTokens[index]
            if (token !is Operator) {
                rewritten.add(token)
                continue
            }

            when (token.name) {
                "Tf" -> {
                    activeFontName = sourceTokens.getOrNull(index - 2) as? COSName
                    activeFontSize = (sourceTokens.getOrNull(index - 1) as? com.tom_roush.pdfbox.cos.COSNumber)
                        ?.floatValue() ?: 0f
                    activeFont = activeFontName?.let { page.resources?.getFont(it) }
                    rewritten.add(token)
                }
                "Tc" -> {
                    characterSpacing = (sourceTokens.getOrNull(index - 1) as? com.tom_roush.pdfbox.cos.COSNumber)
                        ?.floatValue() ?: 0f
                    rewritten.add(token)
                }
                "Tz" -> {
                    horizontalScale = (sourceTokens.getOrNull(index - 1) as? com.tom_roush.pdfbox.cos.COSNumber)
                        ?.floatValue() ?: 100f
                    rewritten.add(token)
                }
                "Tr" -> {
                    textRenderingMode = (sourceTokens.getOrNull(index - 1) as? com.tom_roush.pdfbox.cos.COSNumber)
                        ?.intValue() ?: 0
                    rewritten.add(token)
                }
                "Tj" -> {
                    val operand = sourceTokens.getOrNull(index - 1) as? COSString
                    val font = activeFont
                    if (operand == null || font == null) {
                        rewritten.add(token)
                        continue
                    }
                    if (font !is PDType0Font) {
                        rewritten.add(token)
                        continue
                    }
                    val decoded = try { decode(operand, font) } catch (_: Exception) { null }
                    if (decoded == null) {
                        rewritten.add(token)
                        continue
                    }
                    if (decoded.text != oldText) {
                        if (decoded.text.contains(oldText)) {
                            throw IllegalArgumentException(
                                "Select the complete Bengali text run. Editing only part of a PDF Tj run is not supported."
                            )
                        }
                        rewritten.add(token)
                        continue
                    }
                    replacements++
                    require(replacements == 1) {
                        "The selected Bengali text appears in more than one PDF text run. Edit was cancelled."
                    }
                    require(font is PDType0Font && font.isEmbedded) {
                        "Bengali native editing requires an embedded Unicode Type 0 font with a ToUnicode map."
                    }
                    val originalFontName = activeFontName
                        ?: throw IllegalArgumentException("The selected Bengali text has no valid active font state.")
                    require(activeFontSize > 0f) {
                        "The selected Bengali text has no valid active font size."
                    }
                    require(textRenderingMode == 0) {
                        "This Bengali text uses a non-fill text rendering mode. Native replacement was cancelled."
                    }
                    require(horizontalScale.isFinite() && horizontalScale > 0f) {
                        "The selected Bengali text has an unsupported horizontal text scale."
                    }

                    val originalWidth = decoded.codes.sumOf { code -> font.getWidth(code).toDouble() }.toFloat()
                    val targetAdvance = originalWidth * activeFontSize / 1000f +
                        characterSpacing * (decoded.codes.size - 1)
                    require(targetAdvance > 0f) {
                        "The selected Bengali text has no positive horizontal advance."
                    }

                    val type3Font = createType3Font(document, newText, activeFontSize, targetAdvance)
                    val resources = page.resources ?: PDResources().also { page.resources = it }
                    val resourceName = uniqueFontName(resources)
                    resources.put(resourceName, type3Font)

                    // The original string was appended one token earlier. Replace it and its Tj
                    // operator with the new Type 3 text glyph, then restore the original font.
                    check(rewritten.isNotEmpty() && rewritten.last() is COSString) {
                        "The selected Bengali text stream could not be safely rewritten."
                    }
                    rewritten.removeAt(rewritten.lastIndex)
                    rewritten.add(resourceName)
                    rewritten.add(COSFloat(activeFontSize))
                    rewritten.add(Operator.getOperator("Tf"))
                    rewritten.add(COSString(byteArrayOf(1)))
                    rewritten.add(Operator.getOperator("Tj"))
                    rewritten.add(originalFontName)
                    rewritten.add(COSFloat(activeFontSize))
                    rewritten.add(Operator.getOperator("Tf"))
                }
                "TJ" -> {
                    val array = sourceTokens.getOrNull(index - 1) as? COSArray
                    if (array != null) {
                        val font = activeFont
                        if (font is PDType0Font) {
                            val combined = buildString {
                                for (itemIndex in 0 until array.size()) {
                                    val part = array.getObject(itemIndex) as? COSString ?: continue
                                    val decoded = try { decode(part, font) } catch (_: Exception) { null }
                                    if (decoded != null) append(decoded.text)
                                }
                            }
                            require(!combined.contains(oldText)) {
                                "This Bengali text is split across a TJ array. Select a single Tj text run for native editing."
                            }
                        }
                    }
                    rewritten.add(token)
                }
                else -> rewritten.add(token)
            }
        }

        require(replacements == 1) {
            "The selected Bengali text was not found as one editable Tj run with an embedded Unicode font. Scanned, outlined, custom-encoded and split text runs are not supported."
        }

        val output = PDStream(document)
        output.createOutputStream(COSName.FLATE_DECODE).use { stream ->
            ContentStreamWriter(stream).writeTokens(rewritten)
        }
        page.setContents(output)
    }

    private data class DecodedString(val text: String, val codes: List<Int>)

    private fun decode(value: COSString, font: PDFont): DecodedString {
        val input = ByteArrayInputStream(value.bytes)
        val text = StringBuilder()
        val codes = ArrayList<Int>()
        while (input.available() > 0) {
            val code = font.readCode(input)
            val unicode = font.toUnicode(code)
                ?: throw IllegalArgumentException(
                    "The selected PDF font has no Unicode mapping for one or more Bengali glyphs."
                )
            text.append(unicode)
            codes.add(code)
        }
        return DecodedString(text.toString(), codes)
    }

    private fun uniqueFontName(resources: PDResources): COSName {
        var index = 1
        while (true) {
            val candidate = COSName.getPDFName("HabibBengaliText$index")
            if (resources.getFont(candidate) == null) return candidate
            index++
        }
    }

    private fun createType3Font(
        document: PDDocument,
        text: String,
        fontSize: Float,
        targetAdvance: Float
    ): PDType3Font {
        val typeface = Typeface.createFromAsset(
            context.assets,
            "fonts/NotoSansBengali[wdth,wght].ttf"
        )
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            textSize = 1000f
            this.typeface = typeface
        }
        val measuredWidth = paint.measureText(text)
        require(measuredWidth.isFinite() && measuredWidth > 0f) {
            "The replacement Bengali text has no visible glyph advance."
        }
        val padding = 16
        val layoutWidth = ceil(measuredWidth).toInt() + padding * 2
        require(layoutWidth <= 12000) { "The replacement Bengali text run is too long for one native glyph." }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, layoutWidth - padding * 2)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .build()
        require(layout.lineCount == 1) {
            "The replacement Bengali text is too long for one line. Split it into separate PDF text runs."
        }
        val bitmapHeight = layout.height.coerceAtLeast(1)
        require(bitmapHeight <= 3000 && layoutWidth.toLong() * bitmapHeight <= 18_000_000L) {
            "The replacement Bengali glyph exceeds the supported bitmap size."
        }
        val bitmap = Bitmap.createBitmap(layoutWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.save()
            canvas.translate(padding.toFloat(), 0f)
            layout.draw(canvas)
            canvas.restore()

            val glyphWidth = max(measuredWidth, layout.getLineWidth(0))
            val fontMatrixX = targetAdvance / (glyphWidth * fontSize)
            require(fontMatrixX.isFinite() && fontMatrixX in 0.00005f..0.01f) {
                "The replacement Bengali text would need excessive horizontal distortion to preserve the original layout."
            }
            val baseline = layout.getLineBaseline(0).toFloat()
            val yOffset = baseline - bitmapHeight.toFloat()

            val rowBytes = (bitmap.width + 7) / 8
            val mask = ByteArray(rowBytes * bitmap.height) { 0xFF.toByte() }
            val rowPixels = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(rowPixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (x in 0 until bitmap.width) {
                    if (Color.alpha(rowPixels[x]) >= 96) {
                        val byteIndex = y * rowBytes + x / 8
                        val bit = 0x80 ushr (x % 8)
                        mask[byteIndex] = (mask[byteIndex].toInt() and bit.inv()).toByte()
                    }
                }
            }
            val compressedMask = ByteArrayOutputStream().use { bytes ->
                DeflaterOutputStream(bytes).use { it.write(mask) }
                bytes.toByteArray()
            }
            val maskImage = PDImageXObject(
                document,
                ByteArrayInputStream(compressedMask),
                COSName.FLATE_DECODE,
                bitmap.width,
                bitmap.height,
                1,
                PDDeviceGray.INSTANCE
            ).apply {
                setStencil(true)
                setInterpolate(true)
                val decode = COSArray().apply {
                    add(COSInteger.get(0))
                    add(COSInteger.get(1))
                }
                setDecode(decode)
                cosObject.removeItem(COSName.getPDFName("ColorSpace"))
            }

            val fontResources = PDResources().apply {
                put(COSName.getPDFName("Im0"), maskImage)
            }
            val charProcText = buildString {
                append("${glyphWidth} 0 d0\n")
                append("q\n")
                append("${bitmap.width} 0 0 ${bitmap.height} ${-padding} ${yOffset} cm\n")
                append("/Im0 Do\nQ\n")
            }
            val charProc = PDStream(document)
            charProc.createOutputStream(COSName.FLATE_DECODE).use { stream ->
                stream.write(charProcText.toByteArray(Charsets.US_ASCII))
            }
            val charProcs = COSDictionary().apply {
                setItem(COSName.getPDFName("g1"), charProc.cosObject)
            }

            val differences = COSArray().apply {
                add(COSInteger.get(1))
                add(COSName.getPDFName("g1"))
            }
            val encoding = COSDictionary().apply {
                setItem(COSName.getPDFName("Type"), COSName.getPDFName("Encoding"))
                setItem(COSName.getPDFName("BaseEncoding"), COSName.getPDFName("WinAnsiEncoding"))
                setItem(COSName.getPDFName("Differences"), differences)
            }

            val fontBBox = COSArray().apply {
                add(COSFloat(-padding.toFloat()))
                add(COSFloat(yOffset))
                add(COSFloat((bitmap.width - padding).toFloat()))
                add(COSFloat(baseline))
            }
            val fontMatrix = COSArray().apply {
                add(COSFloat(fontMatrixX))
                add(COSFloat(0f))
                add(COSFloat(0f))
                add(COSFloat(0.001f))
                add(COSFloat(0f))
                add(COSFloat(0f))
            }
            val widths = COSArray().apply { add(COSFloat(glyphWidth)) }

            val unicodeHex = "FEFF" + text.toByteArray(Charsets.UTF_16BE)
                .joinToString("") { byte -> "%02X".format(Locale.ROOT, byte.toInt() and 0xFF) }
            val cmap = """
                /CIDInit /ProcSet findresource begin
                12 dict begin
                begincmap
                /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
                /CMapName /HabibBengaliToUnicode def
                /CMapType 2 def
                1 begincodespacerange
                <00> <FF>
                endcodespacerange
                1 beginbfchar
                <01> <$unicodeHex>
                endbfchar
                endcmap
                CMapName currentdict /CMap defineresource pop
                end
                end
            """.trimIndent()
            val toUnicode = PDStream(document)
            toUnicode.createOutputStream(COSName.FLATE_DECODE).use { stream ->
                stream.write(cmap.toByteArray(Charsets.US_ASCII))
            }

            val fontDictionary = COSDictionary().apply {
                setItem(COSName.getPDFName("Type"), COSName.getPDFName("Font"))
                setItem(COSName.getPDFName("Subtype"), COSName.getPDFName("Type3"))
                setItem(COSName.getPDFName("Name"), COSName.getPDFName("HabibBengaliGlyph"))
                setItem(COSName.getPDFName("FontBBox"), fontBBox)
                setItem(COSName.getPDFName("FontMatrix"), fontMatrix)
                setInt(COSName.getPDFName("FirstChar"), 1)
                setInt(COSName.getPDFName("LastChar"), 1)
                setItem(COSName.getPDFName("Widths"), widths)
                setItem(COSName.getPDFName("CharProcs"), charProcs)
                setItem(COSName.getPDFName("Encoding"), encoding)
                setItem(COSName.getPDFName("Resources"), fontResources.cosObject)
                setItem(COSName.getPDFName("ToUnicode"), toUnicode.cosObject)
            }
            return PDType3Font(fontDictionary)
        } finally {
            bitmap.recycle()
        }
    }
}
