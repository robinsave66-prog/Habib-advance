package com.example.engine.pdfbox

import android.content.Context
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
import java.io.File
import java.io.FileOutputStream

/**
 * Appends editor objects to the original PDF page content streams.
 * Existing PDF page content is retained; this is not arbitrary inline text-object editing.
 */
class PdfBoxOverlayExportEngine(private val context: Context) {
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
                                drawElement(document, stream, page.mediaBox.height, element)
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
                is TextElement -> drawText(stream, element)
                is ShapeElement -> drawShape(stream, element)
                is SignatureElement -> drawSignature(stream, element)
                is ImageElement -> drawImage(document, stream, element)
            }
        } finally {
            stream.restoreGraphicsState()
        }
    }

    private fun drawText(stream: PDPageContentStream, element: TextElement) {
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
                "The built-in PDF font cannot encode this text. Bengali/Arabic export needs a compatible embedded font.",
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

    private fun drawShape(stream: PDPageContentStream, element: ShapeElement) {
        if (element.fillColor != null || element.shapeType == ShapeType.HIGHLIGHT_BOX) {
            setFillColor(stream, element.fillColor ?: element.strokeColor)
            if (element.shapeType == ShapeType.CIRCLE) {
                val radius = minOf(element.width, element.height) / 2f
                addCircle(stream, radius, radius, radius)
            } else {
                stream.addRect(0f, 0f, element.width, element.height)
            }
            stream.fill()
        }
        if (element.strokeWidthPt <= 0f) return
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
