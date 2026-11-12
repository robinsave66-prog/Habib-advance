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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateListOf
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
import com.example.core.sharing.FileSharingManager
import com.example.engine.core.AdvancedPdfOperationsEngine
import com.example.feature.filemanager.formatFileSize
import kotlinx.coroutines.launch
import java.io.File

enum class MergeSplitMode {
    MERGE,
    SPLIT
}

data class MergeItem(
    val uri: Uri,
    val name: String,
    val size: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeSplitPdfScreen(
    initialMode: MergeSplitMode = MergeSplitMode.MERGE,
    onBackClick: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onRegisterGeneratedFile: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf(initialMode) }

    // Merge State
    val mergeItems = remember { mutableStateListOf<MergeItem>() }
    var mergeOutputName by remember { mutableStateOf("Merged_Document_${System.currentTimeMillis() % 10000}.pdf") }

    // Split State
    var splitSourceUri by remember { mutableStateOf<Uri?>(null) }
    var splitSourceName by remember { mutableStateOf<String?>(null) }
    var splitPageRangeInput by remember { mutableStateOf("1-3") }
    var splitOutputName by remember { mutableStateOf("Split_Document_${System.currentTimeMillis() % 10000}.pdf") }

    var isProcessing by remember { mutableStateOf(false) }
    var resultSuccessFile by remember { mutableStateOf<File?>(null) }

    // Pick PDF for Merge
    val pickMergePdfs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        for (u in uris) {
            val name = queryDocName(context, u) ?: "Document_${mergeItems.size + 1}.pdf"
            val size = queryDocSize(context, u) ?: 0L
            mergeItems.add(MergeItem(u, name, size))
        }
    }

    // Pick PDF for Split
    val pickSplitPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            splitSourceUri = uri
            splitSourceName = queryDocName(context, uri) ?: "Source.pdf"
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
                        modifier = Modifier.testTag("merge_split_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = if (currentMode == MergeSplitMode.MERGE) "Merge PDF Files" else "Split PDF Document",
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
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = if (currentMode == MergeSplitMode.MERGE) 0 else 1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = currentMode == MergeSplitMode.MERGE,
                    onClick = { currentMode = MergeSplitMode.MERGE },
                    text = { Text("Merge PDFs") },
                    icon = { Icon(Icons.Default.Merge, contentDescription = null) },
                    modifier = Modifier.testTag("tab_merge_pdf")
                )
                Tab(
                    selected = currentMode == MergeSplitMode.SPLIT,
                    onClick = { currentMode = MergeSplitMode.SPLIT },
                    text = { Text("Split PDF") },
                    icon = { Icon(Icons.Default.CallSplit, contentDescription = null) },
                    modifier = Modifier.testTag("tab_split_pdf")
                )
            }

            if (currentMode == MergeSplitMode.MERGE) {
                // MERGE VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PDF Queue (${mergeItems.size} files)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { pickMergePdfs.launch(arrayOf("application/pdf")) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_pdf_to_merge_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add PDFs")
                        }
                    }

                    if (mergeItems.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Merge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No PDFs Selected", fontWeight = FontWeight.Bold)
                                Text(
                                    "Select 2 or more PDF documents to merge into a single continuous file.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(mergeItems) { index, item ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = formatFileSize(item.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        // Move up / down / remove
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val temp = mergeItems[index]
                                                    mergeItems[index] = mergeItems[index - 1]
                                                    mergeItems[index - 1] = temp
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (index < mergeItems.size - 1) {
                                                    val temp = mergeItems[index]
                                                    mergeItems[index] = mergeItems[index + 1]
                                                    mergeItems[index + 1] = temp
                                                }
                                            },
                                            enabled = index < mergeItems.size - 1,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { mergeItems.removeAt(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = mergeOutputName,
                        onValueChange = { mergeOutputName = it },
                        label = { Text("Merged File Name") },
                        modifier = Modifier.fillMaxWidth().testTag("merge_output_name_input")
                    )

                    Button(
                        onClick = {
                            if (mergeItems.size < 2) {
                                Toast.makeText(context, "Please select at least 2 PDFs to merge", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProcessing = true
                            scope.launch {
                                try {
                                    val uris = mergeItems.map { it.uri }
                                    val result = AdvancedPdfOperationsEngine.mergePdfs(context, uris, mergeOutputName)
                                    onRegisterGeneratedFile(Uri.fromFile(result), result.name)
                                    resultSuccessFile = result
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Merge failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = mergeItems.size >= 2 && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("execute_merge_pdf_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Merging PDFs...")
                        } else {
                            Icon(Icons.Default.Merge, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Merge ${mergeItems.size} PDFs")
                        }
                    }
                }
            } else {
                // SPLIT VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("1. Select PDF to Split", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            if (splitSourceUri != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(splitSourceName ?: "PDF Document", fontWeight = FontWeight.SemiBold)
                                        }
                                        TextButton(onClick = { pickSplitPdf.launch(arrayOf("application/pdf")) }) {
                                            Text("Change")
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { pickSplitPdf.launch(arrayOf("application/pdf")) },
                                    modifier = Modifier.fillMaxWidth().testTag("select_split_pdf_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Choose PDF to Split")
                                }
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("2. Page Range to Extract", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Specify pages to extract, e.g.: 1-3 (pages 1 to 3), or 1,3,5 (individual pages).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = splitPageRangeInput,
                                onValueChange = { splitPageRangeInput = it },
                                label = { Text("Page Range") },
                                placeholder = { Text("e.g. 1-2 or 1,3") },
                                modifier = Modifier.fillMaxWidth().testTag("split_range_input")
                            )
                            OutlinedTextField(
                                value = splitOutputName,
                                onValueChange = { splitOutputName = it },
                                label = { Text("Output PDF Name") },
                                modifier = Modifier.fillMaxWidth().testTag("split_output_name_input")
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val uri = splitSourceUri ?: return@Button
                            val indices = parsePageRange(splitPageRangeInput)
                            if (indices.isEmpty()) {
                                Toast.makeText(context, "Invalid page range (e.g. 1-3 or 1,2)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProcessing = true
                            scope.launch {
                                try {
                                    val result = AdvancedPdfOperationsEngine.splitPdf(context, uri, indices, splitOutputName)
                                    onRegisterGeneratedFile(Uri.fromFile(result), result.name)
                                    resultSuccessFile = result
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Split failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = splitSourceUri != null && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("execute_split_pdf_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extracting Pages...")
                        } else {
                            Icon(Icons.Default.CallSplit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract & Save Pages")
                        }
                    }
                }
            }
        }
    }

    // Success Modal with UniversalResultDialog
    if (resultSuccessFile != null) {
        val file = resultSuccessFile!!
        val isMerge = currentMode == MergeSplitMode.MERGE
        com.example.ui.components.UniversalResultDialog(
            title = if (isMerge) "PDFs Merged Successfully!" else "Pages Extracted Successfully!",
            file = file,
            additionalDetails = listOf(
                "Operation" to if (isMerge) "Merged (${mergeItems.size} documents)" else "Extracted Pages",
                "Format" to "PDF Document"
            ),
            onDismiss = { resultSuccessFile = null },
            onOpenFile = {
                val uri = Uri.fromFile(file)
                resultSuccessFile = null
                onOpenPdf(uri, file.name)
            },
            onAnotherAction = {
                resultSuccessFile = null
                if (isMerge) {
                    mergeItems.clear()
                } else {
                    splitSourceUri = null
                    splitSourceName = null
                }
            },
            anotherActionLabel = if (isMerge) "Merge More" else "Split More"
        )
    }
}

private fun parsePageRange(rangeStr: String): List<Int> {
    val list = mutableListOf<Int>()
    val parts = rangeStr.split(",")
    for (p in parts) {
        val trimmed = p.trim()
        if (trimmed.contains("-")) {
            val sub = trimmed.split("-")
            if (sub.size == 2) {
                val start = sub[0].trim().toIntOrNull()
                val end = sub[1].trim().toIntOrNull()
                if (start != null && end != null) {
                    for (i in start..end) {
                        if (i > 0) list.add(i - 1) // 0-indexed
                    }
                }
            }
        } else {
            val single = trimmed.toIntOrNull()
            if (single != null && single > 0) {
                list.add(single - 1)
            }
        }
    }
    return list.distinct()
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
