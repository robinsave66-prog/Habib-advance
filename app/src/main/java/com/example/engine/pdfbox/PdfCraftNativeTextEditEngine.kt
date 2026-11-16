package com.example.engine.pdfbox

import android.content.Context
import android.net.Uri
import android.os.Build
import io.github.artcraftandroid.pdfcraft.core.MobileSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Paragraph-level native PDF editing backed by PdfCraft's Rust engine.
 *
 * PdfCraft parses page content (including text-bearing Form XObjects), lays out replacement
 * paragraphs, applies Unicode/font fallback, and writes a real PDF copy. This adapter deliberately
 * keeps the app's existing viewer and selection UI; it translates the selected phrase into the
 * paragraph operation exposed by PdfCraft's Android UniFFI API.
 *
 * PdfCraft's Android native build targets API 29. API 26-28 use the conservative PDFBox fallback.
 */
class PdfCraftNativeTextEditEngine(private val context: Context) {

    suspend fun replaceText(
        sourceUri: Uri,
        pageIndex: Int,
        oldText: String,
        newText: String,
        outputFileName: String
    ): File = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= 29) {
            "The advanced PDF editing engine requires Android 10 or later."
        }
        require(pageIndex >= 0) { "Page index cannot be negative." }
        require(oldText.isNotBlank()) { "Select some existing PDF text first." }
        require(newText.isNotEmpty()) { "Replacement text cannot be empty." }

        val cacheInput = File(context.cacheDir, "pdfcraft-source-${System.nanoTime()}.pdf")
        val outputDirectory = File(context.filesDir, "documents").apply { mkdirs() }
        val safeName = File(outputFileName).name.ifBlank { "PdfCraft_Edit.pdf" }
        val output = File(outputDirectory, safeName)
        output.delete()

        val session = MobileSession()
        var sourceDocumentId: Long? = null
        var verificationDocumentId: Long? = null
        var completed = false
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(cacheInput).use { destination -> input.copyTo(destination) }
            } ?: throw IllegalArgumentException("Cannot open the source PDF.")

            val opened = session.open(cacheInput.absolutePath, null)
            val documentId = opened.id.toLong()
            sourceDocumentId = documentId
            require(pageIndex < opened.pages.size) { "The selected page does not exist." }

            val pageNumber = pageIndex + 1
            val listing = execute(
                session,
                "text_paragraphs",
                JSONObject().put("doc", documentId).put("page", pageNumber)
            )
            val paragraphs = listing.optJSONArray("paragraphs")
                ?: throw IllegalArgumentException("The PDF engine could not identify editable paragraphs on this page.")

            val needle = whitespaceTolerantPattern(oldText)
            val candidates = ArrayList<ParagraphMatch>()
            for (index in 0 until paragraphs.length()) {
                val paragraph = paragraphs.optJSONObject(index) ?: continue
                val paragraphText = paragraph.optString("editor_text")
                    .ifBlank { paragraph.optString("text") }
                if (paragraphText.isBlank()) continue
                val matches = needle.findAll(paragraphText).toList()
                if (matches.isNotEmpty()) {
                    candidates += ParagraphMatch(
                        number = paragraph.optInt("paragraph", index + 1),
                        originalText = paragraphText,
                        match = matches.singleOrNull()
                    )
                }
            }

            require(candidates.size == 1 && candidates.single().match != null) {
                when {
                    candidates.isEmpty() ->
                        "The selected words could not be matched to one editable PDF paragraph. Try selecting the text again."
                    else ->
                        "The selected words are ambiguous across this page. Select a shorter, unique phrase."
                }
            }
            val target = candidates.single()
            val replacementParagraph = target.originalText.replaceRange(target.match!!.range, newText)

            // The layout preview uses the same paragraph rewrite path as Save. The engine may
            // reflow the paragraph; the page is not covered with an overlay.
            execute(
                session,
                "text_edit_layout",
                JSONObject()
                    .put("doc", documentId)
                    .put("page", pageNumber)
                    .put("paragraph", target.number)
                    .put("text", replacementParagraph)
            )

            execute(
                session,
                "text_edit",
                JSONObject()
                    .put("doc", documentId)
                    .put("page", pageNumber)
                    .put("paragraph", target.number)
                    .put("text", replacementParagraph)
            )

            execute(
                session,
                "doc_save",
                JSONObject()
                    .put("doc", documentId)
                    .put("path", output.absolutePath)
                    .put("full", true)
            )
            check(output.isFile && output.length() > 0L) {
                "The advanced PDF engine did not create a saved PDF."
            }

            val reopened = session.open(output.absolutePath, null)
            verificationDocumentId = reopened.id.toLong()
            val verified = execute(
                session,
                "text_paragraphs",
                JSONObject().put("doc", verificationDocumentId).put("page", pageNumber)
            ).optJSONArray("paragraphs") ?: JSONArray()
            val normalizedExpected = normalize(newText)
            val savedText = buildString {
                for (index in 0 until verified.length()) {
                    val paragraph = verified.optJSONObject(index) ?: continue
                    append(' ')
                    append(paragraph.optString("editor_text").ifBlank { paragraph.optString("text") })
                }
            }
            check(normalize(savedText).contains(normalizedExpected)) {
                "The edited PDF was saved but the replacement text could not be verified after reopening."
            }

            completed = true
            output
        } finally {
            verificationDocumentId?.let { closeQuietly(session, it) }
            sourceDocumentId?.let { closeQuietly(session, it) }
            cacheInput.delete()
            if (!completed) output.delete()
        }
    }

    private fun execute(session: MobileSession, tool: String, args: JSONObject): JSONObject {
        val raw = session.execute(tool, args.toString())
        return JSONObject(raw)
    }

    private fun closeQuietly(session: MobileSession, documentId: Long) {
        runCatching {
            session.execute(
                "doc_close",
                JSONObject().put("doc", documentId).put("discard_changes", true).toString()
            )
        }
    }

    private fun whitespaceTolerantPattern(text: String): Regex {
        val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(parts.isNotEmpty()) { "Select some existing PDF text first." }
        return Regex(parts.joinToString("\\s+") { Regex.escape(it) })
    }

    private fun normalize(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()

    private data class ParagraphMatch(
        val number: Int,
        val originalText: String,
        val match: MatchResult?
    )
}

/** Keeps API 26-28 install compatibility while routing newer devices through PdfCraft. */
class PdfTextEditEngine(context: Context) {
    private val pdfCraft = PdfCraftNativeTextEditEngine(context)
    private val pdfBoxFallback = PdfNativeTextEditEngine(context)

    suspend fun replaceText(
        sourceUri: Uri,
        pageIndex: Int,
        oldText: String,
        newText: String,
        outputFileName: String
    ): File {
        return if (Build.VERSION.SDK_INT >= 29) {
            pdfCraft.replaceText(sourceUri, pageIndex, oldText, newText, outputFileName)
        } else {
            pdfBoxFallback.replaceText(sourceUri, pageIndex, oldText, newText, outputFileName)
        }
    }
}
