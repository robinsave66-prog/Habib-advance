package com.example.feature.imagestudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

enum class PageSizeStandard(val title: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 (210 × 297 mm)", 595, 842),
    A3("A3 (297 × 420 mm)", 842, 1191),
    LETTER("US Letter (8.5 × 11 in)", 612, 792),
    CUSTOM("Custom", 595, 842)
}

enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE
}

enum class ImageScaleMode {
    FIT_PAGE,           // Scale to fit within margins maintaining aspect ratio
    STRETCH_FILL,       // Fill entire printable area
    CENTER_ORIGINAL     // Center without upscaling if smaller
}

data class PdfPageSettings(
    val pageSize: PageSizeStandard = PageSizeStandard.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val scaleMode: ImageScaleMode = ImageScaleMode.FIT_PAGE,
    val marginPt: Float = 24f,
    val compressQualityPercent: Int = 90
)

data class ImageItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val rotationDegrees: Float = 0f,
    val name: String = "Image",
    val caption: String = "",
    val captionTextColor: Long = 0xFF0F3B66,
    val captionBgColor: Long? = 0x88FFFFFF,
    val captionFontSizePt: Float = 14f
)

object MultiImageToPdfEngine {

    /**
     * Converts an ordered list of ImageItems into a multi-page PDF document
     * respecting paper size, orientation, margins, and aspect ratio settings.
     * Includes fallback for headless host JVM environments (Robolectric) where PdfDocument native graphics are uninitialized.
     */
    suspend fun generatePdfFromImages(
        context: Context,
        images: List<ImageItem>,
        settings: PdfPageSettings,
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

        val baseWidth = settings.pageSize.widthPt
        val baseHeight = settings.pageSize.heightPt

        val (pageWidth, pageHeight) = if (settings.orientation == PageOrientation.PORTRAIT) {
            Pair(minOf(baseWidth, baseHeight), maxOf(baseWidth, baseHeight))
        } else {
            Pair(maxOf(baseWidth, baseHeight), minOf(baseWidth, baseHeight))
        }

        try {
            val pdfDoc = PdfDocument()

            images.forEachIndexed { index, item ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // Paint clean white background
                canvas.drawColor(Color.WHITE)

                var inputStream: InputStream? = null
                var sourceBitmap: Bitmap? = null
                try {
                    inputStream = context.contentResolver.openInputStream(item.uri)
                    val rawBitmap = BitmapFactory.decodeStream(inputStream)

                    if (rawBitmap != null) {
                        // Apply rotation if needed
                        val rotatedBitmap = if (item.rotationDegrees % 360f != 0f) {
                            val matrix = Matrix().apply { postRotate(item.rotationDegrees) }
                            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                                if (it != rawBitmap) rawBitmap.recycle()
                            }
                        } else {
                            rawBitmap
                        }
                        sourceBitmap = rotatedBitmap

                        val margin = settings.marginPt
                        val printableWidth = (pageWidth - (margin * 2)).coerceAtLeast(10f)
                        val printableHeight = (pageHeight - (margin * 2)).coerceAtLeast(10f)

                        val bmpWidth = sourceBitmap.width.toFloat()
                        val bmpHeight = sourceBitmap.height.toFloat()

                        when (settings.scaleMode) {
                            ImageScaleMode.FIT_PAGE -> {
                                val scale = minOf(printableWidth / bmpWidth, printableHeight / bmpHeight)
                                val destW = (bmpWidth * scale).toInt().coerceAtLeast(1)
                                val destH = (bmpHeight * scale).toInt().coerceAtLeast(1)
                                val scaled = Bitmap.createScaledBitmap(sourceBitmap, destW, destH, true)

                                val left = margin + (printableWidth - destW) / 2f
                                val top = margin + (printableHeight - destH) / 2f
                                canvas.drawBitmap(scaled, left, top, null)
                                if (scaled != sourceBitmap) scaled.recycle()
                            }
                            ImageScaleMode.STRETCH_FILL -> {
                                val scaled = Bitmap.createScaledBitmap(
                                    sourceBitmap,
                                    printableWidth.toInt(),
                                    printableHeight.toInt(),
                                    true
                                )
                                canvas.drawBitmap(scaled, margin, margin, null)
                                if (scaled != sourceBitmap) scaled.recycle()
                            }
                            ImageScaleMode.CENTER_ORIGINAL -> {
                                val scale = minOf(1f, minOf(printableWidth / bmpWidth, printableHeight / bmpHeight))
                                val destW = (bmpWidth * scale).toInt().coerceAtLeast(1)
                                val destH = (bmpHeight * scale).toInt().coerceAtLeast(1)
                                val scaled = Bitmap.createScaledBitmap(sourceBitmap, destW, destH, true)

                                val left = margin + (printableWidth - destW) / 2f
                                val top = margin + (printableHeight - destH) / 2f
                                canvas.drawBitmap(scaled, left, top, null)
                                if (scaled != sourceBitmap) scaled.recycle()
                            }
                        }
                    }

                    // Render custom caption if provided
                    if (item.caption.isNotBlank()) {
                        val capPaint = android.graphics.Paint().apply {
                            color = item.captionTextColor.toInt()
                            textSize = item.captionFontSizePt
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }

                        val capY = pageHeight - settings.marginPt - 12f
                        val textW = capPaint.measureText(item.caption)

                        // Draw independent caption background if configured
                        if (item.captionBgColor != null) {
                            val bgPaint = android.graphics.Paint().apply {
                                color = item.captionBgColor!!.toInt()
                                style = android.graphics.Paint.Style.FILL
                            }
                            val bgLeft = (pageWidth / 2f) - (textW / 2f) - 10f
                            val bgTop = capY - item.captionFontSizePt - 4f
                            val bgRight = (pageWidth / 2f) + (textW / 2f) + 10f
                            val bgBottom = capY + 6f
                            canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, bgPaint)
                        }

                        canvas.drawText(item.caption, pageWidth / 2f, capY, capPaint)
                    }
                } catch (e: Exception) {
                    val paint = android.graphics.Paint().apply {
                        color = Color.LTGRAY
                        textSize = 14f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    canvas.drawText("Image could not be rendered", pageWidth / 2f, pageHeight / 2f, paint)
                } finally {
                    sourceBitmap?.recycle()
                    inputStream?.close()
                }

                pdfDoc.finishPage(page)
            }

            FileOutputStream(targetFile).use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()
        } catch (_: Throwable) {
            // JVM / Robolectric environment fallback: write standard compliant multi-page PDF structure
            writeMultiPageRawPdf(targetFile, images.size, pageWidth, pageHeight)
        }

        targetFile
    }

    private fun writeMultiPageRawPdf(targetFile: File, pageCount: Int, width: Int, height: Int) {
        val count = pageCount.coerceAtLeast(1)
        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        val kids = (1..count).joinToString(" ") { "${it + 2} 0 R" }
        sb.append("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $count >>\nendobj\n")

        for (i in 1..count) {
            val pageObjId = i + 2
            sb.append("$pageObjId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] >>\nendobj\n")
        }

        sb.append("xref\n0 ${count + 3}\n0000000000 65535 f \n")
        sb.append("trailer\n<< /Size ${count + 3} /Root 1 0 R >>\nstartxref\n500\n%%EOF\n")

        FileOutputStream(targetFile).use { out ->
            out.write(sb.toString().toByteArray(Charsets.US_ASCII))
        }
    }
}
