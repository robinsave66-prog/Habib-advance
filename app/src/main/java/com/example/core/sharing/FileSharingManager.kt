package com.example.core.sharing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FileSharingManager {

    fun getMimeTypeForFile(fileOrName: String): String {
        val lower = fileOrName.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lower.endsWith(".doc") -> "application/msword"
            lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            lower.endsWith(".xls") -> "application/vnd.ms-excel"
            lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
            lower.endsWith(".txt") -> "text/plain"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> "*/*"
        }
    }

    /**
     * Share a document file via Android Sharesheet and FileProvider.
     */
    fun shareDocument(
        context: Context,
        file: File,
        mimeType: String? = null
    ) {
        val resolvedMime = mimeType ?: getMimeTypeForFile(file.name)
        val shareUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
        shareUriInternal(context, shareUri, file.name, resolvedMime)
    }

    /**
     * Share a document URI (file:// or content://).
     */
    fun shareDocument(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String? = null
    ) {
        val resolvedMime = mimeType ?: getMimeTypeForFile(fileName)
        val shareUri: Uri = try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            } else if (uri.scheme == "content" && !uri.authority.orEmpty().contains("fileprovider")) {
                copyToCacheAndGetShareUri(context, uri, fileName)
            } else {
                uri
            }
        } catch (_: Exception) {
            uri
        }

        shareUriInternal(context, shareUri, fileName, resolvedMime)
    }

    private fun shareUriInternal(
        context: Context,
        shareUri: Uri,
        fileName: String,
        mimeType: String
    ) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
            }

            val chooser = Intent.createChooser(shareIntent, "Share \"$fileName\" via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open an exported document using an external compatible application via ACTION_VIEW.
     */
    fun openDocumentWithExternalApp(
        context: Context,
        file: File,
        mimeType: String? = null
    ) {
        val resolvedMime = mimeType ?: getMimeTypeForFile(file.name)
        try {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No compatible viewer app found for ${file.extension.uppercase()} on this device. You can save or share it.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open a document URI using an external compatible application via ACTION_VIEW.
     */
    fun openDocumentWithExternalApp(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String? = null
    ) {
        val resolvedMime = mimeType ?: getMimeTypeForFile(fileName)
        val viewUri = if (uri.scheme == "content") {
            uri
        } else {
            try {
                val f = File(uri.path ?: "")
                if (f.exists()) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f) else uri
            } catch (_: Exception) {
                uri
            }
        }
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(viewUri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No compatible viewer app found for \"$fileName\" on this device. You can save or share it.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToCacheAndGetShareUri(context: Context, sourceUri: Uri, fileName: String): Uri {
        val shareDir = File(context.cacheDir, "shared_docs")
        if (!shareDir.exists()) shareDir.mkdirs()

        val destFile = File(shareDir, fileName)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
    }
}
