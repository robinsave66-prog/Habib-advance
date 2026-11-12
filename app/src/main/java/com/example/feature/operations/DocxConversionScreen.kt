package com.example.feature.operations

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.sharing.FileSharingManager
import com.example.engine.core.AdvancedPdfOperationsEngine
import com.example.feature.filemanager.formatFileSize
import kotlinx.coroutines.launch
import java.io.File

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Switch
import com.example.engine.converter.WordPageMargins
import com.example.engine.converter.WordPageOrientation
import com.example.engine.converter.WordPageSize
import com.example.engine.converter.WordToPdfConfig
import com.example.engine.converter.WordToPdfConversionReport

enum class DocxConversionMode {
    PDF_TO_WORD,
    WORD_TO_PDF
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxConversionScreen(
    initialMode: DocxConversionMode = DocxConversionMode.PDF_TO_WORD,
    onBackClick: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onRegisterGeneratedFile: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf(initialMode) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<Long?>(null) }
    var outputNameInput by remember {
        mutableStateOf(
            if (initialMode == DocxConversionMode.PDF_TO_WORD) "Converted_Document.docx"
            else "Converted_Document.pdf"
        )
    }

    // Word to PDF Page Settings
    var selectedPageSize by remember { mutableStateOf(WordPageSize.A4) }
    var selectedOrientation by remember { mutableStateOf(WordPageOrientation.PORTRAIT) }
    var selectedMargins by remember { mutableStateOf(WordPageMargins.NORMAL) }
    var includePageNumbers by remember { mutableStateOf(true) }
    var wordToPdfReport by remember { mutableStateOf<WordToPdfConversionReport?>(null) }

    var isConverting by remember { mutableStateOf(false) }
    var conversionSuccessFile by remember { mutableStateOf<File?>(null) }

    // Picker for source file
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            val name = queryDocName(context, uri) ?: if (currentMode == DocxConversionMode.PDF_TO_WORD) "source.pdf" else "source.docx"
            selectedFileName = name
            selectedFileSize = queryDocSize(context, uri)
            val baseName = name.substringBeforeLast(".")
            outputNameInput = if (currentMode == DocxConversionMode.PDF_TO_WORD) "${baseName}.docx" else "${baseName}.pdf"
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
                        modifier = Modifier.testTag("docx_conversion_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = if (currentMode == DocxConversionMode.PDF_TO_WORD) "PDF to Word (DOCX)" else "Word to PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = if (currentMode == DocxConversionMode.PDF_TO_WORD) 0 else 1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = currentMode == DocxConversionMode.PDF_TO_WORD,
                    onClick = {
                        currentMode = DocxConversionMode.PDF_TO_WORD
                        selectedUri = null
                        selectedFileName = null
                        outputNameInput = "Converted_Document.docx"
                    },
                    text = { Text("PDF to Word") },
                    icon = { Icon(Icons.Default.Description, contentDescription = null) },
                    modifier = Modifier.testTag("tab_pdf_to_word")
                )
                Tab(
                    selected = currentMode == DocxConversionMode.WORD_TO_PDF,
                    onClick = {
                        currentMode = DocxConversionMode.WORD_TO_PDF
                        selectedUri = null
                        selectedFileName = null
                        outputNameInput = "Converted_Document.pdf"
                    },
                    text = { Text("Word to PDF") },
                    icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                    modifier = Modifier.testTag("tab_word_to_pdf")
                )
            }

            // Overview Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (currentMode == DocxConversionMode.PDF_TO_WORD) Icons.Default.Description else Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (currentMode == DocxConversionMode.PDF_TO_WORD) "Extract & Generate Genuine DOCX" else "Convert OpenXML Word to Formatted PDF",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (currentMode == DocxConversionMode.PDF_TO_WORD) "Creates native Microsoft Office compatible OpenXML (.docx) documents." else "Compiles document structure and text into standard A4 PDF pages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Source File Selection Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "1. Select Source Document",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (selectedUri != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedFileName ?: "Selected Document",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (selectedFileSize != null) {
                                        Text(
                                            text = formatFileSize(selectedFileSize!!),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        val mime = if (currentMode == DocxConversionMode.PDF_TO_WORD) "application/pdf" else "*/*"
                                        filePicker.launch(arrayOf(mime))
                                    }
                                ) {
                                    Text("Change")
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val mime = if (currentMode == DocxConversionMode.PDF_TO_WORD) "application/pdf" else "*/*"
                                filePicker.launch(arrayOf(mime))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("select_source_doc_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (currentMode == DocxConversionMode.PDF_TO_WORD) "Choose PDF File" else "Choose Word File (.docx)")
                        }
                    }
                }
            }

            // Page Layout & Formatting Options (for Word to PDF)
            if (currentMode == DocxConversionMode.WORD_TO_PDF) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "2. Page Layout & Print Setup",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )

                        // Page Size
                        Text(
                            text = "Page Size:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WordPageSize.values().forEach { size ->
                                FilterChip(
                                    selected = selectedPageSize == size,
                                    onClick = { selectedPageSize = size },
                                    label = { Text(size.name) }
                                )
                            }
                        }

                        // Orientation
                        Text(
                            text = "Orientation:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WordPageOrientation.values().forEach { orient ->
                                FilterChip(
                                    selected = selectedOrientation == orient,
                                    onClick = { selectedOrientation = orient },
                                    label = { Text(orient.displayName) }
                                )
                            }
                        }

                        // Margins
                        Text(
                            text = "Margins:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WordPageMargins.values().forEach { margin ->
                                FilterChip(
                                    selected = selectedMargins == margin,
                                    onClick = { selectedMargins = margin },
                                    label = { Text(margin.displayName.substringBefore(" ")) }
                                )
                            }
                        }

                        // Page Numbers Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Include Page Numbers", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Footer numbering (Page 1, 2...)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = includePageNumbers,
                                onCheckedChange = { includePageNumbers = it }
                            )
                        }
                    }
                }
            }

            // Output Settings Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (currentMode == DocxConversionMode.WORD_TO_PDF) "3. Output File Name" else "2. Output File Name",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = outputNameInput,
                        onValueChange = { outputNameInput = it },
                        label = { Text("Output File Name") },
                        modifier = Modifier.fillMaxWidth().testTag("output_docx_name_input")
                    )
                }
            }

            // Convert Button
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    isConverting = true
                    scope.launch {
                        try {
                            if (currentMode == DocxConversionMode.PDF_TO_WORD) {
                                val report = AdvancedPdfOperationsEngine.convertPdfToRichDocx(
                                    context = context,
                                    pdfUri = uri,
                                    outputFileName = outputNameInput
                                )
                                onRegisterGeneratedFile(Uri.fromFile(report.file), report.file.name)
                                conversionSuccessFile = report.file
                            } else {
                                val config = WordToPdfConfig(
                                    pageSize = selectedPageSize,
                                    orientation = selectedOrientation,
                                    margins = selectedMargins,
                                    includePageNumbers = includePageNumbers
                                )
                                val report = AdvancedPdfOperationsEngine.convertDocxToPdfWithReport(
                                    context = context,
                                    docxUri = uri,
                                    outputFileName = outputNameInput,
                                    config = config
                                )
                                wordToPdfReport = report
                                onRegisterGeneratedFile(Uri.fromFile(report.file), report.file.name)
                                conversionSuccessFile = report.file
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Conversion failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isConverting = false
                        }
                    }
                },
                enabled = selectedUri != null && !isConverting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("execute_docx_conversion_button")
            ) {
                if (isConverting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Converting Document...")
                } else {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (currentMode == DocxConversionMode.PDF_TO_WORD) "Convert to Word (DOCX)" else "Convert to PDF")
                }
            }
        }
    }

    // Success Modal with UniversalResultDialog
    if (conversionSuccessFile != null) {
        val file = conversionSuccessFile!!
        val isPdfResult = file.name.endsWith(".pdf", ignoreCase = true)
        val details = mutableListOf<Pair<String, String>>()
        details.add("Format" to if (isPdfResult) "Adobe PDF Standard" else "Microsoft Word OpenXML (.docx)")
        if (wordToPdfReport != null && isPdfResult) {
            val r = wordToPdfReport!!
            details.add("Pages Generated" to "${r.pageCount}")
            details.add("Paragraphs" to "${r.paragraphCount}")
            details.add("Tables Preserved" to "${r.tableCount}")
            details.add("Layout Verification" to if (r.validationPassed) "Pass (No text clipping)" else "Generated")
            details.add("Page Setup" to "${selectedPageSize.name} ${selectedOrientation.displayName}")
        } else {
            details.add("Integrity" to "Verified")
        }

        com.example.ui.components.UniversalResultDialog(
            title = if (isPdfResult) "PDF Created Successfully!" else "Word Document Generated!",
            file = file,
            additionalDetails = details,
            onDismiss = { conversionSuccessFile = null },
            onOpenFile = {
                val uri = Uri.fromFile(file)
                conversionSuccessFile = null
                if (isPdfResult) {
                    onOpenPdf(uri, file.name)
                } else {
                    FileSharingManager.openDocumentWithExternalApp(context, file, FileSharingManager.getMimeTypeForFile(file.name))
                }
            },
            onAnotherAction = {
                conversionSuccessFile = null
                selectedUri = null
                selectedFileName = null
                wordToPdfReport = null
            },
            anotherActionLabel = "Convert Another"
        )
    }
}

private fun queryDocName(context: Context, uri: Uri): String? {
    var name: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
    } catch (_: Exception) {}
    return name
}

private fun queryDocSize(context: Context, uri: Uri): Long? {
    var size: Long? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (idx != -1 && cursor.moveToFirst()) {
                size = cursor.getLong(idx)
            }
        }
    } catch (_: Exception) {}
    return size
}
