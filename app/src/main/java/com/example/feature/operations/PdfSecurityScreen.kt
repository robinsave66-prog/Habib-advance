package com.example.feature.operations

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.engine.pdfbox.PdfBoxDocumentEngine
import kotlinx.coroutines.launch
import java.io.File

private enum class PdfSecurityMode { PROTECT, UNLOCK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSecurityScreen(
    onBackClick: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onRegisterGeneratedFile: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(PdfSecurityMode.PROTECT) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf<String?>(null) }
    var outputName by remember { mutableStateOf("Protected_Document.pdf") }
    var userPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var resultFile by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            sourceUri = uri
            sourceName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected PDF"
            val base = sourceName.orEmpty().substringBeforeLast('.').ifBlank { "Document" }
            outputName = if (mode == PdfSecurityMode.PROTECT) "Protected_$base.pdf" else "Unlocked_$base.pdf"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose an operation", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == PdfSecurityMode.PROTECT,
                            onClick = {
                                mode = PdfSecurityMode.PROTECT
                                outputName = "Protected_Document.pdf"
                                userPassword = ""
                                ownerPassword = ""
                                currentPassword = ""
                            },
                            label = { Text("Protect PDF") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                        )
                        FilterChip(
                            selected = mode == PdfSecurityMode.UNLOCK,
                            onClick = {
                                mode = PdfSecurityMode.UNLOCK
                                outputName = "Unlocked_Document.pdf"
                                userPassword = ""
                                ownerPassword = ""
                                currentPassword = ""
                            },
                            label = { Text("Unlock PDF") },
                            leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                        )
                    }
                    Button(
                        onClick = { picker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth().testTag("pdf_security_pick_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (sourceUri == null) "Choose PDF File" else "Change PDF: ${sourceName ?: "Selected"}")
                    }
                    if (sourceUri != null) {
                        Text("Selected: ${sourceName ?: "PDF"}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mode == PdfSecurityMode.PROTECT) {
                        Text("Set document passwords", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = userPassword,
                            onValueChange = { userPassword = it },
                            label = { Text("User password (opens the PDF)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("pdf_security_user_password")
                        )
                        OutlinedTextField(
                            value = ownerPassword,
                            onValueChange = { ownerPassword = it },
                            label = { Text("Owner password (controls permissions)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("pdf_security_owner_password")
                        )
                        Text(
                            "Both passwords must contain at least 6 characters and must be different. This uses standard PDF password protection; it is not a substitute for secure file sharing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Remove existing password", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Current PDF password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("pdf_security_current_password")
                        )
                        Text(
                            "You must know a valid password to unlock the PDF. This tool cannot bypass unknown passwords.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = outputName,
                        onValueChange = { outputName = it },
                        label = { Text("Output file name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pdf_security_output_name")
                    )
                }
            }

            Button(
                onClick = {
                    val uri = sourceUri ?: return@Button
                    isProcessing = true
                    scope.launch {
                        try {
                            val engine = PdfBoxDocumentEngine(context)
                            val file = if (mode == PdfSecurityMode.PROTECT) {
                                engine.protectPdf(uri, userPassword, ownerPassword, outputName)
                            } else {
                                engine.removePassword(uri, currentPassword, outputName)
                            }
                            onRegisterGeneratedFile(Uri.fromFile(file), file.name)
                            resultFile = file
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: "PDF security operation failed.", Toast.LENGTH_LONG).show()
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                enabled = sourceUri != null && !isProcessing &&
                    if (mode == PdfSecurityMode.PROTECT) userPassword.length >= 6 && ownerPassword.length >= 6 && userPassword != ownerPassword
                    else currentPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pdf_security_execute_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Processing…")
                } else {
                    Text(if (mode == PdfSecurityMode.PROTECT) "Protect PDF" else "Unlock PDF")
                }
            }
        }
    }

    resultFile?.let { file ->
        AlertDialog(
            onDismissRequest = { resultFile = null },
            title = { Text(if (mode == PdfSecurityMode.PROTECT) "PDF Protected" else "PDF Unlocked") },
            text = { Text("Created: ${file.name}") },
            confirmButton = {
                Button(onClick = {
                    resultFile = null
                    onOpenPdf(Uri.fromFile(file), file.name)
                }) { Text("Open PDF") }
            },
            dismissButton = {
                TextButton(onClick = { resultFile = null }) { Text("Close") }
            }
        )
    }
}
