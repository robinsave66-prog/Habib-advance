package com.example.engine.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.engine.core.IPdfRenderer
import com.example.engine.core.PdfDocumentInfo
import com.example.engine.core.PdfLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AndroidNativePdfRenderer(private val context: Context) : IPdfRenderer {

    private val mutex = Mutex()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var tempFile: File? = null

    // Cache rendered page bitmaps to ensure 60fps scrolling without re-rendering every frame
    // Uses up to 25% of max app memory
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 4
    private val pageBitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    override suspend fun openDocument(uri: Uri): PdfLoadResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeInternal()
            try {
                // If it's a content URI, copy safely to a temporary cache file to obtain a seekable FileDescriptor
                val fileDescriptor = if (uri.scheme == "file") {
                    val f = File(uri.path ?: "")
                    if (!f.exists() || f.length() == 0L) {
                        return@withContext PdfLoadResult.Corrupted("File does not exist or is empty")
                    }
                    ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext PdfLoadResult.Corrupted("Cannot access file stream")
                    val temp = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}.pdf")
                    tempFile = temp
                    FileOutputStream(temp).use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                    if (temp.length() == 0L) {
                        return@withContext PdfLoadResult.Corrupted("File is empty (0 bytes)")
                    }
                    ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                }

                pfd = fileDescriptor
                val pdfRenderer = try {
                    PdfRenderer(fileDescriptor)
                } catch (e: SecurityException) {
                    return@withContext PdfLoadResult.PasswordRequired("Password protected PDF: ${e.message}")
                } catch (e: Exception) {
                    return@withContext PdfLoadResult.Corrupted("Unable to parse PDF: ${e.localizedMessage}")
                }

                renderer = pdfRenderer
                val pageCount = pdfRenderer.pageCount
                val fileSize = tempFile?.length() ?: 0L

                return@withContext PdfLoadResult.Success(
                    PdfDocumentInfo(
                        uri = uri,
                        title = uri.lastPathSegment ?: "Document",
                        pageCount = pageCount,
                        fileSize = fileSize
                    )
                )
            } catch (e: Exception) {
                return@withContext PdfLoadResult.Error(e)
            }
        }
    }

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val cacheKey = "${pageIndex}_${targetWidth}_${targetHeight}"
            synchronized(pageBitmapCache) {
                val cached = pageBitmapCache.get(cacheKey)
                if (cached != null && !cached.isRecycled) {
                    return@withContext cached
                }
            }

            mutex.withLock {
                val r = renderer ?: return@withContext null
                if (pageIndex < 0 || pageIndex >= r.pageCount) return@withContext null

                try {
                    val page = r.openPage(pageIndex)
                    val originalWidth = page.width
                    val originalHeight = page.height

                    // Calculate scale factor while preserving aspect ratio
                    val scale = if (targetWidth > 0 && targetHeight > 0) {
                        minOf(targetWidth.toFloat() / originalWidth, targetHeight.toFloat() / originalHeight)
                    } else if (targetWidth > 0) {
                        targetWidth.toFloat() / originalWidth
                    } else {
                        1.5f
                    }

                    val renderWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
                    val renderHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE) // Blank white page background

                    val matrix = Matrix()
                    matrix.postScale(scale, scale)

                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    synchronized(pageBitmapCache) {
                        pageBitmapCache.put(cacheKey, bitmap)
                    }

                    return@withContext bitmap
                } catch (e: Exception) {
                    return@withContext null
                }
            }
        }

    override fun getPageCount(): Int {
        return renderer?.pageCount ?: 0
    }

    override fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? {
        val r = renderer ?: return null
        if (pageIndex < 0 || pageIndex >= r.pageCount) return null
        return try {
            val page = r.openPage(pageIndex)
            val dims = Pair(page.width, page.height)
            page.close()
            dims
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        try {
            renderer?.close()
        } catch (_: Exception) {}
        renderer = null

        try {
            pfd?.close()
        } catch (_: Exception) {}
        pfd = null

        try {
            tempFile?.delete()
        } catch (_: Exception) {}
        tempFile = null

        synchronized(pageBitmapCache) {
            pageBitmapCache.evictAll()
        }
    }
}
