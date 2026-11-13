package com.example.feature.pdfeditor

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.engine.editor.DetectedPdfWord
import com.example.engine.editor.EditorElement
import com.example.engine.editor.ImageElement
import com.example.engine.editor.ShapeElement
import com.example.engine.editor.ShapeType
import com.example.engine.editor.SignatureElement
import com.example.engine.editor.TextElement
import com.example.ui.components.UniversalResultDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    documentUri: Uri,
    documentName: String? = null,
    viewModel: PdfEditorViewModel,
    onBackClick: () -> Unit,
    onOpenExportedPdf: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val pageDimensions by viewModel.pageDimensions.collectAsStateWithLifecycle()
    val elementsMap by viewModel.elements.collectAsStateWithLifecycle()
    val selectedElementId by viewModel.selectedElementId.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val interactionMode by viewModel.interactionMode.collectAsStateWithLifecycle()
    val selectedWords by viewModel.selectedWords.collectAsStateWithLifecycle()

    var showSaveDialog by remember { mutableStateOf(false) }
    var showPageOperationsDialog by remember { mutableStateOf(false) }
    var saveFileNameInput by remember { mutableStateOf("Edited_${documentName ?: "Document.pdf"}") }

    // Text Insertion / Edit Dialog State
    var showTextDialog by remember { mutableStateOf(false) }
    var isEditingExistingElement by remember { mutableStateOf(false) }
    var editingElementId by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    var textSizePt by remember { mutableFloatStateOf(16f) }
    var textColor by remember { mutableStateOf(0xFF000000) }
    var textBgColor by remember { mutableStateOf<Long?>(null) }
    var textBold by remember { mutableStateOf(false) }
    var textItalic by remember { mutableStateOf(false) }
    var textUnderline by remember { mutableStateOf(false) }
    var textAlign by remember { mutableStateOf("LEFT") }

    // Shape Dialog State
    var showShapeDialog by remember { mutableStateOf(false) }
    var selectedShapeType by remember { mutableStateOf(ShapeType.RECTANGLE) }
    var shapeStrokeColor by remember { mutableStateOf(0xFF0F3B66) }
    var shapeFillColor by remember { mutableStateOf<Long?>(null) }

    // Signature Dialog State
    var showSignatureDialog by remember { mutableStateOf(false) }
    var signaturePoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var signatureInkColor by remember { mutableStateOf(0xFF0F3B66) }

    // Freehand Pen Tool State
    var penStrokePoints = remember { mutableStateListOf<Pair<Float, Float>>() }

    // Canvas Zoom & Pan State
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Disallow background panning while an element is being actively dragged/resized
    var isElementInteractionActive by remember { mutableStateOf(false) }

    // Image Picker for inserting photos
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val imgElem = ImageElement(
                pageIndex = currentPage,
                x = 80f,
                y = 120f,
                width = 200f,
                height = 200f,
                imageUri = uri.toString()
            )
            viewModel.addElement(imgElem)
        }
    }

    val mergePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { selectedUris ->
        if (selectedUris.size >= 2) {
            viewModel.mergePdfDocuments(selectedUris)
        } else if (selectedUris.isNotEmpty()) {
            Toast.makeText(context, "Select at least two PDF files.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(documentUri) {
        viewModel.loadDocument(documentUri, documentName)
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
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = documentName ?: "PDF Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (selectedWords.isNotEmpty()) "${selectedWords.size} words selected" else "Page ${currentPage + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Undo
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo(),
                        modifier = Modifier.testTag("editor_undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }

                    // Redo
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo(),
                        modifier = Modifier.testTag("editor_redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }

                    // Paste anywhere if clipboard has text or element
                    IconButton(
                        onClick = {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipItem = clipMgr.primaryClip?.getItemAt(0)?.text?.toString()
                            viewModel.pasteElementAt(x = 80f, y = 140f)
                            if (clipItem != null && !viewModel.hasClipboard()) {
                                viewModel.pasteTextAtPosition(80f, 140f, clipItem)
                            }
                            Toast.makeText(context, "Pasted at position", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("editor_paste_button")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }

                    TextButton(
                        onClick = { showPageOperationsDialog = true },
                        modifier = Modifier.testTag("editor_page_operations_button")
                    ) {
                        Text("Pages")
                    }

                    // Save / Export
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save and Export")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
            ) {
                // 1. Contextual Selected Element Ribbon (when an overlay object is selected)
                val currentElements = elementsMap[currentPage].orEmpty()
                val selectedItem = currentElements.find { it.id == selectedElementId }

                AnimatedVisibility(visible = selectedItem != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Selected: ${selectedItem?.javaClass?.simpleName?.replace("Element", "")}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (selectedItem is TextElement) {
                                    IconButton(
                                        onClick = {
                                            isEditingExistingElement = true
                                            editingElementId = selectedItem.id
                                            textInput = selectedItem.text
                                            textSizePt = selectedItem.fontSizePt
                                            textColor = selectedItem.textColor
                                            textBgColor = selectedItem.backgroundColor
                                            textBold = selectedItem.isBold
                                            textItalic = selectedItem.isItalic
                                            textUnderline = selectedItem.isUnderline
                                            textAlign = selectedItem.textAlign
                                            showTextDialog = true
                                        },
                                        modifier = Modifier.size(32.dp).testTag("ribbon_edit_text_button")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Text", modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { selectedElementId?.let { viewModel.copyElement(it) } },
                                    modifier = Modifier.size(32.dp).testTag("ribbon_copy_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = {
                                        selectedElementId?.let {
                                            val curRot = selectedItem?.rotationDegrees ?: 0f
                                            viewModel.updateElementRotation(it, (curRot + 90f) % 360f)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp).testTag("ribbon_rotate_button")
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { selectedElementId?.let { viewModel.duplicateElement(it) } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { selectedElementId?.let { viewModel.bringForward(it) } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.FlipToFront, contentDescription = "Bring Forward", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { selectedElementId?.let { viewModel.sendBackward(it) } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.FlipToBack, contentDescription = "Send Backward", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSelectedElement() },
                                    modifier = Modifier.size(32.dp).testTag("ribbon_delete_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // 1.5. 5 Distinct Interaction Modes Segmented Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple(EditorInteractionMode.SELECT, "Select", Icons.Default.NearMe),
                            Triple(EditorInteractionMode.TEXT_EDIT, "Text Edit", Icons.Default.TextFields),
                            Triple(EditorInteractionMode.IMAGE_EDIT, "Image", Icons.Default.Image),
                            Triple(EditorInteractionMode.SHAPE_ANNOTATION, "Annotate", Icons.Default.Category),
                            Triple(EditorInteractionMode.PAGE_NAV, "Page Nav", Icons.Default.FitScreen)
                        ).forEach { (mode, label, icon) ->
                            val isSelected = interactionMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setInteractionMode(mode) }
                                    .testTag("mode_${mode.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Main Tool Palette Ribbon
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorToolButton(
                            title = "Select",
                            icon = Icons.Default.NearMe,
                            isSelected = activeTool == ActiveEditorTool.SELECT,
                            onClick = { viewModel.setActiveTool(ActiveEditorTool.SELECT) },
                            testTag = "tool_select"
                        )
                        EditorToolButton(
                            title = "Text",
                            icon = Icons.Default.TextFields,
                            isSelected = activeTool == ActiveEditorTool.TEXT,
                            onClick = {
                                isEditingExistingElement = false
                                editingElementId = null
                                textInput = ""
                                textSizePt = 16f
                                textColor = 0xFF000000
                                textBgColor = null
                                showTextDialog = true
                            },
                            testTag = "tool_text"
                        )
                        EditorToolButton(
                            title = "Image",
                            icon = Icons.Default.Image,
                            isSelected = activeTool == ActiveEditorTool.IMAGE,
                            onClick = {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            testTag = "tool_image"
                        )
                        EditorToolButton(
                            title = "Shapes",
                            icon = Icons.Default.Category,
                            isSelected = activeTool == ActiveEditorTool.SHAPE,
                            onClick = { showShapeDialog = true },
                            testTag = "tool_shape"
                        )
                        EditorToolButton(
                            title = "Draw",
                            icon = Icons.Default.Draw,
                            isSelected = activeTool == ActiveEditorTool.PEN,
                            onClick = { viewModel.setActiveTool(ActiveEditorTool.PEN) },
                            testTag = "tool_pen"
                        )
                        EditorToolButton(
                            title = "Signature",
                            icon = Icons.Default.Draw,
                            isSelected = activeTool == ActiveEditorTool.SIGNATURE,
                            onClick = { showSignatureDialog = true },
                            testTag = "tool_sign"
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE2E8F0))
        ) {
            when (val s = status) {
                is EditorUiStatus.Exporting -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(s.progress, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is EditorUiStatus.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = s.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is EditorUiStatus.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error Opening Document",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = s.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBackClick) { Text("Go Back") }
                    }
                }
                is EditorUiStatus.Loaded -> {
                    val docInfo = s.documentInfo
                    val totalPages = docInfo.pageCount

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Quick Zoom & Navigation Control Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Page Navigation
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { viewModel.setPage(currentPage - 1) },
                                        enabled = currentPage > 0,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Prev")
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${currentPage + 1} / $totalPages",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.setPage(currentPage + 1) },
                                        enabled = currentPage < totalPages - 1,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Next")
                                    }
                                }

                                // Zoom Controls
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = "${(zoomScale * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.0f) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            zoomScale = 1.0f
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.FitScreen, contentDescription = "Reset Zoom", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Interactive Canvas Area with PDF page coordinates
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val availableWidthPx = constraints.maxWidth.toFloat()
                            val availableHeightPx = constraints.maxHeight.toFloat()

                            val (pageW, pageH) = pageDimensions
                            val pdfAspect = pageW.toFloat() / pageH.toFloat()

                            val fittedWidthPx = if (availableWidthPx / availableHeightPx < pdfAspect) {
                                availableWidthPx * 0.94f
                            } else {
                                availableHeightPx * 0.94f * pdfAspect
                            }
                            val fittedHeightPx = fittedWidthPx / pdfAspect

                            val density = LocalDensity.current
                            val fittedWidthDp = with(density) { fittedWidthPx.toDp() }
                            val fittedHeightDp = with(density) { fittedHeightPx.toDp() }

                            // Coordinate Scale Factor: Screen Pixels per PDF Page Point
                            val pxPerPt = fittedWidthPx / pageW.toFloat()

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(isElementInteractionActive, interactionMode) {
                                        if (!isElementInteractionActive || interactionMode == EditorInteractionMode.PAGE_NAV) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                zoomScale = (zoomScale * zoom).coerceIn(0.75f, 3.5f)
                                                val maxPanX = (availableWidthPx * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                                                val maxPanY = (availableHeightPx * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                                                if (zoomScale > 1f) {
                                                    panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                                                    panOffsetY = (panOffsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                                                } else {
                                                    panOffsetX = 0f
                                                    panOffsetY = 0f
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Page Container (White Card)
                                Card(
                                shape = RoundedCornerShape(2.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier
                                    .size(fittedWidthDp, fittedHeightDp)
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = panOffsetX
                                        translationY = panOffsetY
                                    }
                                    .pointerInput(interactionMode) {
                                        if (interactionMode != EditorInteractionMode.PAGE_NAV) {
                                            detectTapGestures(
                                                onTap = {
                                                    viewModel.selectElement(null)
                                                    viewModel.clearWordSelection()
                                                },
                                                onDoubleTap = {
                                                    zoomScale = if (zoomScale > 1.2f) 1.0f else 2.0f
                                                    if (zoomScale == 1.0f) {
                                                        panOffsetX = 0f
                                                        panOffsetY = 0f
                                                    }
                                                },
                                                onLongPress = { offset ->
                                                    val touchPtX = offset.x / pxPerPt
                                                    val touchPtY = offset.y / pxPerPt
                                                    viewModel.onLongPressAtPoint(touchPtX, touchPtY)
                                                }
                                            )
                                        } else {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    zoomScale = if (zoomScale > 1.2f) 1.0f else 2.0f
                                                    if (zoomScale == 1.0f) {
                                                        panOffsetX = 0f
                                                        panOffsetY = 0f
                                                    }
                                                }
                                            )
                                        }
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // 1. Rendered PDF Background Layer
                                    val pageBitmap by produceState<Bitmap?>(
                                        initialValue = null,
                                        currentPage,
                                        fittedWidthPx.toInt()
                                    ) {
                                        value = viewModel.renderPageBitmap(
                                            currentPage,
                                            fittedWidthPx.toInt(),
                                            fittedHeightPx.toInt()
                                        )
                                    }

                                    if (pageBitmap != null) {
                                        Image(
                                            bitmap = pageBitmap!!.asImageBitmap(),
                                            contentDescription = "PDF Page ${currentPage + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                                        }
                                    }

                                    // 2. Long-Press Selected Words Highlight Overlay Layer
                                    if (selectedWords.isNotEmpty()) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            for (word in selectedWords) {
                                                val leftPx = word.bounds.left * pxPerPt
                                                val topPx = word.bounds.top * pxPerPt
                                                val widthPx = (word.bounds.right - word.bounds.left) * pxPerPt
                                                val heightPx = (word.bounds.bottom - word.bounds.top) * pxPerPt

                                                // Draw semi-transparent blue highlight over word
                                                drawRect(
                                                    color = Color(0x662196F3),
                                                    topLeft = Offset(leftPx, topPx),
                                                    size = androidx.compose.ui.geometry.Size(widthPx, heightPx)
                                                )
                                                // Border
                                                drawRect(
                                                    color = Color(0xFF1976D2),
                                                    topLeft = Offset(leftPx, topPx),
                                                    size = androidx.compose.ui.geometry.Size(widthPx, heightPx),
                                                    style = Stroke(width = 1.5.dp.toPx())
                                                )
                                            }
                                        }
                                    }

                                    // 3. Vector Object Elements Layer (Movable, Resizable, Editable)
                                    val currentElements = elementsMap[currentPage].orEmpty()
                                    for (elem in currentElements) {
                                        EditorElementView(
                                            element = elem,
                                            isSelected = elem.id == selectedElementId,
                                            pxPerPt = pxPerPt,
                                            onSelect = {
                                                viewModel.selectElement(elem.id)
                                                viewModel.clearWordSelection()
                                            },
                                            onMove = { deltaXPt, deltaYPt ->
                                                viewModel.updateElementPosition(
                                                    elem.id,
                                                    elem.x + deltaXPt,
                                                    elem.y + deltaYPt
                                                )
                                            },
                                            onResize = { newWPt, newHPt ->
                                                viewModel.updateElementSize(elem.id, newWPt, newHPt)
                                            },
                                            onRotate = { degrees ->
                                                viewModel.updateElementRotation(elem.id, degrees)
                                            },
                                            onInteractionActiveChange = { active ->
                                                isElementInteractionActive = active
                                                if (active) {
                                                    viewModel.beginElementTransform()
                                                }
                                            },
                                            isInteractionDisabled = interactionMode == EditorInteractionMode.PAGE_NAV
                                        )
                                    }

                                    // 4. Freehand Pen In-Progress Drawing Layer
                                    if (activeTool == ActiveEditorTool.PEN) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            penStrokePoints.clear()
                                                            penStrokePoints.add(Pair(offset.x / pxPerPt, offset.y / pxPerPt))
                                                        },
                                                        onDrag = { change, _ ->
                                                            change.consume()
                                                            penStrokePoints.add(Pair(change.position.x / pxPerPt, change.position.y / pxPerPt))
                                                        },
                                                        onDragEnd = {
                                                            if (penStrokePoints.size > 1) {
                                                                val minX = penStrokePoints.minOf { it.first }
                                                                val minY = penStrokePoints.minOf { it.second }
                                                                val maxX = penStrokePoints.maxOf { it.first }
                                                                val maxY = penStrokePoints.maxOf { it.second }
                                                                val w = (maxX - minX).coerceAtLeast(10f)
                                                                val h = (maxY - minY).coerceAtLeast(10f)

                                                                val normalized = penStrokePoints.map { Pair(it.first - minX, it.second - minY) }
                                                                val signatureElem = SignatureElement(
                                                                    pageIndex = currentPage,
                                                                    x = minX,
                                                                    y = minY,
                                                                    width = w,
                                                                    height = h,
                                                                    pathPoints = normalized,
                                                                    inkColor = 0xFF0F3B66,
                                                                    inkWidthPt = 3f
                                                                )
                                                                viewModel.addElement(signatureElem)
                                                                penStrokePoints.clear()
                                                            }
                                                        }
                                                    )
                                                }
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                if (penStrokePoints.size > 1) {
                                                    val path = Path().apply {
                                                        moveTo(penStrokePoints[0].first * pxPerPt, penStrokePoints[0].second * pxPerPt)
                                                        for (i in 1 until penStrokePoints.size) {
                                                            lineTo(penStrokePoints[i].first * pxPerPt, penStrokePoints[i].second * pxPerPt)
                                                        }
                                                    }
                                                    drawPath(path, Color(0xFF0F3B66), style = Stroke(width = 3f * pxPerPt))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Contextual Text Selection Floating Toolbar (Anchored on Screen above selection)
                            if (selectedWords.isNotEmpty()) {
                                PdfEditorTextSelectionBar(
                                    selectedWordCount = selectedWords.size,
                                    onCopy = {
                                        val text = viewModel.copySelectedWords(context)
                                        Toast.makeText(context, "Copied: \"$text\"", Toast.LENGTH_SHORT).show()
                                    },
                                    onCut = {
                                        val text = viewModel.cutSelectedWords(context)
                                        Toast.makeText(context, "Copied and visually covered. Original PDF text remains underneath.", Toast.LENGTH_LONG).show()
                                    },
                                    onPaste = {
                                        val first = selectedWords.first()
                                        viewModel.pasteTextAtPosition(first.bounds.left, first.bounds.bottom + 6f)
                                        viewModel.clearWordSelection()
                                    },
                                    onEdit = {
                                        val first = selectedWords.first()
                                        isEditingExistingElement = false
                                        editingElementId = null
                                        textInput = selectedWords.joinToString(" ") { it.text }
                                        textSizePt = first.fontSizePt
                                        textColor = first.textColor
                                        textBgColor = null // Respects transparent background by default
                                        textBold = first.isBold
                                        textItalic = first.isItalic
                                        showTextDialog = true
                                    },
                                    onDelete = {
                                        viewModel.deleteSelectedWords()
                                        Toast.makeText(context, "Text covered visually only. Original PDF text remains in the file.", Toast.LENGTH_LONG).show()
                                    },
                                    onSelectAll = { viewModel.selectAllWordsOnCurrentPage() },
                                    onChangeColor = {
                                        textColor = 0xFFDC2626 // Quick Red toggle
                                        viewModel.replaceSelectedWords(
                                            newText = selectedWords.joinToString(" ") { it.text },
                                            fontSizePt = selectedWords.first().fontSizePt,
                                            textColor = textColor
                                        )
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 16.dp)
                                )
                            }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    // Insert or Edit Text Dialog (With Independent Text & Background Colors)
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = {
                Text(
                    text = if (isEditingExistingElement) "Edit Text Object" else if (selectedWords.isNotEmpty()) "Replace PDF Text" else "Add New Text",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedWords.isNotEmpty() && !isEditingExistingElement) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "This operation places a cover over the selected text and adds a new text overlay. The original PDF text remains underneath and may still be extracted. This is not secure redaction or direct editing of the original text object.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Text Content") },
                        placeholder = { Text("Enter words or paragraphs...") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("editor_text_input")
                    )

                    // Font Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font Size: ${textSizePt.toInt()} pt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(12f, 16f, 20f, 24f, 32f).forEach { pt ->
                                FilterChip(
                                    selected = textSizePt == pt,
                                    onClick = { textSizePt = pt },
                                    label = { Text("${pt.toInt()}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    Slider(
                        value = textSizePt,
                        onValueChange = { textSizePt = it },
                        valueRange = 8f..64f
                    )

                    // Font Styling (Bold, Italic, Underline)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = textBold,
                            onClick = { textBold = !textBold },
                            label = { Text("Bold") },
                            leadingIcon = { Icon(Icons.Default.FormatBold, contentDescription = null) }
                        )
                        FilterChip(
                            selected = textItalic,
                            onClick = { textItalic = !textItalic },
                            label = { Text("Italic") },
                            leadingIcon = { Icon(Icons.Default.FormatItalic, contentDescription = null) }
                        )
                        FilterChip(
                            selected = textUnderline,
                            onClick = { textUnderline = !textUnderline },
                            label = { Text("Underline") },
                            leadingIcon = { Icon(Icons.Default.FormatUnderlined, contentDescription = null) }
                        )
                    }

                    // Alignment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("LEFT" to Icons.Default.FormatAlignLeft, "CENTER" to Icons.Default.FormatAlignCenter, "RIGHT" to Icons.Default.FormatAlignRight).forEach { (align, icon) ->
                            FilterChip(
                                selected = textAlign == align,
                                onClick = { textAlign = align },
                                label = { Text(align.lowercase().capitalize()) },
                                leadingIcon = { Icon(icon, contentDescription = null) }
                            )
                        }
                    }

                    // 1. Text Color Picker (Independent)
                    Text("1. Text Color:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0xFF000000 to "Black",
                            0xFF0F3B66 to "Navy",
                            0xFFDC2626 to "Red",
                            0xFF16A34A to "Green",
                            0xFF2563EB to "Blue",
                            0xFFD97706 to "Amber",
                            0xFFFFFFFF to "White"
                        ).forEach { (colorHex, _) ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorHex))
                                    .border(
                                        width = if (textColor == colorHex) 3.dp else 1.dp,
                                        color = if (textColor == colorHex) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { textColor = colorHex }
                            )
                        }
                    }

                    // 2. Text Background Color Picker (Independent)
                    Text("2. Text Background Banner (Independent):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = textBgColor == null,
                            onClick = { textBgColor = null },
                            label = { Text("None") }
                        )
                        listOf(
                            0xFFFFFFFF to "White",
                            0xFFFEF08A to "Yellow",
                            0xFFBFDBFE to "Blue",
                            0xFFBBF7D0 to "Green",
                            0xFF1E293B to "Dark"
                        ).forEach { (bgColorHex, label) ->
                            FilterChip(
                                selected = textBgColor == bgColorHex,
                                onClick = { textBgColor = bgColorHex },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isEditingExistingElement && editingElementId != null) {
                            viewModel.updateTextElement(
                                elementId = editingElementId!!,
                                newText = textInput,
                                fontSizePt = textSizePt,
                                textColor = textColor,
                                isBold = textBold,
                                isItalic = textItalic,
                                isUnderline = textUnderline,
                                backgroundColor = textBgColor,
                                textAlign = textAlign
                            )
                        } else if (selectedWords.isNotEmpty()) {
                            viewModel.replaceSelectedWords(
                                newText = textInput,
                                fontSizePt = textSizePt,
                                textColor = textColor,
                                backgroundColor = textBgColor,
                                isBold = textBold,
                                isItalic = textItalic,
                                isUnderline = textUnderline,
                                textAlign = textAlign
                            )
                        } else {
                            val newTextElem = TextElement(
                                pageIndex = currentPage,
                                x = 70f,
                                y = 140f,
                                width = maxOf(140f, textInput.length * textSizePt * 0.65f),
                                height = textSizePt + 16f,
                                text = textInput,
                                fontSizePt = textSizePt,
                                textColor = textColor,
                                backgroundColor = textBgColor,
                                isBold = textBold,
                                isItalic = textItalic,
                                isUnderline = textUnderline,
                                textAlign = textAlign
                            )
                            viewModel.addElement(newTextElem)
                        }
                        showTextDialog = false
                    },
                    enabled = textInput.isNotBlank(),
                    modifier = Modifier.testTag("apply_text_button")
                ) {
                    Text(if (isEditingExistingElement) "Save Changes" else if (selectedWords.isNotEmpty()) "Replace Text" else "Insert Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Insert Shapes Dialog
    if (showShapeDialog) {
        AlertDialog(
            onDismissRequest = { showShapeDialog = false },
            title = { Text("Insert Shape", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select Shape Type:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShapeType.values().forEach { type ->
                            FilterChip(
                                selected = selectedShapeType == type,
                                onClick = { selectedShapeType = type },
                                label = { Text(type.name.replace("_", " ").lowercase().capitalize(), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text("Border Color:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFF0F3B66, 0xFFE53935, 0xFF16A34A, 0xFF000000, 0xFFD97706).forEach { colorLong ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorLong))
                                    .border(
                                        width = if (shapeStrokeColor == colorLong) 3.dp else 1.dp,
                                        color = if (shapeStrokeColor == colorLong) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { shapeStrokeColor = colorLong }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shape = ShapeElement(
                            pageIndex = currentPage,
                            x = 80f,
                            y = 160f,
                            width = 160f,
                            height = 90f,
                            shapeType = selectedShapeType,
                            strokeColor = shapeStrokeColor,
                            fillColor = shapeFillColor,
                            strokeWidthPt = 2.5f
                        )
                        viewModel.addElement(shape)
                        showShapeDialog = false
                    }
                ) {
                    Text("Insert Shape")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShapeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Insert Signature Dialog
    if (showSignatureDialog) {
        AlertDialog(
            onDismissRequest = { showSignatureDialog = false },
            title = { Text("Draw Signature", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Draw your signature with your finger:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        signaturePoints = signaturePoints + Pair(offset.x, offset.y)
                                    },
                                    onDrag = { change, _ ->
                                        signaturePoints = signaturePoints + Pair(change.position.x, change.position.y)
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (signaturePoints.size > 1) {
                                val path = Path().apply {
                                    moveTo(signaturePoints[0].first, signaturePoints[0].second)
                                    for (i in 1 until signaturePoints.size) {
                                        lineTo(signaturePoints[i].first, signaturePoints[i].second)
                                    }
                                }
                                drawPath(path, Color(signatureInkColor), style = Stroke(width = 4f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { signaturePoints = emptyList() }) { Text("Clear Canvas") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (signaturePoints.isNotEmpty()) {
                            val minX = signaturePoints.minOf { it.first }
                            val maxX = signaturePoints.maxOf { it.first }
                            val minY = signaturePoints.minOf { it.second }
                            val maxY = signaturePoints.maxOf { it.second }
                            val rawW = (maxX - minX).coerceAtLeast(10f)
                            val rawH = (maxY - minY).coerceAtLeast(10f)

                            val targetW = 140f
                            val targetH = (targetW * (rawH / rawW)).coerceIn(40f, 100f)

                            val normalizedPts = signaturePoints.map { pt ->
                                Pair(
                                    ((pt.first - minX) / rawW) * targetW,
                                    ((pt.second - minY) / rawH) * targetH
                                )
                            }

                            val signatureElem = SignatureElement(
                                pageIndex = currentPage,
                                x = 80f,
                                y = 200f,
                                width = targetW,
                                height = targetH,
                                pathPoints = normalizedPts,
                                inkColor = signatureInkColor
                            )
                            viewModel.addElement(signatureElem)
                        }
                        showSignatureDialog = false
                    },
                    enabled = signaturePoints.isNotEmpty()
                ) {
                    Text("Insert Signature")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignatureDialog = false }) { Text("Cancel") }
            }
        )
    }

    // PDFBox-backed page operations. Each action creates a new PDF copy.
    if (showPageOperationsDialog) {
        AlertDialog(
            onDismissRequest = { showPageOperationsDialog = false },
            title = { Text("PDF Page Operations", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Page changes create a new PDF copy. Existing text, image and signature overlays are kept and remapped when pages are inserted, deleted, duplicated or reordered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showPageOperationsDialog = false; viewModel.duplicateCurrentPage() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Duplicate Current Page") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPageOperationsDialog = false; viewModel.moveCurrentPageBy(-1) },
                            enabled = currentPage > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("Move Earlier") }
                        OutlinedButton(
                            onClick = { showPageOperationsDialog = false; viewModel.moveCurrentPageBy(1) },
                            enabled = currentPage < ((status as? EditorUiStatus.Loaded)?.documentInfo?.pageCount ?: 1) - 1,
                            modifier = Modifier.weight(1f)
                        ) { Text("Move Later") }
                    }
                    OutlinedButton(
                        onClick = { showPageOperationsDialog = false; viewModel.rotateCurrentPage() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Rotate Current Page 90°") }
                    OutlinedButton(
                        onClick = { showPageOperationsDialog = false; viewModel.insertBlankPageAfterCurrent() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Insert Blank Page After Current") }
                    OutlinedButton(
                        onClick = { showPageOperationsDialog = false; viewModel.deleteCurrentPage() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete Current Page") }
                    OutlinedButton(
                        onClick = { showPageOperationsDialog = false; viewModel.extractCurrentPage() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Extract Current Page") }
                    OutlinedButton(
                        onClick = {
                            showPageOperationsDialog = false
                            mergePdfLauncher.launch(arrayOf("application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Merge With Other PDFs") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPageOperationsDialog = false }) { Text("Close") }
            }
        )
    }

    // Save & Export Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save & Export PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "A new PDF copy will be created and the original file will remain untouched. Existing PDF page content is preserved as PDF content (not flattened to page images). Added objects are appended as overlays; this does not directly replace existing PDF text objects. Built-in Helvetica export is limited to WinAnsi-encodable text; Bengali, Arabic, and other unsupported Unicode characters require an embedded Unicode font.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = saveFileNameInput,
                        onValueChange = { saveFileNameInput = it },
                        label = { Text("Output PDF Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("editor_output_filename_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        viewModel.exportEditedPdf(saveFileNameInput) { /* state observer updates */ }
                    },
                    modifier = Modifier.testTag("confirm_export_pdf_button")
                ) {
                    Text("Export PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Universal Export Result Dialog
    if (status is EditorUiStatus.ExportSuccess) {
        val success = status as EditorUiStatus.ExportSuccess
        UniversalResultDialog(
            title = "PDF Exported Successfully!",
            file = success.exportedFile,
            additionalDetails = listOf(
                "Pages" to "${viewModel.elements.value.values.count { it.isNotEmpty() }} modified pages",
                "Export" to "Original PDF content preserved with vector overlays"
            ),
            onDismiss = { viewModel.resetExportStatus() },
            onOpenFile = {
                val uri = Uri.fromFile(success.exportedFile)
                val name = success.exportedFile.name
                viewModel.resetExportStatus()
                onOpenExportedPdf(uri, name)
            },
            anotherActionLabel = "Keep Editing"
        )
    }
}

@Composable
private fun EditorElementView(
    element: EditorElement,
    isSelected: Boolean,
    pxPerPt: Float,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onRotate: (Float) -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit,
    isInteractionDisabled: Boolean = false
) {
    val density = LocalDensity.current

    val leftDp = with(density) { (element.x * pxPerPt).toDp() }
    val topDp = with(density) { (element.y * pxPerPt).toDp() }
    val widthDp = with(density) { (element.width * pxPerPt).toDp() }
    val heightDp = with(density) { (element.height * pxPerPt).toDp() }

    Box(
        modifier = Modifier
            .offset(x = leftDp, y = topDp)
            .size(widthDp, heightDp)
            .rotate(element.rotationDegrees)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .pointerInput(element.id, isInteractionDisabled) {
                if (!isInteractionDisabled) {
                    detectTapGestures(
                        onTap = { onSelect() }
                    )
                }
            }
            .pointerInput(element.id, isInteractionDisabled) {
                if (!isInteractionDisabled) {
                    detectDragGestures(
                        onDragStart = {
                            onSelect()
                            onInteractionActiveChange(true)
                        },
                        onDragEnd = { onInteractionActiveChange(false) },
                        onDragCancel = { onInteractionActiveChange(false) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaXPt = dragAmount.x / pxPerPt
                            val deltaYPt = dragAmount.y / pxPerPt
                            onMove(deltaXPt, deltaYPt)
                        }
                    )
                }
            }
    ) {
        when (element) {
            is TextElement -> {
                val scaledFontSizeSp = (element.fontSizePt * pxPerPt * 0.75f).sp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .let {
                            if (element.backgroundColor != null) it.background(Color(element.backgroundColor!!)) else it
                        }
                        .padding(2.dp)
                ) {
                    Text(
                        text = element.text,
                        fontSize = scaledFontSizeSp,
                        color = Color(element.textColor),
                        fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (element.isUnderline) TextDecoration.Underline else TextDecoration.None,
                        textAlign = when (element.textAlign) {
                            "CENTER" -> TextAlign.Center
                            "RIGHT" -> TextAlign.End
                            else -> TextAlign.Start
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            is ImageElement -> {
                AsyncImage(
                    model = element.imageUri,
                    contentDescription = "Image Object",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ShapeElement -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = element.strokeWidthPt * pxPerPt
                    val shapeColor = Color(element.strokeColor)
                    when (element.shapeType) {
                        ShapeType.RECTANGLE -> {
                            if (element.fillColor != null) {
                                drawRect(Color(element.fillColor!!))
                            }
                            drawRect(shapeColor, style = Stroke(width = strokeW))
                        }
                        ShapeType.CIRCLE -> {
                            val r = minOf(size.width, size.height) / 2f
                            if (element.fillColor != null) {
                                drawCircle(Color(element.fillColor!!), radius = r)
                            }
                            drawCircle(shapeColor, radius = r, style = Stroke(width = strokeW))
                        }
                        ShapeType.LINE -> {
                            drawLine(shapeColor, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = strokeW)
                        }
                        ShapeType.ARROW -> {
                            val centerY = size.height / 2f
                            val headLength = 10f * pxPerPt
                            val headHalfHeight = 6f * pxPerPt
                            drawLine(
                                shapeColor,
                                Offset(0f, centerY),
                                Offset(size.width, centerY),
                                strokeWidth = strokeW
                            )
                            drawLine(
                                shapeColor,
                                Offset(size.width - headLength, centerY - headHalfHeight),
                                Offset(size.width, centerY),
                                strokeWidth = strokeW
                            )
                            drawLine(
                                shapeColor,
                                Offset(size.width - headLength, centerY + headHalfHeight),
                                Offset(size.width, centerY),
                                strokeWidth = strokeW
                            )
                        }
                        ShapeType.HIGHLIGHT_BOX -> {
                            drawRect(shapeColor.copy(alpha = 0.35f * element.opacity.coerceIn(0f, 1f)))
                        }
                    }
                }
            }
            is SignatureElement -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (element.pathPoints.size > 1) {
                        val path = Path().apply {
                            val first = element.pathPoints[0]
                            moveTo(first.first * pxPerPt, first.second * pxPerPt)
                            for (i in 1 until element.pathPoints.size) {
                                val pt = element.pathPoints[i]
                                lineTo(pt.first * pxPerPt, pt.second * pxPerPt)
                            }
                        }
                        drawPath(path, Color(element.inkColor), style = Stroke(width = element.inkWidthPt * pxPerPt))
                    }
                }
            }
        }

        // 4 Corner Resize Handles when selected
        if (isSelected) {
            // Bottom-Right Corner Resize Handle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, Color.White, CircleShape)
                    .pointerInput(element.id) {
                        detectDragGestures(
                            onDragStart = { onInteractionActiveChange(true) },
                            onDragEnd = { onInteractionActiveChange(false) },
                            onDragCancel = { onInteractionActiveChange(false) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newWPt = element.width + (dragAmount.x / pxPerPt)
                                val newHPt = element.height + (dragAmount.y / pxPerPt)
                                onResize(newWPt, newHPt)
                            }
                        )
                    }
            )

            // Top-Left Corner Drag Handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, Color.White, CircleShape)
            )

            // Top Center Rotation Handle
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-14).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFB8C00))
                    .border(2.dp, Color.White, CircleShape)
                    .clickable { onRotate((element.rotationDegrees + 45f) % 360f) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorToolButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
