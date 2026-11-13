package com.example.feature.operations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feature.documentcreator.DocumentCreatorEngine
import com.example.engine.ocr.OcrLanguage
import com.example.engine.ocr.OfflineTesseractOcrEngine
import com.example.ui.components.UniversalResultDialog
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(
    onBackClick: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onRegisterGeneratedFile: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Keep the result empty until a real OCR operation returns recognized text.
    var recognizedText by remember { mutableStateOf("") }
    var sourceLabel by remember { mutableStateOf("No document selected") }
    var selectedLanguage by remember { mutableStateOf(OcrLanguage.ENGLISH) }

    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var scanStatusMessage by remember { mutableStateOf("") }

    // Result dialog states
    var resultDialogFile by remember { mutableStateOf<File?>(null) }
    var resultDialogMime by remember { mutableStateOf("text/plain") }
    var resultDialogTitle by remember { mutableStateOf("OCR Text Exported!") }

    // Pick Image Launcher
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            sourceLabel = "Image Document"
            isScanning = true
            scanProgress = 0.3f
            scanStatusMessage = "Preparing offline OCR..."
            scope.launch {
                try {
                    val extracted = runOfflineImageOcr(context, uri, selectedLanguage)
                    recognizedText = if (extracted.isNotBlank()) extracted else "[Offline OCR: No text detected in this image]"
                    scanStatusMessage = "Image scan complete."
                } catch (error: Exception) {
                    recognizedText = ""
                    scanStatusMessage = "OCR failed: ${error.localizedMessage ?: "Please try another image."}"
                } finally {
                    isScanning = false
                    scanProgress = 1f
                }
            }
        }
    }

    // Pick PDF Launcher for Multi-page OCR
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            sourceLabel = "PDF Document"
            isScanning = true
            scanProgress = 0.1f
            scanStatusMessage = "Reading PDF pages..."
            scope.launch {
                try {
                    val extracted = runOfflinePdfOcr(context, uri, selectedLanguage) { current, total ->
                        scanProgress = current.toFloat() / total.toFloat()
                        scanStatusMessage = "Scanning page $current of $total..."
                    }
                    recognizedText = if (extracted.isNotBlank()) extracted else "[Offline OCR: No text detected in PDF]"
                    scanStatusMessage = "Multi-page scan complete."
                } catch (error: Exception) {
                    recognizedText = ""
                    scanStatusMessage = "OCR failed: ${error.localizedMessage ?: "Please try another PDF."}"
                } finally {
                    isScanning = false
                    scanProgress = 1f
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("ocr_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("OCR Document Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(recognizedText))
                            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        enabled = recognizedText.isNotBlank() && !isScanning,
                        modifier = Modifier.testTag("ocr_copy_all_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy All")
                    }
                    IconButton(
                        onClick = { recognizedText = "" },
                        modifier = Modifier.testTag("ocr_clear_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Source Picker Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Select Source Document:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("ocr_scan_image_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Image")
                        }

                        OutlinedButton(
                            onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.weight(1f).testTag("ocr_scan_pdf_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan PDF")
                        }
                    }

                    // Language Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recognition Target:", style = MaterialTheme.typography.labelMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            OcrLanguage.values().forEach { lang ->
                                FilterChip(
                                    selected = selectedLanguage == lang,
                                    onClick = { selectedLanguage = lang },
                                    enabled = !isScanning,
                                    label = { Text(lang.label) },
                                    modifier = Modifier.testTag("ocr_lang_${lang.tessCode}")
                                )
                            }
                        }
                    }

                    Text(
                        "Offline OCR supports English, Bengali, or both together. Language models are included with the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Progress State Indicator
                    if (isScanning) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(scanStatusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth())
                        }
                    } else if (scanStatusMessage.isNotBlank()) {
                        Text("Source: $sourceLabel • $scanStatusMessage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Editable Text Review Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. Editable Text Review:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${recognizedText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size} words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = recognizedText,
                        onValueChange = { recognizedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("ocr_recognized_text_field"),
                        placeholder = { Text("Extracted text will appear here. You can modify any words or sentences.") }
                    )
                }
            }

            // Export Options
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("3. Export Extracted Text:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Export to Word (DOCX)
                        Button(
                            onClick = {
                                scope.launch {
                                    val docxFile = exportOcrToDocx(context, recognizedText)
                                    onRegisterGeneratedFile(Uri.fromFile(docxFile), docxFile.name)
                                    resultDialogFile = docxFile
                                    resultDialogMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    resultDialogTitle = "DOCX Word Document Ready!"
                                }
                            },
                            enabled = recognizedText.isNotBlank(),
                            modifier = Modifier.weight(1f).testTag("ocr_export_word_button")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DOCX")
                        }

                        // Export to PDF
                        Button(
                            onClick = {
                                scope.launch {
                                    val lines = recognizedText.split("\n")
                                    val pdfFile = DocumentCreatorEngine.createNewPdf(context, "OCR_Document", lines)
                                    onRegisterGeneratedFile(Uri.fromFile(pdfFile), pdfFile.name)
                                    resultDialogFile = pdfFile
                                    resultDialogMime = "application/pdf"
                                    resultDialogTitle = "PDF Document Ready!"
                                }
                            },
                            enabled = recognizedText.isNotBlank(),
                            modifier = Modifier.weight(1f).testTag("ocr_export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF")
                        }

                        // Export to TXT
                        Button(
                            onClick = {
                                scope.launch {
                                    val txtFile = exportOcrToTxt(context, recognizedText)
                                    onRegisterGeneratedFile(Uri.fromFile(txtFile), txtFile.name)
                                    resultDialogFile = txtFile
                                    resultDialogMime = "text/plain"
                                    resultDialogTitle = "TXT Text File Ready!"
                                }
                            },
                            enabled = recognizedText.isNotBlank(),
                            modifier = Modifier.weight(1f).testTag("ocr_export_txt_button")
                        ) {
                            Icon(Icons.Default.TextSnippet, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TXT")
                        }
                    }
                }
            }
        }
    }

    // Universal Result Screen
    if (resultDialogFile != null) {
        val file = resultDialogFile!!
        val isPdf = file.name.endsWith(".pdf", ignoreCase = true)

        UniversalResultDialog(
            title = resultDialogTitle,
            file = file,
            onDismiss = { resultDialogFile = null },
            onOpenFile = {
                val f = file
                resultDialogFile = null
                if (isPdf) {
                    onOpenPdf(Uri.fromFile(f), f.name)
                } else {
                    com.example.core.sharing.FileSharingManager.shareDocument(context, Uri.fromFile(f), f.name)
                }
            },
            onAnotherAction = {
                resultDialogFile = null
                recognizedText = ""
            },
            anotherActionLabel = "Scan Another"
        )
    }
}

private suspend fun runOfflineImageOcr(
    context: Context,
    uri: Uri,
    language: OcrLanguage
): String = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext ""
    var sampleSize = 1
    while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 3000) {
        sampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, decodeOptions)
    } ?: return@withContext ""
    try {
        OfflineTesseractOcrEngine.openSession(context, language).use { session ->
            session.recognize(bitmap)
        }
    } finally {
        bitmap.recycle()
    }
}

private suspend fun runOfflinePdfOcr(
    context: Context,
    pdfUri: Uri,
    language: OcrLanguage,
    onProgress: (current: Int, total: Int) -> Unit
): String = withContext(Dispatchers.IO) {
    val output = StringBuilder()
    val tempFile = File(context.cacheDir, "temp_ocr_${java.util.UUID.randomUUID()}.pdf")
    try {
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            FileOutputStream(tempFile).use { fileOutput -> input.copyTo(fileOutput) }
        } ?: return@withContext ""

        require(tempFile.length() > 0L) { "The selected PDF is empty or unreadable." }
        PDFBoxResourceLoader.init(context)

        // Prefer searchable native PDF text; run OCR only on pages with no extractable text.
        val nativeDocument = try { PDDocument.load(tempFile) } catch (_: Exception) { null }
        val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(pfd)
            try {
                val total = renderer.pageCount
                if (total == 0) return@withContext ""
                var ocrSession: OfflineTesseractOcrEngine.Session? = null
                try {
                    for (index in 0 until total) {
                        onProgress(index + 1, total)
                        var pageText = ""
                        if (nativeDocument != null && index < nativeDocument.numberOfPages) {
                            try {
                                val stripper = PDFTextStripper().apply {
                                    startPage = index + 1
                                    endPage = index + 1
                                    sortByPosition = true
                                }
                                pageText = stripper.getText(nativeDocument).trim()
                            } catch (_: Exception) {
                                pageText = ""
                            }
                        }

                        if (pageText.isBlank()) {
                            val page = renderer.openPage(index)
                            try {
                                val bitmap = Bitmap.createBitmap(
                                    (page.width * 1.5f).toInt().coerceAtLeast(1),
                                    (page.height * 1.5f).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                try {
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    if (ocrSession == null) ocrSession = OfflineTesseractOcrEngine.openSession(context, language)
                                    pageText = ocrSession!!.recognize(bitmap)
                                } finally {
                                    bitmap.recycle()
                                }
                            } finally {
                                page.close()
                            }
                        }

                        if (pageText.isNotBlank()) {
                            output.append("--- PAGE ${index + 1} ---\n")
                            output.append(pageText).append("\n\n")
                        }
                    }
                } finally {
                    ocrSession?.close()
                }
            } finally {
                renderer.close()
            }
        } finally {
            pfd.close()
            nativeDocument?.close()
        }
    } catch (error: Exception) {
        throw error
    } finally {
        tempFile.delete()
    }
    output.toString()
}

private suspend fun exportOcrToDocx(context: Context, text: String): File = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
    val docxFile = File(dir, "OCR_Document_${System.currentTimeMillis() % 10000}.docx")

    val lines = text.split("\n")
    val docXml = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">")
        append("<w:body>")
        for (line in lines) {
            val escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            append("<w:p><w:r><w:t>$escaped</w:t></w:r></w:p>")
        }
        append("</w:body></w:document>")
    }

    val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    ZipOutputStream(FileOutputStream(docxFile)).use { zos ->
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write(relsXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("word/document.xml"))
        zos.write(docXml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    docxFile
}

private suspend fun exportOcrToTxt(context: Context, text: String): File = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
    val txtFile = File(dir, "OCR_Document_${System.currentTimeMillis() % 10000}.txt")
    FileOutputStream(txtFile).use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    }
    txtFile
}
