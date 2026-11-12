package com.example.feature.operations

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FileOpen
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.core.sharing.FileSharingManager
import com.example.engine.core.AdvancedPdfOperationsEngine
import com.example.feature.filemanager.formatFileSize
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressPdfScreen(
    onBackClick: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onRegisterGeneratedFile: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<Long?>(null) }
    var compressionLevel by remember { mutableStateOf("Medium") }
    var outputNameInput by remember { mutableStateOf("Compressed_Document_${System.currentTimeMillis() % 10000}.pdf") }

    var isCompressing by remember { mutableStateOf(false) }
    var resultSuccessFile by remember { mutableStateOf<File?>(null) }
    var originalSizeForReport by remember { mutableStateOf(0L) }

    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            val name = queryDocName(context, uri) ?: "Document.pdf"
            selectedFileName = name
            val size = queryDocSize(context, uri) ?: 0L
            selectedFileSize = size
            originalSizeForReport = size
            val base = name.substringBeforeLast(".")
            outputNameInput = "${base}_compressed.pdf"
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
                        modifier = Modifier.testTag("compress_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Compress PDF Document", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            // Source File Selection Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Choose PDF to Compress", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

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
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(selectedFileName ?: "Selected PDF", fontWeight = FontWeight.SemiBold)
                                    if (selectedFileSize != null) {
                                        Text(
                                            "Original Size: ${formatFileSize(selectedFileSize!!)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) }) {
                                    Text("Change")
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("select_pdf_for_compression_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF from Device")
                        }
                    }
                }
            }

            // Compression Level Options
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("2. Compression Preset", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    listOf(
                        Triple("Low", "High Quality", "Minimal compression, preserves fine graphics and typography."),
                        Triple("Medium", "Balanced (Recommended)", "Optimal balance between small file size and document clarity."),
                        Triple("High", "Maximum Compression", "Smallest file size, ideal for email and messaging attachments.")
                    ).forEach { (level, title, desc) ->
                        val isSelected = compressionLevel.equals(level, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 2.dp else 0.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { compressionLevel = level }
                                .testTag("compress_preset_${level.lowercase()}")
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Output Name Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("3. Output File Name", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = outputNameInput,
                        onValueChange = { outputNameInput = it },
                        label = { Text("Compressed PDF Name") },
                        modifier = Modifier.fillMaxWidth().testTag("compress_output_name_input")
                    )
                }
            }

            // Action Button
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    isCompressing = true
                    scope.launch {
                        try {
                            val result = AdvancedPdfOperationsEngine.compressPdf(context, uri, compressionLevel, outputNameInput)
                            onRegisterGeneratedFile(Uri.fromFile(result), result.name)
                            resultSuccessFile = result
                        } catch (e: Exception) {
                            Toast.makeText(context, "Compression failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isCompressing = false
                        }
                    }
                },
                enabled = selectedUri != null && !isCompressing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("execute_compress_pdf_button")
            ) {
                if (isCompressing) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Compressing PDF...")
                } else {
                    Icon(Icons.Default.Compress, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compress PDF Now")
                }
            }
        }
    }

    // Success Modal with UniversalResultDialog
    if (resultSuccessFile != null) {
        val file = resultSuccessFile!!
        val newSize = file.length()
        val orig = if (originalSizeForReport > 0) originalSizeForReport else newSize
        val savings = if (orig > newSize) {
            val pct = ((orig - newSize).toDouble() / orig.toDouble()) * 100
            "Saved ${String.format("%.1f", pct)}%"
        } else {
            "Optimized"
        }

        com.example.ui.components.UniversalResultDialog(
            title = "PDF Compressed Successfully!",
            file = file,
            additionalDetails = listOf(
                "Original Size" to formatFileSize(orig),
                "Compressed Size" to formatFileSize(newSize),
                "Compression Ratio" to savings,
                "Preset" to compressionLevel
            ),
            onDismiss = { resultSuccessFile = null },
            onOpenFile = {
                val uri = Uri.fromFile(file)
                resultSuccessFile = null
                onOpenPdf(uri, file.name)
            },
            onAnotherAction = {
                resultSuccessFile = null
                selectedUri = null
                selectedFileName = null
            },
            anotherActionLabel = "Compress Another"
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
