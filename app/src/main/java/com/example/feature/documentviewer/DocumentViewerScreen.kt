package com.example.feature.documentviewer

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.text.Html
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.sharing.FileSharingManager
import com.example.engine.core.AdvancedPdfOperationsEngine
import com.example.feature.filemanager.formatFileSize
import com.example.feature.pdfviewer.PdfViewerScreen
import com.example.feature.pdfviewer.PdfViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

enum class DocumentFormatType {
    PDF,
    DOCX,
    LEGACY_DOC,
    XLSX,
    LEGACY_XLS,
    PPTX,
    LEGACY_PPT,
    TXT,
    RTF,
    HTML,
    IMAGE,
    UNSUPPORTED
}

data class ParsedDocumentContent(
    val format: DocumentFormatType,
    val title: String,
    val fileSize: Long,
    val paragraphs: List<String> = emptyList(),
    val docxTables: List<List<List<String>>> = emptyList(),
    val spreadsheetSheets: Map<String, List<List<String>>> = emptyMap(),
    val presentationSlides: List<String> = emptyList(),
    val rawText: String = "",
    val textLineCount: Int = 0,
    val textWordCount: Int = 0,
    val textCharCount: Int = 0,
    val legacyWarning: String? = null,
    val errorMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentUri: Uri,
    documentName: String? = null,
    pdfViewerViewModel: PdfViewerViewModel,
    onBackClick: () -> Unit,
    onEditPdfClick: (Uri, String) -> Unit,
    onConvertToPdfRequested: ((Uri, String) -> Unit)? = null
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val resolvedName = remember(documentUri) {
        documentName ?: queryFileName(context, documentUri) ?: "Document"
    }

    val formatType = remember(resolvedName, documentUri) {
        detectFormat(resolvedName, documentUri, context)
    }

    // If PDF, route to the dedicated high-performance PdfViewerScreen with Zoom & Pan
    if (formatType == DocumentFormatType.PDF) {
        PdfViewerScreen(
            documentUri = documentUri,
            documentName = resolvedName,
            viewModel = pdfViewerViewModel,
            onBackClick = onBackClick,
            onEditClick = onEditPdfClick
        )
        return
    }

    var isLoading by remember { mutableStateOf(true) }
    var parsedContent by remember { mutableStateOf<ParsedDocumentContent?>(null) }
    var isConvertingToPdf by remember { mutableStateOf(false) }

    // Search query state
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Save to Device via SAF CreateDocument
    val saveToDeviceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(FileSharingManager.getMimeTypeForFile(resolvedName))
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(documentUri)?.use { inStream ->
                        context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                            inStream.copyTo(outStream)
                            outStream.flush()
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved to device storage successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(documentUri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val size = queryFileSize(context, documentUri) ?: 0L
                val content = when (formatType) {
                    DocumentFormatType.DOCX -> parseDocxDocument(context, documentUri, resolvedName, size)
                    DocumentFormatType.LEGACY_DOC -> parseLegacyOfficeDocument(context, documentUri, resolvedName, size, DocumentFormatType.LEGACY_DOC)
                    DocumentFormatType.XLSX -> parseXlsxDocument(context, documentUri, resolvedName, size)
                    DocumentFormatType.LEGACY_XLS -> parseLegacyOfficeDocument(context, documentUri, resolvedName, size, DocumentFormatType.LEGACY_XLS)
                    DocumentFormatType.PPTX -> parsePptxDocument(context, documentUri, resolvedName, size)
                    DocumentFormatType.LEGACY_PPT -> parseLegacyOfficeDocument(context, documentUri, resolvedName, size, DocumentFormatType.LEGACY_PPT)
                    DocumentFormatType.TXT -> parseTxtDocument(context, documentUri, resolvedName, size)
                    DocumentFormatType.RTF -> parseRtfDocument(context, documentUri, resolvedName, size)
                    DocumentFormatType.HTML -> parseHtmlDocument(context, documentUri, resolvedName, size)
                    DocumentFormatType.IMAGE -> ParsedDocumentContent(DocumentFormatType.IMAGE, resolvedName, size)
                    else -> ParsedDocumentContent(
                        format = DocumentFormatType.UNSUPPORTED,
                        title = resolvedName,
                        fileSize = size
                    )
                }
                parsedContent = content
            } catch (e: Exception) {
                parsedContent = ParsedDocumentContent(
                    format = formatType,
                    title = resolvedName,
                    fileSize = 0L,
                    errorMessage = e.message
                )
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBackClick, modifier = Modifier.testTag("doc_viewer_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = resolvedName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = getFormatLabel(formatType),
                                style = MaterialTheme.typography.bodySmall,
                                color = getFormatColor(formatType)
                            )
                        }
                    },
                    actions = {
                        // Search Toggle for text-based formats
                        if (formatType in listOf(DocumentFormatType.DOCX, DocumentFormatType.XLSX, DocumentFormatType.PPTX, DocumentFormatType.TXT, DocumentFormatType.RTF, DocumentFormatType.HTML)) {
                            IconButton(
                                onClick = {
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) searchQuery = ""
                                },
                                modifier = Modifier.testTag("doc_viewer_search_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search text")
                            }
                        }

                        IconButton(
                            onClick = { saveToDeviceLauncher.launch(resolvedName) },
                            modifier = Modifier.testTag("doc_viewer_save_button")
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Save to Device")
                        }
                        IconButton(
                            onClick = {
                                FileSharingManager.shareDocument(context, documentUri, resolvedName)
                            },
                            modifier = Modifier.testTag("doc_viewer_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Document")
                        }
                    }
                )

                // Inline Search Bar
                AnimatedVisibility(visible = showSearchBar) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search in document...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("doc_viewer_search_input"),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Reading and rendering document...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val content = parsedContent ?: return@Box

                Column(modifier = Modifier.fillMaxSize()) {
                    // Document Format Overview Header
                    DocumentFormatOverviewHeader(
                        formatType = formatType,
                        fileName = content.title,
                        fileSize = content.fileSize,
                        onOpenWithExternalApp = {
                            FileSharingManager.openDocumentWithExternalApp(
                                context = context,
                                uri = documentUri,
                                fileName = content.title,
                                mimeType = FileSharingManager.getMimeTypeForFile(content.title)
                            )
                        },
                        onConvertToPdf = if (formatType == DocumentFormatType.DOCX || formatType == DocumentFormatType.TXT) {
                            {
                                isConvertingToPdf = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val pdfResult = if (formatType == DocumentFormatType.DOCX) {
                                            AdvancedPdfOperationsEngine.convertDocxToPdf(
                                                context = context,
                                                docxUri = documentUri,
                                                outputFileName = "${content.title.substringBeforeLast(".")}.pdf"
                                            )
                                        } else {
                                            com.example.feature.documentcreator.DocumentCreatorEngine.createNewPdf(
                                                context = context,
                                                title = content.title.substringBeforeLast("."),
                                                contentLines = content.rawText.split("\n")
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Converted to PDF successfully!", Toast.LENGTH_SHORT).show()
                                            onConvertToPdfRequested?.invoke(Uri.fromFile(pdfResult), pdfResult.name)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Conversion failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        isConvertingToPdf = false
                                    }
                                }
                            }
                        } else null,
                        isConverting = isConvertingToPdf
                    )

                    // Document Content Rendering based on format
                    when (formatType) {
                        DocumentFormatType.DOCX -> {
                            DocxContentViewer(
                                paragraphs = content.paragraphs,
                                tables = content.docxTables,
                                searchQuery = searchQuery
                            )
                        }
                        DocumentFormatType.XLSX -> {
                            XlsxContentViewer(
                                sheetsMap = content.spreadsheetSheets,
                                searchQuery = searchQuery
                            )
                        }
                        DocumentFormatType.PPTX -> {
                            PptxContentViewer(
                                slides = content.presentationSlides,
                                searchQuery = searchQuery
                            )
                        }
                        DocumentFormatType.TXT,
                        DocumentFormatType.RTF,
                        DocumentFormatType.HTML -> {
                            TxtContentViewer(
                                text = content.rawText,
                                lineCount = content.textLineCount,
                                wordCount = content.textWordCount,
                                charCount = content.textCharCount,
                                searchQuery = searchQuery
                            )
                        }
                        DocumentFormatType.IMAGE -> {
                            ZoomableImageContentViewer(imageUri = documentUri)
                        }
                        DocumentFormatType.LEGACY_DOC,
                        DocumentFormatType.LEGACY_XLS,
                        DocumentFormatType.LEGACY_PPT -> {
                            LegacyFormatContentViewer(
                                formatType = formatType,
                                fileName = content.title,
                                extractedText = content.rawText,
                                warningMessage = content.legacyWarning,
                                onOpenExternal = {
                                    FileSharingManager.openDocumentWithExternalApp(
                                        context = context,
                                        uri = documentUri,
                                        fileName = content.title,
                                        mimeType = FileSharingManager.getMimeTypeForFile(content.title)
                                    )
                                }
                            )
                        }
                        else -> {
                            UnsupportedContentViewer(
                                fileName = content.title,
                                onOpenExternal = {
                                    FileSharingManager.openDocumentWithExternalApp(
                                        context = context,
                                        uri = documentUri,
                                        fileName = content.title,
                                        mimeType = FileSharingManager.getMimeTypeForFile(content.title)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentFormatOverviewHeader(
    formatType: DocumentFormatType,
    fileName: String,
    fileSize: Long,
    onOpenWithExternalApp: () -> Unit,
    onConvertToPdf: (() -> Unit)?,
    isConverting: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = getFormatColor(formatType).copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = getFormatIcon(formatType),
                                contentDescription = null,
                                tint = getFormatColor(formatType),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = getFormatLabel(formatType),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (fileSize > 0) formatFileSize(fileSize) else "Document File",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Read-Only Document",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onConvertToPdf != null) {
                    Button(
                        onClick = onConvertToPdf,
                        enabled = !isConverting,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("convert_doc_to_pdf_button")
                    ) {
                        if (isConverting) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Converting...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Convert to PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenWithExternalApp,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("open_external_app_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open with App", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// --- Format Viewers ---

@Composable
private fun DocxContentViewer(
    paragraphs: List<String>,
    tables: List<List<List<String>>>,
    searchQuery: String
) {
    if (paragraphs.isEmpty() && tables.isEmpty()) {
        EmptyDocumentPlaceholder("No readable text or tables found in this Word document.")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        paragraphs.forEach { paragraph ->
                            val isHeading = paragraph.length < 60 && (paragraph.all { it.isUpperCase() || it.isWhitespace() } || paragraph.endsWith(":"))
                            val annotated = remember(paragraph, searchQuery) {
                                buildAnnotatedText(paragraph, searchQuery)
                            }
                            Text(
                                text = annotated,
                                style = if (isHeading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHeading) MaterialTheme.colorScheme.primary else Color(0xFF1E293B),
                                lineHeight = 22.sp
                            )
                        }
                        tables.forEachIndexed { tableIndex, table ->
                            Text(
                                text = "Table " + (tableIndex + 1),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                            ) {
                                table.forEachIndexed { rowIndex, cells ->
                                    Row(modifier = Modifier.background(
                                        if (rowIndex == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        else Color.White
                                    )) {
                                        cells.forEach { cell ->
                                            val annotatedCell = remember(cell, searchQuery) {
                                                buildAnnotatedText(cell, searchQuery)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .width(150.dp)
                                                    .border(0.5.dp, Color(0xFFCBD5E1))
                                                    .padding(horizontal = 8.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    text = annotatedCell,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                                    color = Color(0xFF1E293B)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XlsxContentViewer(
    sheetsMap: Map<String, List<List<String>>>,
    searchQuery: String
) {
    if (sheetsMap.isEmpty() || sheetsMap.values.all { it.isEmpty() }) {
        EmptyDocumentPlaceholder("No spreadsheet data found in workbook.")
        return
    }

    val sheetNames = remember(sheetsMap) { sheetsMap.keys.toList() }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }

    val currentSheetName = sheetNames.getOrElse(selectedSheetIndex) { sheetNames.first() }
    val rows = sheetsMap[currentSheetName].orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // Multi-Sheet Tabs
        if (sheetNames.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedSheetIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                sheetNames.forEachIndexed { index, name ->
                    Tab(
                        selected = selectedSheetIndex == index,
                        onClick = { selectedSheetIndex = index },
                        text = { Text(name, fontWeight = if (selectedSheetIndex == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }

        // Sheet Header Info
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sheet: $currentSheetName",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${rows.size} rows",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val hScroll = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll)
            ) {
                itemsIndexed(rows) { rowIndex, rowCells ->
                    val isHeader = rowIndex == 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isHeader) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else if (rowIndex % 2 == 0) Color.White else Color(0xFFF8FAFC)
                            )
                            .border(0.5.dp, Color(0xFFCBD5E1))
                    ) {
                        // Row index column
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(38.dp)
                                .background(Color(0xFFE2E8F0))
                                .border(0.5.dp, Color(0xFFCBD5E1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${rowIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }

                        rowCells.forEach { cellText ->
                            val isMatch = searchQuery.isNotBlank() && cellText.contains(searchQuery, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .width(135.dp)
                                    .height(38.dp)
                                    .background(if (isMatch) Color(0xFFFFF9C4) else Color.Transparent)
                                    .border(0.5.dp, Color(0xFFCBD5E1))
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cellText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isHeader || isMatch) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    color = if (isMatch) Color(0xFFB71C1C) else Color(0xFF1E293B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PptxContentViewer(
    slides: List<String>,
    searchQuery: String
) {
    if (slides.isEmpty()) {
        EmptyDocumentPlaceholder("No slide contents detected in this PowerPoint presentation.")
        return
    }

    var currentSlideIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Slide Navigation Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (currentSlideIndex > 0) currentSlideIndex-- },
                    enabled = currentSlideIndex > 0
                ) {
                    Icon(Icons.Default.NavigateBefore, contentDescription = "Previous Slide")
                }

                Text(
                    text = "Slide ${currentSlideIndex + 1} of ${slides.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { if (currentSlideIndex < slides.size - 1) currentSlideIndex++ },
                    enabled = currentSlideIndex < slides.size - 1
                ) {
                    Icon(Icons.Default.NavigateNext, contentDescription = "Next Slide")
                }
            }
        }

        // Active Slide Card
        val activeSlideText = slides.getOrElse(currentSlideIndex) { "" }
        val annotatedText = remember(activeSlideText, searchQuery) {
            buildAnnotatedText(activeSlideText, searchQuery)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Slide ${currentSlideIndex + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.Slideshow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider()

                    Text(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 26.sp,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }
    }
}

@Composable
private fun TxtContentViewer(
    text: String,
    lineCount: Int,
    wordCount: Int,
    charCount: Int,
    searchQuery: String
) {
    var fontSizeSp by remember { mutableFloatStateOf(14f) }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Text stats & font controls toolbar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$lineCount lines • $wordCount words • $charCount chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(10f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Decrease Font", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${fontSizeSp.toInt()} pt",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { fontSizeSp = (fontSizeSp + 1f).coerceAtMost(26f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Increase Font", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        val lines = remember(text) { text.split("\n") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                itemsIndexed(lines) { idx, lineText ->
                    val annotatedLine = remember(lineText, searchQuery) {
                        buildAnnotatedText(lineText, searchQuery)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Line number
                        Text(
                            text = "${idx + 1}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = (fontSizeSp - 2f).coerceAtLeast(9f).sp,
                            color = Color.Gray,
                            modifier = Modifier
                                .width(36.dp)
                                .padding(end = 8.dp)
                        )
                        // Content
                        Text(
                            text = annotatedLine,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.4f).sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageContentViewer(imageUri: Uri) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1.0f, 5.0f)
                        if (scale > 1.0f) {
                            panX += pan.x
                            panY += pan.y
                        } else {
                            panX = 0f
                            panY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1.0f
                                panX = 0f
                                panY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Image preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panX
                        translationY = panY
                        rotationZ = rotationDegrees
                    }
            )
        }

        // Overlay Zoom & Rotate controls
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f }) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White)
                }
                IconButton(onClick = {
                    scale = 1.0f
                    panX = 0f
                    panY = 0f
                    rotationDegrees = 0f
                }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = Color.White)
                }
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun LegacyFormatContentViewer(
    formatType: DocumentFormatType,
    fileName: String,
    extractedText: String,
    warningMessage: String?,
    onOpenExternal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Legacy Office Binary Format (97-2003)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = warningMessage ?: "This file uses the legacy binary OLE format (.doc/.xls/.ppt). Modern applications recommend converting to OpenXML (.docx/.xlsx/.pptx) for full layout fidelity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onOpenExternal,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open with Microsoft Office / External App")
                    }
                }
            }
        }

        if (extractedText.isNotBlank()) {
            Text(
                text = "Extracted Text Content:",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = extractedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun UnsupportedContentViewer(
    fileName: String,
    onOpenExternal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "External Viewer Required",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Habib Advance Editor provides native reading for PDF, DOCX, XLSX, PPTX, TXT, and Images.\n\nFile \"$fileName\" can be opened with compatible external applications installed on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onOpenExternal) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open with External App")
        }
    }
}

@Composable
private fun EmptyDocumentPlaceholder(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

// --- Text Highlighting Utility ---

private fun buildAnnotatedText(text: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    var startIndex = 0
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()

    while (startIndex < text.length) {
        val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
        if (matchIndex == -1) {
            append(text.substring(startIndex))
            break
        }
        if (matchIndex > startIndex) {
            append(text.substring(startIndex, matchIndex))
        }
        withStyle(SpanStyle(background = Color(0xFFFFF176), color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)) {
            append(text.substring(matchIndex, matchIndex + query.length))
        }
        startIndex = matchIndex + query.length
    }
}

// --- Format Detection & Parsing Helpers ---

private fun detectFormat(fileName: String, uri: Uri, context: Context): DocumentFormatType {
    val lower = fileName.lowercase()
    if (lower.endsWith(".pdf")) return DocumentFormatType.PDF
    if (lower.endsWith(".docx")) return DocumentFormatType.DOCX
    if (lower.endsWith(".doc")) return DocumentFormatType.LEGACY_DOC
    if (lower.endsWith(".xlsx")) return DocumentFormatType.XLSX
    if (lower.endsWith(".xls")) return DocumentFormatType.LEGACY_XLS
    if (lower.endsWith(".pptx")) return DocumentFormatType.PPTX
    if (lower.endsWith(".ppt")) return DocumentFormatType.LEGACY_PPT
    if (lower.endsWith(".rtf")) return DocumentFormatType.RTF
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return DocumentFormatType.HTML
    if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".csv") || lower.endsWith(".md") || lower.endsWith(".json") || lower.endsWith(".xml")) return DocumentFormatType.TXT
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) return DocumentFormatType.IMAGE

    // Check MIME type if extension is missing
    val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
    if (mime.contains("pdf")) return DocumentFormatType.PDF
    if (mime.contains("officedocument.wordprocessingml")) return DocumentFormatType.DOCX
    if (mime.contains("msword")) return DocumentFormatType.LEGACY_DOC
    if (mime.contains("officedocument.spreadsheetml")) return DocumentFormatType.XLSX
    if (mime.contains("ms-excel")) return DocumentFormatType.LEGACY_XLS
    if (mime.contains("officedocument.presentationml")) return DocumentFormatType.PPTX
    if (mime.contains("ms-powerpoint")) return DocumentFormatType.LEGACY_PPT
    if (mime.contains("rtf")) return DocumentFormatType.RTF
    if (mime.contains("text/html")) return DocumentFormatType.HTML
    if (mime.contains("text/plain")) return DocumentFormatType.TXT
    if (mime.startsWith("image/")) return DocumentFormatType.IMAGE

    return DocumentFormatType.UNSUPPORTED
}

private fun getFormatLabel(type: DocumentFormatType): String = when (type) {
    DocumentFormatType.PDF -> "Adobe PDF Document"
    DocumentFormatType.DOCX -> "Microsoft Word Document (.docx)"
    DocumentFormatType.LEGACY_DOC -> "Legacy Word Document (.doc)"
    DocumentFormatType.XLSX -> "Microsoft Excel Spreadsheet (.xlsx)"
    DocumentFormatType.LEGACY_XLS -> "Legacy Excel Spreadsheet (.xls)"
    DocumentFormatType.PPTX -> "PowerPoint Presentation (.pptx)"
    DocumentFormatType.LEGACY_PPT -> "Legacy PowerPoint (.ppt)"
    DocumentFormatType.TXT -> "Plain Text Document"
    DocumentFormatType.RTF -> "Rich Text Format (.rtf)"
    DocumentFormatType.HTML -> "HTML Document (.html)"
    DocumentFormatType.IMAGE -> "Image Document"
    DocumentFormatType.UNSUPPORTED -> "Document File"
}

private fun getFormatIcon(type: DocumentFormatType): ImageVector = when (type) {
    DocumentFormatType.PDF -> Icons.Default.PictureAsPdf
    DocumentFormatType.DOCX, DocumentFormatType.LEGACY_DOC -> Icons.Default.Description
    DocumentFormatType.XLSX, DocumentFormatType.LEGACY_XLS -> Icons.Default.GridOn
    DocumentFormatType.PPTX, DocumentFormatType.LEGACY_PPT -> Icons.Default.Slideshow
    DocumentFormatType.TXT, DocumentFormatType.RTF, DocumentFormatType.HTML -> Icons.Default.Description
    DocumentFormatType.IMAGE -> Icons.Default.Image
    DocumentFormatType.UNSUPPORTED -> Icons.Default.FileOpen
}

private fun getFormatColor(type: DocumentFormatType): Color = when (type) {
    DocumentFormatType.PDF -> Color(0xFFE53935)
    DocumentFormatType.DOCX -> Color(0xFF1E88E5)
    DocumentFormatType.LEGACY_DOC -> Color(0xFF1565C0)
    DocumentFormatType.XLSX -> Color(0xFF2E7D32)
    DocumentFormatType.LEGACY_XLS -> Color(0xFF1B5E20)
    DocumentFormatType.PPTX -> Color(0xFFD84315)
    DocumentFormatType.LEGACY_PPT -> Color(0xFFBF360C)
    DocumentFormatType.TXT -> Color(0xFF546E7A)
    DocumentFormatType.RTF -> Color(0xFF455A64)
    DocumentFormatType.HTML -> Color(0xFFE65100)
    DocumentFormatType.IMAGE -> Color(0xFF8E24AA)
    DocumentFormatType.UNSUPPORTED -> Color(0xFF607D8B)
}

private fun parseDocxDocument(context: Context, uri: Uri, title: String, size: Long): ParsedDocumentContent {
    val paragraphs = mutableListOf<String>()
    val tables = mutableListOf<List<List<String>>>()
    context.contentResolver.openInputStream(uri)?.use { inStream ->
        ZipInputStream(inStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.bufferedReader().readText()
                    val tableRegex = Regex("<w:tbl[ >](.*?)</w:tbl>", RegexOption.DOT_MATCHES_ALL)
                    tableRegex.findAll(xml).forEach { tableMatch ->
                        val rows = mutableListOf<List<String>>()
                        val rowRegex = Regex("<w:tr[ >](.*?)</w:tr>", RegexOption.DOT_MATCHES_ALL)
                        val cellRegex = Regex("<w:tc[ >](.*?)</w:tc>", RegexOption.DOT_MATCHES_ALL)
                        rowRegex.findAll(tableMatch.value).forEach { rowMatch ->
                            val cells = cellRegex.findAll(rowMatch.value).map { cellMatch ->
                                extractDocxText(cellMatch.value)
                            }.toList()
                            if (cells.isNotEmpty()) rows.add(cells)
                        }
                        if (rows.isNotEmpty()) tables.add(rows)
                    }
                    val bodyWithoutTables = tableRegex.replace(xml, "")
                    val pRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
                    pRegex.findAll(bodyWithoutTables).forEach { pMatch ->
                        val text = extractDocxText(pMatch.value)
                        if (text.isNotBlank()) paragraphs.add(text)
                    }
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
    return ParsedDocumentContent(
        format = DocumentFormatType.DOCX,
        title = title,
        fileSize = size,
        paragraphs = paragraphs,
        docxTables = tables
    )
}

private fun extractDocxText(xmlFragment: String): String {
    val text = Regex("<w:t(?:\\s+[^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
        .findAll(xmlFragment)
        .joinToString("") { it.groupValues[1] }
    return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
}

private fun parseXlsxDocument(context: Context, uri: Uri, title: String, size: Long): ParsedDocumentContent {
    val sharedStrings = mutableListOf<String>()
    var workbookXml = ""
    var workbookRelsXml = ""
    val sheetDataMap = mutableMapOf<String, String>()

    context.contentResolver.openInputStream(uri)?.use { inStream ->
        ZipInputStream(inStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        val xml = zip.bufferedReader().readText()
                        val itemRegex = Regex("<si\\b[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
                        val textRegex = Regex("<t\\b[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                        itemRegex.findAll(xml).forEach { item ->
                            sharedStrings.add(textRegex.findAll(item.groupValues[1])
                                .joinToString("") { decodeOfficeXmlText(it.groupValues[1]) })
                        }
                    }
                    entry.name == "xl/workbook.xml" -> workbookXml = zip.bufferedReader().readText()
                    entry.name == "xl/_rels/workbook.xml.rels" -> workbookRelsXml = zip.bufferedReader().readText()
                    entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml") ->
                        sheetDataMap[entry.name] = zip.bufferedReader().readText()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    } ?: return ParsedDocumentContent(
        format = DocumentFormatType.XLSX,
        title = title,
        fileSize = size,
        spreadsheetSheets = emptyMap()
    )

    fun attribute(attributes: String, name: String): String? {
        val pattern = """(?:^|\s)${Regex.escape(name)}\s*=\s*"([^"]*)""""
        return Regex(pattern).find(attributes)?.groupValues?.get(1)
    }

    fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile { it.isLetter() }.uppercase()
        var result = 0
        for (char in letters) result = result * 26 + (char - 'A' + 1)
        return (result - 1).coerceIn(0, 16383)
    }

    val relationshipTargets = mutableMapOf<String, String>()
    Regex("<Relationship\\b([^>]*)/?>").findAll(workbookRelsXml).forEach { match ->
        val attrs = match.groupValues[1]
        val id = attribute(attrs, "Id") ?: return@forEach
        val target = attribute(attrs, "Target") ?: return@forEach
        relationshipTargets[id] = when {
            target.startsWith("/") -> target.removePrefix("/")
            target.startsWith("xl/") -> target
            else -> "xl/$target"
        }
    }

    val orderedSheets = mutableListOf<Pair<String, String>>()
    Regex("<sheet\\b([^>]*)/?>").findAll(workbookXml).forEach { match ->
        val attrs = match.groupValues[1]
        val name = attribute(attrs, "name")?.let(::decodeOfficeXmlText) ?: "Sheet ${orderedSheets.size + 1}"
        val relationId = attribute(attrs, "r:id") ?: attribute(attrs, "id")
        val target = relationId?.let { relationshipTargets[it] }
        if (target != null) orderedSheets.add(name to target)
    }

    if (orderedSheets.isEmpty()) {
        sheetDataMap.keys.sortedBy { key ->
            Regex("""sheet(\d+)\.xml""").find(key)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }.forEachIndexed { index, key -> orderedSheets.add("Sheet ${index + 1}" to key) }
    }

    val sheetsMap = LinkedHashMap<String, List<List<String>>>()
    orderedSheets.forEach { (sheetName, sheetPath) ->
        val xml = sheetDataMap[sheetPath] ?: return@forEach
        val rows = mutableListOf<List<String>>()
        val rowRegex = Regex("<row\\b([^>]*)>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c\\b([^>]*)>(.*?)</c>|<c\\b([^>]*)/>", RegexOption.DOT_MATCHES_ALL)
        val valueRegex = Regex("<v\\b[^>]*>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
        val inlineTextRegex = Regex("<t\\b[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)

        rowRegex.findAll(xml).forEach { rowMatch ->
            val rowNumber = attribute(rowMatch.groupValues[1], "r")?.toIntOrNull() ?: (rows.size + 1)
            while (rows.size < rowNumber - 1 && rows.size < 1_048_576) rows.add(emptyList())
            val indexedCells = sortedMapOf<Int, String>()

            cellRegex.findAll(rowMatch.groupValues[2]).forEach cellLoop@{ cellMatch ->
                val attrs = if (cellMatch.groupValues[1].isNotEmpty()) cellMatch.groupValues[1] else cellMatch.groupValues[3]
                val cellXml = cellMatch.groupValues[2]
                val reference = attribute(attrs, "r") ?: return@cellLoop
                val column = columnIndex(reference)
                val type = attribute(attrs, "t").orEmpty()
                val raw = if (type == "inlineStr") {
                    inlineTextRegex.findAll(cellXml).joinToString("") { decodeOfficeXmlText(it.groupValues[1]) }
                } else valueRegex.find(cellXml)?.groupValues?.get(1).orEmpty()
                indexedCells[column] = when (type) {
                    "s" -> sharedStrings.getOrNull(raw.toIntOrNull() ?: -1) ?: raw
                    "inlineStr" -> raw
                    "b" -> if (raw == "1") "TRUE" else if (raw == "0") "FALSE" else raw
                    else -> decodeOfficeXmlText(raw)
                }
            }

            val width = (indexedCells.keys.maxOrNull()?.plus(1) ?: 0).coerceAtMost(16384)
            val row = MutableList(width) { "" }
            indexedCells.forEach { (column, value) -> if (column in row.indices) row[column] = value }
            if (rows.size < 1_048_576) rows.add(row)
        }
        sheetsMap[sheetName] = rows
    }

    return ParsedDocumentContent(
        format = DocumentFormatType.XLSX,
        title = title,
        fileSize = size,
        spreadsheetSheets = sheetsMap
    )
}

private fun decodeOfficeXmlText(value: String): String =
    Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()

private fun parsePptxDocument(context: Context, uri: Uri, title: String, size: Long): ParsedDocumentContent {
    val slideMap = mutableMapOf<Int, String>()

    context.contentResolver.openInputStream(uri)?.use { inStream ->
        ZipInputStream(inStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                    val slideNum = entry.name.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0
                    val xml = zip.bufferedReader().readText()
                    val tRegex = Regex("<a:t>(.*?)</a:t>")
                    val slideText = tRegex.findAll(xml).joinToString(" ") { it.groupValues[1] }
                    if (slideText.isNotBlank()) {
                        slideMap[slideNum] = slideText.trim()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    val sortedSlides = slideMap.entries.sortedBy { it.key }.map { it.value }

    return ParsedDocumentContent(
        format = DocumentFormatType.PPTX,
        title = title,
        fileSize = size,
        presentationSlides = sortedSlides
    )
}

private fun parseTxtDocument(context: Context, uri: Uri, title: String, size: Long): ParsedDocumentContent {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    val text = decodeTextWithAutoCharset(bytes)

    val lines = text.split("\n")
    val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

    return ParsedDocumentContent(
        format = DocumentFormatType.TXT,
        title = title,
        fileSize = size,
        rawText = text,
        textLineCount = lines.size,
        textWordCount = words.size,
        textCharCount = text.length
    )
}

private fun parseRtfDocument(context: Context, uri: Uri, title: String, size: Long): ParsedDocumentContent {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    val source = decodeTextWithAutoCharset(bytes)
    val plainText = source
        .replace(Regex("""\\'[0-9a-fA-F]{2}"""), " ")
        .replace(Regex("""\\u(-?\d+)\??""")) { match ->
            val code = match.groupValues[1].toIntOrNull() ?: 32
            String(Character.toChars(if (code < 0) code + 65536 else code))
        }
        .replace(Regex("""\\(par|line)\b ?"""), "\n")
        .replace(Regex("""\\tab\b ?"""), "\t")
        .replace(Regex("""\\[a-zA-Z]+-?\d* ?"""), "")
        .replace(Regex("""\\([\\{}])"""), "$1")
        .replace(Regex("""\\[^a-zA-Z]"""), "")
        .replace(Regex("[{}]"), "")
        .replace(Regex("""[ \t]+\n"""), "\n")
        .trim()
    return createPlainTextContent(DocumentFormatType.RTF, title, size, plainText)
}

private fun parseHtmlDocument(context: Context, uri: Uri, title: String, size: Long): ParsedDocumentContent {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    val source = decodeTextWithAutoCharset(bytes)
    val withoutHiddenBlocks = source
        .replace(Regex("""(?is)<(script|style|noscript)\b[^>]*>.*?</\1>"""), " ")
    val withLineBreaks = withoutHiddenBlocks
        .replace(Regex("""(?i)<\s*br\s*/?>"""), "\n")
        .replace(Regex("""(?i)</(p|div|h[1-6]|li|tr|section|article|blockquote)\s*>"""), "\n")
    val plainText = Html.fromHtml(withLineBreaks, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
    return createPlainTextContent(DocumentFormatType.HTML, title, size, plainText)
}

private fun createPlainTextContent(
    format: DocumentFormatType,
    title: String,
    size: Long,
    text: String
): ParsedDocumentContent {
    val lines = text.split("\n")
    val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return ParsedDocumentContent(
        format = format,
        title = title,
        fileSize = size,
        rawText = text,
        textLineCount = lines.size,
        textWordCount = words.size,
        textCharCount = text.length
    )
}

private fun decodeTextWithAutoCharset(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
    }
    return try {
        String(bytes, Charsets.UTF_8)
    } catch (_: Exception) {
        String(bytes, Charsets.ISO_8859_1)
    }
}

private fun parseLegacyOfficeDocument(
    context: Context,
    uri: Uri,
    title: String,
    size: Long,
    format: DocumentFormatType
): ParsedDocumentContent {
    val extractedStrings = mutableListOf<String>()
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            var currentWord = StringBuilder()
            for (b in bytes) {
                val c = b.toInt().toChar()
                if (c.isLetterOrDigit() || c.isWhitespace() || c in ".,!?-;:'\"()[]{}@#") {
                    currentWord.append(c)
                } else {
                    if (currentWord.length >= 4) {
                        val word = currentWord.toString().trim()
                        if (word.isNotBlank()) extractedStrings.add(word)
                    }
                    currentWord = StringBuilder()
                }
            }
        }
    } catch (_: Exception) {}

    val formatName = when (format) {
        DocumentFormatType.LEGACY_DOC -> "Microsoft Word 97-2003 (.doc)"
        DocumentFormatType.LEGACY_XLS -> "Microsoft Excel 97-2003 (.xls)"
        DocumentFormatType.LEGACY_PPT -> "Microsoft PowerPoint 97-2003 (.ppt)"
        else -> "Legacy Office Binary Format"
    }

    return ParsedDocumentContent(
        format = format,
        title = title,
        fileSize = size,
        rawText = extractedStrings.joinToString(" ").take(1000),
        legacyWarning = "$formatName uses a legacy binary OLE structure rather than modern OpenXML. Use \"Open with App\" for full layout fidelity."
    )
}

private fun queryFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
    } catch (_: Exception) {}
    return name
}

private fun queryFileSize(context: Context, uri: Uri): Long? {
    var size: Long? = null
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx != -1 && cursor.moveToFirst()) {
                size = cursor.getLong(idx)
            }
        }
    } catch (_: Exception) {}
    return size
}
