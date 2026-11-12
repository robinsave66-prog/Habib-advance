package com.example.feature.pdfeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.engine.editor.EditorElement
import com.example.engine.editor.ImageElement
import com.example.engine.editor.ShapeElement
import com.example.engine.editor.ShapeType
import com.example.engine.editor.SignatureElement
import com.example.engine.editor.TextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfExportEngine {

    /**
     * Renders each original PDF page and flattens all added vector elements (text, shapes, pen, signatures, images)
     * using exact 72-DPI PDF page coordinate geometry.
     * Preserves original dimensions and exports to a new standalone PDF file without modifying the source.
     */
    suspend fun exportPdfWithEdits(
        context: Context,
        sourceUri: Uri,
        pageCount: Int,
        elementsByPage: Map<Int, List<EditorElement>>,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        val docsDir = File(context.filesDir, "documents")
        if (!docsDir.exists()) docsDir.mkdirs()

        val safeName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
            outputFileName.trim().replace("\\s+".toRegex(), "_")
        } else {
            "${outputFileName.trim().replace("\\s+".toRegex(), "_")}.pdf"
        }
        val targetFile = File(docsDir, safeName)

        // Read source into temporary file descriptor for PdfRenderer
        val tempSource = File(context.cacheDir, "source_export_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(tempSource).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open source PDF stream")

        val pfd = ParcelFileDescriptor.open(tempSource, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val exportedDoc = PdfDocument()

        val textPaint = Paint().apply { isAntiAlias = true }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
        }
        val fillPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val totalPages = minOf(pageCount, renderer.pageCount)

        for (pageIndex in 0 until totalPages) {
            val srcPage = renderer.openPage(pageIndex)
            val pageWidth = srcPage.width
            val pageHeight = srcPage.height

            // 1. Render original background page bitmap at 2x supersampling for high vector fidelity
            val renderScale = 2.0f
            val baseBitmap = Bitmap.createBitmap(
                (pageWidth * renderScale).toInt(),
                (pageHeight * renderScale).toInt(),
                Bitmap.Config.ARGB_8888
            )
            baseBitmap.eraseColor(AndroidColor.WHITE)

            val matrix = Matrix()
            matrix.postScale(renderScale, renderScale)
            srcPage.render(baseBitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            srcPage.close()

            // 2. Start new PDF page matching exact original page dimensions
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val destPage = exportedDoc.startPage(pageInfo)
            val canvas = destPage.canvas

            // Draw base rasterized original PDF page
            val destRect = android.graphics.RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
            canvas.drawBitmap(baseBitmap, null, destRect, null)
            baseBitmap.recycle()

            // 3. Draw all user added/edited elements for this page sorted by zIndex
            val pageElements = elementsByPage[pageIndex].orEmpty().sortedBy { it.zIndex }

            for (elem in pageElements) {
                canvas.save()
                canvas.translate(elem.x, elem.y)
                if (elem.rotationDegrees != 0f) {
                    canvas.rotate(elem.rotationDegrees, elem.width / 2f, elem.height / 2f)
                }

                when (elem) {
                    is TextElement -> {
                        // Draw background rectangle if set independently
                        if (elem.backgroundColor != null) {
                            val bgPaint = Paint().apply {
                                color = elem.backgroundColor!!.toInt()
                                style = Paint.Style.FILL
                            }
                            canvas.drawRect(0f, 0f, elem.width, elem.height, bgPaint)
                        }

                        textPaint.apply {
                            color = elem.textColor.toInt()
                            alpha = (elem.opacity * 255).toInt()
                            textSize = elem.fontSizePt
                            isFakeBoldText = elem.isBold
                            textSkewX = if (elem.isItalic) -0.25f else 0f
                            isUnderlineText = elem.isUnderline
                        }

                        val xOffset = when (elem.textAlign) {
                            "CENTER" -> (elem.width - textPaint.measureText(elem.text)) / 2f
                            "RIGHT" -> elem.width - textPaint.measureText(elem.text)
                            else -> 4f
                        }
                        // PDF text baseline offset
                        canvas.drawText(elem.text, xOffset.coerceAtLeast(0f), elem.fontSizePt + 2f, textPaint)
                    }

                    is ShapeElement -> {
                        strokePaint.apply {
                            color = elem.strokeColor.toInt()
                            alpha = (elem.opacity * 255).toInt()
                            strokeWidth = elem.strokeWidthPt
                        }
                        fillPaint.apply {
                            if (elem.fillColor != null) {
                                color = elem.fillColor!!.toInt()
                                alpha = (elem.opacity * 255).toInt()
                            }
                        }

                        when (elem.shapeType) {
                            ShapeType.RECTANGLE -> {
                                if (elem.fillColor != null) {
                                    canvas.drawRect(0f, 0f, elem.width, elem.height, fillPaint)
                                }
                                canvas.drawRect(0f, 0f, elem.width, elem.height, strokePaint)
                            }
                            ShapeType.CIRCLE -> {
                                val radius = minOf(elem.width, elem.height) / 2f
                                if (elem.fillColor != null) {
                                    canvas.drawCircle(radius, radius, radius, fillPaint)
                                }
                                canvas.drawCircle(radius, radius, radius, strokePaint)
                            }
                            ShapeType.LINE -> {
                                canvas.drawLine(0f, 0f, elem.width, elem.height, strokePaint)
                            }
                            ShapeType.ARROW -> {
                                canvas.drawLine(0f, elem.height / 2f, elem.width, elem.height / 2f, strokePaint)
                                val headPath = Path().apply {
                                    moveTo(elem.width - 12f, (elem.height / 2f) - 8f)
                                    lineTo(elem.width, elem.height / 2f)
                                    lineTo(elem.width - 12f, (elem.height / 2f) + 8f)
                                }
                                canvas.drawPath(headPath, strokePaint)
                            }
                            ShapeType.HIGHLIGHT_BOX -> {
                                val hlPaint = Paint().apply {
                                    color = elem.strokeColor.toInt()
                                    alpha = 80 // Semi-transparent yellow or custom highlight
                                    style = Paint.Style.FILL
                                }
                                canvas.drawRect(0f, 0f, elem.width, elem.height, hlPaint)
                            }
                        }
                    }

                    is SignatureElement -> {
                        val inkPaint = Paint().apply {
                            isAntiAlias = true
                            color = elem.inkColor.toInt()
                            alpha = (elem.opacity * 255).toInt()
                            strokeWidth = elem.inkWidthPt
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }

                        if (elem.pathPoints.size > 1) {
                            val path = Path()
                            val first = elem.pathPoints[0]
                            path.moveTo(first.first, first.second)
                            for (i in 1 until elem.pathPoints.size) {
                                val pt = elem.pathPoints[i]
                                path.lineTo(pt.first, pt.second)
                            }
                            canvas.drawPath(path, inkPaint)
                        }
                    }

                    is ImageElement -> {
                        try {
                            val stream = context.contentResolver.openInputStream(Uri.parse(elem.imageUri))
                            val imgBitmap = BitmapFactory.decodeStream(stream)
                            stream?.close()
                            if (imgBitmap != null) {
                                val imgRect = android.graphics.RectF(0f, 0f, elem.width, elem.height)
                                val imgPaint = Paint().apply {
                                    alpha = (elem.opacity * 255).toInt()
                                }
                                canvas.drawBitmap(imgBitmap, null, imgRect, imgPaint)
                                imgBitmap.recycle()
                            }
                        } catch (_: Exception) {}
                    }
                }

                canvas.restore()
            }

            exportedDoc.finishPage(destPage)
        }

        renderer.close()
        pfd.close()
        tempSource.delete()

        FileOutputStream(targetFile).use { out ->
            exportedDoc.writeTo(out)
        }
        exportedDoc.close()

        targetFile
    }
}
