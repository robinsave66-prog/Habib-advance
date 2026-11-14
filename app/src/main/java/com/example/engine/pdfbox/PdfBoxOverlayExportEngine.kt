package com.example.engine.pdfbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.net.Uri
import com.example.engine.editor.EditorElement
import com.example.engine.editor.ImageElement
import com.example.engine.editor.ShapeElement
import com.example.engine.editor.ShapeType
import com.example.engine.editor.SignatureElement
import com.example.engine.editor.TextElement
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Appends editor objects to the original PDF page content streams.
 * Existing PDF page content is retained; this is not arbitrary inline text-object editing.
 */
class PdfBoxOverlayExportEngine(private val context: Context) {
    // Bundle a deterministic Bengali typeface; do not rely on OEM/system font fallback.
    private val bengaliTypeface: Typeface by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Typeface.createFromAsset(context.assets, "fonts/NotoSansBengali[wdth,wght].ttf")
    }

    suspend fun export(
        sourceUri: Uri,
        elementsByPage: Map<Int, List<EditorElement>>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val source = File(context.cacheDir, "overlay-export-${System.currentTimeMillis()}.pdf")
        val output = createOutputFile(outputFileName)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(source).use { destination -> input.copyTo(destination) }
            } ?: throw IllegalArgumentException("Cannot open the source PDF.")

            PDDocument.load(source).use { document ->
                require(document.numberOfPages > 0) { "The source PDF has no pages." }
                elementsByPage.toSortedMap().forEach { (pageIndex, elements) ->
                    require(pageIndex in 0 until document.numberOfPages) {
                        "An edited object refers to a page that no longer exists."
                    }
                    if (elements.isNotEmpty()) {
                        val page = document.getPage(pageIndex)
                        PDPageContentStream(
                            document, page,
                            PDPageContentStream.AppendMode.APPEND,
                            true, true
                        ).use { stream ->
                            elements.sortedBy { it.zIndex }.forEach { element ->
                                drawElement(
                                    document,
                                    stream,
                                    page.mediaBox.height,
                                    mapRotatedDisplayElementToPdf(element, page.mediaBox.width, page.mediaBox.height, page.rotation)
                                )
                            }
                        }
                    }
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

    /**
     * Editor coordinates follow the page orientation shown by PdfRenderer. PDF content
     * streams use the unrotated media-box coordinate system, so undo the page rotation
     * before appending overlay operators. Page rotation is then applied by the PDF viewer
     * to both the original content and these overlays.
     */
    private fun mapRotatedDisplayElementToPdf(
        element: EditorElement,
        pageWidth: Float,
        pageHeight: Float,
        pageRotation: Int
    ): EditorElement {
        val rotation = ((pageRotation % 360) + 360) % 360
        val mapped = when (rotation) {
            90 -> element
                .copyWithPosition(element.y, pageHeight - element.x - element.width)
                .copyWithSize(element.height, element.width)
            180 -> element
                .copyWithPosition(
                    pageWidth - element.x - element.width,
                    pageHeight - element.y - element.height
                )
            270 -> element
                .copyWithPosition(pageWidth - element.y - element.height, element.x)
                .copyWithSize(element.height, element.width)
            else -> element
        }
        return if (rotation == 0) mapped else mapped.copyWithRotation(element.rotationDegrees - rotation)
    }

    private fun drawElement(
        document: PDDocument,
        stream: PDPageContentStream,
        pageHeight: Float,
        element: EditorElement
    ) {
        stream.saveGraphicsState()
        try {
            stream.transform(Matrix.getTranslateInstance(element.x, pageHeight - element.y - element.height))
            if (element.rotationDegrees != 0f) {
                stream.transform(
                    Matrix.getRotateInstance(
                        Math.toRadians(element.rotationDegrees.toDouble()),
                        element.width / 2f, element.height / 2f
                    )
                )
            }
            val alpha = element.opacity.coerceIn(0f, 1f)
            val state = PDExtendedGraphicsState().apply {
                nonStrokingAlphaConstant = alpha
                strokingAlphaConstant = alpha
            }
            stream.setGraphicsStateParameters(state)
            when (element) {
                is TextElement -> drawText(document, stream, element)
                is ShapeElement -> drawShape(stream, element)
                is SignatureElement -> drawSignature(stream, element)
                is ImageElement -> drawImage(document, stream, element)
            }
        } finally {
            stream.restoreGraphicsState()
        }
    }

    private fun drawText(document: PDDocument, stream: PDPageContentStream, element: TextElement) {
        // Android's text shaper supports Bengali conjuncts and vowel marks. PDFBox's
        // built-in Type 1 fonts do not; render Bengali overlays to a transparent image.
        if (element.text.any { it.code in 0x0980..0x09FF }) {
            drawBengaliTextImage(document, stream, element)
            return
        }
        if (element.backgroundColor != null) {
            setFillColor(stream, element.backgroundColor)
            stream.addRect(0f, 0f, element.width, element.height)
            stream.fill()
        }
        val font = when {
            element.isBold && element.isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            element.isBold -> PDType1Font.HELVETICA_BOLD
            element.isItalic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
        val textWidth = try {
            font.getStringWidth(element.text) / 1000f * element.fontSizePt
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "The built-in PDF font cannot encode this text. Bengali export needs a compatible embedded font.",
                error
            )
        }
        val x = when (element.textAlign.uppercase()) {
            "CENTER" -> ((element.width - textWidth) / 2f).coerceAtLeast(0f)
            "RIGHT" -> (element.width - textWidth).coerceAtLeast(0f)
            else -> 0f
        }
        setFillColor(stream, element.textColor)
        stream.beginText()
        stream.setFont(font, element.fontSizePt.coerceAtLeast(1f))
        stream.newLineAtOffset(x, (element.height - element.fontSizePt).coerceAtLeast(0f))
        try {
            stream.showText(element.text)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "The built-in PDF font cannot encode this text. Bengali/Arabic export needs a compatible embedded font.",
                error
            )
        } finally {
            stream.endText()
        }
        if (element.isUnderline) {
            setStrokeColor(stream, element.textColor)
            stream.setLineWidth((element.fontSizePt / 16f).coerceAtLeast(0.5f))
            val y = (element.height - element.fontSizePt - 2f).coerceAtLeast(0f)
            stream.moveTo(x, y)
            stream.lineTo((x + textWidth).coerceAtMost(element.width), y)
            stream.stroke()
        }
    }



    private fun drawBengaliTextImage(
        document: PDDocument,
        stream: PDPageContentStream,
        element: TextElement
    ) {
        require(element.width > 0f && element.height > 0f) {
            "Bengali text needs a text box with positive width and height."
        }
        val scale = 3
        val bitmapWidth = (element.width * scale).toInt().coerceAtLeast(1)
        val bitmapHeight = (element.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(scale.toFloat(), scale.toFloat())
            element.backgroundColor?.let { background ->
                val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = background.toInt()
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, element.width, element.height, backgroundPaint)
            }

            val typefaceStyle = when {
                element.isBold && element.isItalic -> Typeface.BOLD_ITALIC
                element.isBold -> Typeface.BOLD
                element.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = element.textColor.toInt()
                textSize = element.fontSizePt.coerceAtLeast(1f)
                typeface = Typeface.create(bengaliTypeface, typefaceStyle)
                isUnderlineText = element.isUnderline
            }
            val alignment = when (element.textAlign.uppercase()) {
                "CENTER" -> Layout.Alignment.ALIGN_CENTER
                "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val layout = StaticLayout.Builder
                .obtain(element.text, 0, element.text.length, paint, element.width.toInt().coerceAtLeast(1))
                .setAlignment(alignment)
                .setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .build()
            canvas.save()
            canvas.clipRect(0f, 0f, element.width, element.height)
            layout.draw(canvas)
            canvas.restore()

            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Unable to render Bengali text for PDF export."
                }
                val image = PDImageXObject.createFromByteArray(
                    document, output.toByteArray(), "bengali-text-overlay"
                )
                stream.drawImage(image, 0f, 0f, element.width, element.height)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawShape(stream: PDPageContentStream, element: ShapeElement) {
        if (element.fillColor != null || element.shapeType == ShapeType.HIGHLIGHT_BOX) {
            setFillColor(stream, element.fillColor ?: element.strokeColor)
            if (element.shapeType == ShapeType.HIGHLIGHT_BOX) {
                val highlightState = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = element.opacity.coerceIn(0f, 1f) * 0.35f
                }
                stream.setGraphicsStateParameters(highlightState)
            }
            if (element.shapeType == ShapeType.CIRCLE) {
                val radius = minOf(element.width, element.height) / 2f
                addCircle(stream, radius, radius, radius)
            } else {
                stream.addRect(0f, 0f, element.width, element.height)
            }
            stream.fill()
        }
        if (element.shapeType == ShapeType.HIGHLIGHT_BOX || element.strokeWidthPt <= 0f) return
        setStrokeColor(stream, element.strokeColor)
        stream.setLineWidth(element.strokeWidthPt)
        when (element.shapeType) {
            ShapeType.RECTANGLE, ShapeType.HIGHLIGHT_BOX -> {
                stream.addRect(0f, 0f, element.width, element.height)
                stream.stroke()
            }
            ShapeType.CIRCLE -> {
                val radius = minOf(element.width, element.height) / 2f
                addCircle(stream, radius, radius, radius)
                stream.stroke()
            }
            ShapeType.LINE -> {
                stream.moveTo(0f, element.height)
                stream.lineTo(element.width, 0f)
                stream.stroke()
            }
            ShapeType.ARROW -> {
                val mid = element.height / 2f
                stream.moveTo(0f, mid)
                stream.lineTo(element.width, mid)
                stream.stroke()
                val head = 10f
                stream.moveTo(element.width - head, mid + head * 0.6f)
                stream.lineTo(element.width, mid)
                stream.lineTo(element.width - head, mid - head * 0.6f)
                stream.stroke()
            }
        }
    }

    private fun drawSignature(stream: PDPageContentStream, element: SignatureElement) {
        if (element.pathPoints.size < 2) return
        setStrokeColor(stream, element.inkColor)
        stream.setLineWidth(element.inkWidthPt.coerceAtLeast(0.5f))
        stream.setLineCapStyle(1)
        stream.setLineJoinStyle(1)
        val first = element.pathPoints.first()
        stream.moveTo(first.first, element.height - first.second)
        element.pathPoints.drop(1).forEach { point ->
            stream.lineTo(point.first, element.height - point.second)
        }
        stream.stroke()
    }

    private fun drawImage(document: PDDocument, stream: PDPageContentStream, element: ImageElement) {
        val bytes = context.contentResolver.openInputStream(Uri.parse(element.imageUri))?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Cannot read the inserted image.")
        val image = PDImageXObject.createFromByteArray(document, bytes, "editor-image")
        stream.drawImage(image, 0f, 0f, element.width, element.height)
    }

    private fun addCircle(stream: PDPageContentStream, cx: Float, cy: Float, radius: Float) {
        val k = 0.55228475f * radius
        stream.moveTo(cx + radius, cy)
        stream.curveTo(cx + radius, cy + k, cx + k, cy + radius, cx, cy + radius)
        stream.curveTo(cx - k, cy + radius, cx - radius, cy + k, cx - radius, cy)
        stream.curveTo(cx - radius, cy - k, cx - k, cy - radius, cx, cy - radius)
        stream.curveTo(cx + k, cy - radius, cx + radius, cy - k, cx + radius, cy)
        stream.closePath()
    }

    private fun setFillColor(stream: PDPageContentStream, argb: Long) {
        stream.setNonStrokingColor(
            ((argb shr 16) and 0xFF).toInt(),
            ((argb shr 8) and 0xFF).toInt(),
            (argb and 0xFF).toInt()
        )
    }

    private fun setStrokeColor(stream: PDPageContentStream, argb: Long) {
        stream.setStrokingColor(
            ((argb shr 16) and 0xFF).toInt(),
            ((argb shr 8) and 0xFF).toInt(),
            (argb and 0xFF).toInt()
        )
    }

    private fun createOutputFile(fileName: String): File {
        val directory = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
        val base = fileName.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .removeSuffix(".pdf").ifBlank { "Edited_Document" }
        var candidate = File(directory, "$base.pdf")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base($suffix).pdf")
            suffix++
        }
        return candidate
    }
}
