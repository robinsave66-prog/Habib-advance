package com.example.feature.operations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.widget.Toast
import com.example.engine.pdfbox.PdfSignatureStampEngine
import java.io.ByteArrayOutputStream
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.UniversalResultDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

data class BézierStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val alpha: Float = 1.0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPdfScreen(
    onBackClick: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onRegisterGeneratedFile: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Mode: 0 = Draw / Studio, 1 = Stamp onto PDF
    var currentTab by remember { mutableIntStateOf(0) }

    // Strokes & History for Undo/Redo
    val strokes = remember { mutableStateListOf<BézierStroke>() }
    val redoStack = remember { mutableStateListOf<BézierStroke>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }

    // Customization states
    var strokeColor by remember { mutableStateOf(Color(0xFF0F3B66)) }
    var strokeWidthPx by remember { mutableFloatStateOf(6f) }
    var strokeAlpha by remember { mutableFloatStateOf(1.0f) }

    // Signature Library (stored transparent PNG files)
    val signatureLibrary = remember { mutableStateListOf<File>() }

    fun refreshSignatureLibrary() {
        val libDir = File(context.filesDir, "signatures").apply { if (!exists()) mkdirs() }
        signatureLibrary.clear()
        signatureLibrary.addAll(libDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }?.sortedByDescending { it.lastModified() } ?: emptyList())
    }

    LaunchedEffect(Unit) {
        refreshSignatureLibrary()
    }

    // PDF Stamp Selection
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf<String?>(null) }
    var targetPageToSign by remember { mutableIntStateOf(1) }
    var signedOutputName by remember { mutableStateOf("Signed_Document_${System.currentTimeMillis() % 10000}.pdf") }
    var isSigning by remember { mutableStateOf(false) }
    var selectedSignatureFile by remember { mutableStateOf<File?>(null) }
    var signatureHorizontalPosition by remember { mutableFloatStateOf(0.78f) }
    var signatureVerticalPosition by remember { mutableFloatStateOf(0.08f) }
    var signatureWidthFraction by remember { mutableFloatStateOf(0.38f) }

    // Universal Result Dialog state
    var resultDialogFile by remember { mutableStateOf<File?>(null) }
    var resultDialogMime by remember { mutableStateOf("application/pdf") }
    var resultDialogTitle by remember { mutableStateOf("Document Signed!") }

    // SAF Document Picker for PDF to Sign
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPdfUri = uri
            val name = queryDocName(context, uri) ?: "Document.pdf"
            selectedPdfName = name
            val base = name.substringBeforeLast(".")
            signedOutputName = "${base}_signed.pdf"
        }
    }

    // SAF CreateDocument Launcher for saving Transparent PNG directly to user's device
    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destUri: Uri? ->
        if (destUri != null && strokes.isNotEmpty()) {
            scope.launch {
                try {
                    val bitmap = renderSmoothStrokesToBitmap(strokes, 600, 300)
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                    Toast.makeText(context, "Transparent PNG signature saved to device!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error saving PNG: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        modifier = Modifier.testTag("sign_pdf_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Signature Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                },
                actions = {
                    // Save as Transparent PNG to Device SAF
                    IconButton(
                        onClick = {
                            if (strokes.isEmpty()) {
                                Toast.makeText(context, "Please draw a signature first", Toast.LENGTH_SHORT).show()
                            } else {
                                exportPngLauncher.launch("signature_${System.currentTimeMillis() % 10000}.png")
                            }
                        },
                        modifier = Modifier.testTag("export_signature_png_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Save PNG to Device")
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
        ) {
            TabRow(selectedTabIndex = currentTab) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Text("Signature Studio") },
                    icon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_signature_studio")
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Text("Stamp on PDF") },
                    icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_stamp_pdf")
                )
            }

            if (currentTab == 0) {
                // TAB 0: SIGNATURE STUDIO & DRAWING
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Canvas Card
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
                                Text("Signature Canvas (Smooth Bézier):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                // Undo / Redo / Clear Row
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            if (strokes.isNotEmpty()) {
                                                val removed = strokes.removeAt(strokes.lastIndex)
                                                redoStack.add(removed)
                                            }
                                        },
                                        enabled = strokes.isNotEmpty(),
                                        modifier = Modifier.size(36.dp).testTag("undo_stroke_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (redoStack.isNotEmpty()) {
                                                val restored = redoStack.removeAt(redoStack.lastIndex)
                                                strokes.add(restored)
                                            }
                                        },
                                        enabled = redoStack.isNotEmpty(),
                                        modifier = Modifier.size(36.dp).testTag("redo_stroke_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            strokes.clear()
                                            redoStack.clear()
                                            currentPoints.clear()
                                        },
                                        modifier = Modifier.size(36.dp).testTag("clear_signature_button")
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            // Interactive Canvas
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFAFAFA))
                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .pointerInput(strokeColor, strokeWidthPx, strokeAlpha) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentPoints.clear()
                                                currentPoints.add(offset)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentPoints.add(change.position)
                                            },
                                            onDragEnd = {
                                                if (currentPoints.isNotEmpty()) {
                                                    strokes.add(
                                                        BézierStroke(
                                                            points = currentPoints.toList(),
                                                            color = strokeColor,
                                                            strokeWidth = strokeWidthPx,
                                                            alpha = strokeAlpha
                                                        )
                                                    )
                                                    redoStack.clear()
                                                    currentPoints.clear()
                                                }
                                            }
                                        )
                                    }
                                    .testTag("signature_canvas")
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    // Draw completed strokes with smooth Bézier interpolation
                                    for (stroke in strokes) {
                                        drawBezierPath(stroke.points, stroke.color.copy(alpha = stroke.alpha), stroke.strokeWidth)
                                    }
                                    // Draw current active stroke
                                    if (currentPoints.size > 1) {
                                        drawBezierPath(currentPoints.toList(), strokeColor.copy(alpha = strokeAlpha), strokeWidthPx)
                                    }
                                }
                            }

                            // Color Palette Row
                            Text("Ink Color:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val palette = listOf(
                                    Color(0xFF0F3B66), // Classic Navy
                                    Color.Black,
                                    Color(0xFF1E3A8A), // Deep Royal
                                    Color(0xFFDC2626), // Red
                                    Color(0xFF16A34A), // Green
                                    Color(0xFF9333EA), // Purple
                                    Color(0xFF475569)  // Slate
                                )
                                palette.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (strokeColor == color) 2.5.dp else 1.dp,
                                                color = if (strokeColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = CircleShape
                                            )
                                            .clickable { strokeColor = color }
                                    )
                                }
                            }

                            // Stroke Width Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Stroke Width: ${strokeWidthPx.toInt()} px", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = strokeWidthPx,
                                onValueChange = { strokeWidthPx = it },
                                valueRange = 2f..16f
                            )

                            // Transparency Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ink Opacity: ${(strokeAlpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = strokeAlpha,
                                onValueChange = { strokeAlpha = it },
                                valueRange = 0.2f..1.0f
                            )

                            // Actions: Save to Library & Save as Transparent PNG
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (strokes.isEmpty()) {
                                            Toast.makeText(context, "Sign first before saving", Toast.LENGTH_SHORT).show()
                                        } else {
                                            scope.launch {
                                                val libDir = File(context.filesDir, "signatures").apply { if (!exists()) mkdirs() }
                                                val sigFile = File(libDir, "Signature_${System.currentTimeMillis() % 10000}.png")
                                                val bitmap = renderSmoothStrokesToBitmap(strokes, 500, 250)
                                                FileOutputStream(sigFile).use { out ->
                                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                                }
                                                bitmap.recycle()
                                                refreshSignatureLibrary()
                                                Toast.makeText(context, "Saved to Signature Library!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("save_to_library_button")
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save to Library")
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (strokes.isEmpty()) {
                                            Toast.makeText(context, "Sign first before saving", Toast.LENGTH_SHORT).show()
                                        } else {
                                            exportPngLauncher.launch("signature_transparent.png")
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("save_transparent_png_button")
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save PNG (SAF)")
                                }
                            }
                        }
                    }

                    // Signature Library Shelf
                    if (signatureLibrary.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("In-App Signature Library (${signatureLibrary.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(signatureLibrary) { file ->
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            modifier = Modifier.size(100.dp, 60.dp).clickable {
                                                selectedSignatureFile = file
                                                currentTab = 1
                                                Toast.makeText(context, "Selected ${file.nameWithoutExtension} for PDF", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(file.nameWithoutExtension, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 1: STAMP ON PDF WORKFLOW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("1. Choose Target PDF:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                            if (selectedPdfUri != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(selectedPdfName ?: "Document.pdf", fontWeight = FontWeight.SemiBold)
                                        Text("Selected for digital stamp", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Button(
                                onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.fillMaxWidth().testTag("pick_pdf_to_sign_button")
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (selectedPdfUri == null) "Select PDF Document" else "Change PDF Document")
                            }

                            // Page selection to place signature
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Page to Stamp Signature:", fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (targetPageToSign > 1) targetPageToSign-- }) {
                                        Text("-", fontWeight = FontWeight.Bold)
                                    }
                                    Text("Page $targetPageToSign", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { targetPageToSign++ }) {
                                        Text("+", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Text("Signature Position & Size", fontWeight = FontWeight.SemiBold)
                            Text("Horizontal: ${(signatureHorizontalPosition * 100).toInt()}%")
                            Slider(value = signatureHorizontalPosition, onValueChange = { signatureHorizontalPosition = it }, valueRange = 0f..1f, modifier = Modifier.testTag("signature_horizontal_position"))
                            Text("Vertical from bottom: ${(signatureVerticalPosition * 100).toInt()}%")
                            Slider(value = signatureVerticalPosition, onValueChange = { signatureVerticalPosition = it }, valueRange = 0f..1f, modifier = Modifier.testTag("signature_vertical_position"))
                            Text("Signature width: ${(signatureWidthFraction * 100).toInt()}% of page")
                            Slider(value = signatureWidthFraction, onValueChange = { signatureWidthFraction = it }, valueRange = 0.10f..0.70f, modifier = Modifier.testTag("signature_width_fraction"))

                            OutlinedTextField(
                                value = signedOutputName,
                                onValueChange = { signedOutputName = it },
                                label = { Text("Output PDF Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Execute Sign Button
                    Button(
                        onClick = {
                            if (strokes.isEmpty() && selectedSignatureFile == null) {
                                Toast.makeText(context, "Draw a signature or select one from the Signature Library.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val uri = selectedPdfUri
                            if (uri == null) {
                                Toast.makeText(context, "Please select a target PDF document", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isSigning = true
                            scope.launch {
                                try {
                                    val signatureBytes = selectedSignatureFile?.takeIf { strokes.isEmpty() }?.readBytes()
                                        ?: ByteArrayOutputStream().use { output ->
                                            val bitmap = renderSmoothStrokesToBitmap(strokes, 500, 250)
                                            try {
                                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                                                output.toByteArray()
                                            } finally { bitmap.recycle() }
                                        }
                                    val result = PdfSignatureStampEngine(context).stamp(
                                        sourceUri = uri,
                                        signaturePng = signatureBytes,
                                        targetPageOneBased = targetPageToSign,
                                        outputFileName = signedOutputName,
                                        horizontalPosition = signatureHorizontalPosition,
                                        verticalPosition = signatureVerticalPosition,
                                        widthFraction = signatureWidthFraction
                                    )
                                    onRegisterGeneratedFile(Uri.fromFile(result), result.name)
                                    resultDialogFile = result
                                    resultDialogMime = "application/pdf"
                                    resultDialogTitle = "PDF Signed Successfully!"
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Signing failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSigning = false
                                }
                            }
                        },
                        enabled = selectedPdfUri != null && !isSigning,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("execute_sign_pdf_button")
                    ) {
                        if (isSigning) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Applying Signature Stamp...")
                        } else {
                            Icon(Icons.Default.Draw, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply Signature & Export PDF")
                        }
                    }
                }
            }
        }
    }

    // Universal Result Screen
    if (resultDialogFile != null) {
        val file = resultDialogFile!!
        UniversalResultDialog(
            title = resultDialogTitle,
            file = file,
            onDismiss = { resultDialogFile = null },
            onOpenFile = {
                val f = file
                resultDialogFile = null
                onOpenPdf(Uri.fromFile(f), f.name)
            },
            onAnotherAction = {
                resultDialogFile = null
                strokes.clear()
                redoStack.clear()
                selectedPdfUri = null
            },
            anotherActionLabel = "Sign Another"
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBezierPath(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float
) {
    if (points.size < 2) return
    val path = androidx.compose.ui.graphics.Path()
    path.moveTo(points[0].x, points[0].y)

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val midX = (prev.x + cur.x) / 2f
        val midY = (prev.y + cur.y) / 2f
        path.quadraticTo(prev.x, prev.y, midX, midY)
    }
    path.lineTo(points.last().x, points.last().y)

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun renderSmoothStrokesToBitmap(strokes: List<BézierStroke>, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    for (s in strokes) {
        if (s.points.size > 1) {
            paint.color = s.color.hashCode()
            paint.alpha = (s.alpha * 255).toInt().coerceIn(0, 255)
            paint.strokeWidth = s.strokeWidth

            val path = Path()
            path.moveTo(s.points[0].x, s.points[0].y)
            for (i in 1 until s.points.size) {
                val prev = s.points[i - 1]
                val cur = s.points[i]
                path.quadTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
            }
            path.lineTo(s.points.last().x, s.points.last().y)
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
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
