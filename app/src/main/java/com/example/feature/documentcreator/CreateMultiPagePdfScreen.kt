package com.example.feature.documentcreator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Square
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.editor.EditorElement
import com.example.engine.editor.ImageElement
import com.example.engine.editor.ShapeElement
import com.example.engine.editor.ShapeType
import com.example.engine.editor.SignatureElement
import com.example.engine.editor.TextElement
import com.example.ui.components.UniversalResultDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

enum class PageStandardSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    A3("A3", 842, 1191),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008)
}

enum class PageOrientation { PORTRAIT, LANDSCAPE }

enum class PageMargins(val label: String, val marginPt: Float) {
    NONE("None (0 pt)", 0f),
    NORMAL("Normal (36 pt)", 36f),
    WIDE("Wide (72 pt)", 72f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMultiPagePdfScreen(
    onBackClick: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit,
    onRegisterGeneratedFile: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Document Setup States
    var docTitle by remember { mutableStateOf("New_Document_${System.currentTimeMillis() % 10000}") }
    var standardSize by remember { mutableStateOf(PageStandardSize.A4) }
    var orientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var margins by remember { mutableStateOf(PageMargins.NORMAL) }
    var pageBgColor by remember { mutableStateOf(Color.White) }

    val baseWidth = if (orientation == PageOrientation.PORTRAIT) standardSize.widthPt else standardSize.heightPt
    val baseHeight = if (orientation == PageOrientation.PORTRAIT) standardSize.heightPt else standardSize.widthPt

    // Multi-page state
    var totalPages by remember { mutableIntStateOf(1) }
    var currentPageIndex by remember { mutableIntStateOf(0) } // 0-based

    // Per-page vector elements map
    val pageElementsMap = remember {
        mutableStateMapOf<Int, MutableList<EditorElement>>().apply {
            put(0, mutableListOf())
        }
    }

    // Image Bitmap preview cache
    val imagePreviewCache = remember { mutableStateMapOf<String, Bitmap>() }

    var selectedElementId by remember { mutableStateOf<String?>(null) }
    var isElementInteractionActive by remember { mutableStateOf(false) }

    // Canvas Zoom & Pan
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Sheet / Dialog states
    var showPageSettingsSheet by remember { mutableStateOf(false) }
    var showAddTextModal by remember { mutableStateOf(false) }
    var showAddShapeModal by remember { mutableStateOf(false) }
    var showSignaturePadModal by remember { mutableStateOf(false) }
    var showExportConfirmDialog by remember { mutableStateOf(false) }
    var isCompiling by remember { mutableStateOf(false) }
    var exportedFileResult by remember { mutableStateOf<File?>(null) }

    // Text editing inputs
    var newTextValue by remember { mutableStateOf("") }
    var newTextFontSize by remember { mutableFloatStateOf(16f) }
    var newTextColor by remember { mutableStateOf(Color(0xFF0F3B66)) }
    var newTextBgColor by remember { mutableStateOf(Color.Transparent) }
    var newTextBold by remember { mutableStateOf(false) }
    var newTextItalic by remember { mutableStateOf(false) }

    // Image Picker Launcher
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bitmap = loadBitmapFromUri(context, uri)
                if (bitmap != null) {
                    val elemId = UUID.randomUUID().toString()
                    imagePreviewCache[elemId] = bitmap
                    val pageElements = pageElementsMap.getOrPut(currentPageIndex) { mutableListOf() }
                    val imageElem = ImageElement(
                        id = elemId,
                        pageIndex = currentPageIndex,
                        x = margins.marginPt + 20f,
                        y = margins.marginPt + 20f,
                        width = 180f,
                        height = 180f * (bitmap.height.toFloat() / bitmap.width.toFloat()).coerceIn(0.3f, 3f),
                        imageUri = uri.toString()
                    )
                    pageElements.add(imageElem)
                    selectedElementId = imageElem.id
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
                        modifier = Modifier.testTag("create_multipage_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = docTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Page ${currentPageIndex + 1} of $totalPages • ${standardSize.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPageSettingsSheet = true },
                        modifier = Modifier.testTag("page_setup_settings_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Page Setup")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        },
                        modifier = Modifier.testTag("reset_zoom_multipage_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Fit View")
                    }
                    Button(
                        onClick = { showExportConfirmDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("export_multipage_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Page Navigation Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (currentPageIndex > 0) currentPageIndex--
                                },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.testTag("prev_page_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = "Previous Page")
                            }
                            Text(
                                text = "Page ${currentPageIndex + 1} / $totalPages",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (currentPageIndex < totalPages - 1) currentPageIndex++
                                },
                                enabled = currentPageIndex < totalPages - 1,
                                modifier = Modifier.testTag("next_page_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = "Next Page")
                            }
                        }

                        // Page Operations Strip (Add Page, Duplicate, Delete)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Duplicate Page
                            IconButton(
                                onClick = {
                                    val currentElements = pageElementsMap[currentPageIndex]?.map { it } ?: emptyList()
                                    for (i in totalPages downTo currentPageIndex + 2) {
                                        pageElementsMap[i] = pageElementsMap[i - 1] ?: mutableListOf()
                                    }
                                    totalPages++
                                    currentPageIndex++
                                    pageElementsMap[currentPageIndex] = currentElements.toMutableList()
                                    Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("duplicate_page_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Page", modifier = Modifier.size(20.dp))
                            }

                            // Delete Page (Minimum 1 page)
                            IconButton(
                                onClick = {
                                    if (totalPages > 1) {
                                        for (i in currentPageIndex until totalPages - 1) {
                                            pageElementsMap[i] = pageElementsMap[i + 1] ?: mutableListOf()
                                        }
                                        pageElementsMap.remove(totalPages - 1)
                                        totalPages--
                                        if (currentPageIndex >= totalPages) currentPageIndex = totalPages - 1
                                        Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "A document must have at least 1 page", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = totalPages > 1,
                                modifier = Modifier.testTag("delete_page_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Page", modifier = Modifier.size(20.dp))
                            }

                            // PERSISTENT "+ Add Page" Button (ALWAYS VISIBLE)
                            Button(
                                onClick = {
                                    totalPages++
                                    currentPageIndex = totalPages - 1
                                    pageElementsMap[currentPageIndex] = mutableListOf()
                                    Toast.makeText(context, "Added Page $totalPages", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("persistent_add_page_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add Page")
                            }
                        }
                    }

                    // Main Creation Tools Bar (Text, Image, Shape, Signature)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                newTextValue = ""
                                showAddTextModal = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_text_button")
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Text")
                        }

                        OutlinedButton(
                            onClick = {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_image_button")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Image")
                        }

                        OutlinedButton(
                            onClick = { showAddShapeModal = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_shape_button")
                        ) {
                            Icon(Icons.Outlined.Square, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shape")
                        }

                        OutlinedButton(
                            onClick = { showSignaturePadModal = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_signature_button")
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign")
                        }

                        // Selected Element Quick Controls
                        if (selectedElementId != null) {
                            Button(
                                onClick = {
                                    val list = pageElementsMap[currentPageIndex]
                                    list?.removeAll { it.id == selectedElementId }
                                    selectedElementId = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("delete_selected_element_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete Item")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE2E8F0))
                .pointerInput(isElementInteractionActive) {
                    if (!isElementInteractionActive) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.8f, 3.5f)
                            val maxPan = 450f * zoomScale
                            panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPan, maxPan)
                            panOffsetY = (panOffsetY + pan.y).coerceIn(-maxPan, maxPan)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomScale > 1.1f) {
                                zoomScale = 1.0f
                                panOffsetX = 0f
                                panOffsetY = 0f
                            } else {
                                zoomScale = 2.0f
                            }
                        },
                        onTap = {
                            selectedElementId = null
                        }
                    )
                }
        ) {
            val containerWidth = maxWidth
            val containerHeight = maxHeight

            val pageRatio = baseWidth.toFloat() / baseHeight.toFloat()
            val fittedPageWidth = (containerWidth.value * 0.90f).coerceAtMost(containerHeight.value * 0.90f * pageRatio)
            val fittedPageHeight = fittedPageWidth / pageRatio

            val scaleFactor = fittedPageWidth / baseWidth.toFloat()

            // Page Canvas Container
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(panOffsetX.roundToInt(), panOffsetY.roundToInt()) }
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale
                    )
                    .width(fittedPageWidth.dp)
                    .height(fittedPageHeight.dp)
                    .shadow(10.dp, RoundedCornerShape(2.dp))
                    .background(pageBgColor)
                    .border(1.dp, Color(0xFFCBD5E1))
                    .testTag("document_page_canvas")
            ) {
                if (margins.marginPt > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding((margins.marginPt * scaleFactor).dp)
                            .border(0.5.dp, Color(0x33000000))
                    )
                }

                // Render vector elements for current page
                val currentElements = pageElementsMap[currentPageIndex] ?: emptyList()
                for (element in currentElements) {
                    val isSelected = element.id == selectedElementId

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (element.x * scaleFactor).roundToInt(),
                                    (element.y * scaleFactor).roundToInt()
                                )
                            }
                            .width((element.width * scaleFactor).dp)
                            .height((element.height * scaleFactor).dp)
                            .rotate(element.rotationDegrees)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .pointerInput(element.id) {
                                detectTapGestures {
                                    selectedElementId = element.id
                                }
                            }
                            .pointerInput(element.id, scaleFactor) {
                                detectDragGestures(
                                    onDragStart = {
                                        isElementInteractionActive = true
                                        selectedElementId = element.id
                                    },
                                    onDragEnd = { isElementInteractionActive = false },
                                    onDragCancel = { isElementInteractionActive = false },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val list = pageElementsMap[currentPageIndex] ?: return@detectDragGestures
                                        val idx = list.indexOfFirst { it.id == element.id }
                                        if (idx != -1) {
                                            val cur = list[idx]
                                            val newX = (cur.x + dragAmount.x / scaleFactor).coerceIn(0f, baseWidth.toFloat() - cur.width)
                                            val newY = (cur.y + dragAmount.y / scaleFactor).coerceIn(0f, baseHeight.toFloat() - cur.height)
                                            list[idx] = cur.copyWithPosition(newX, newY)
                                        }
                                    }
                                )
                            }
                    ) {
                        when (element) {
                            is TextElement -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(element.backgroundColor?.let { Color(it) } ?: Color.Transparent)
                                        .padding(2.dp)
                                ) {
                                    Text(
                                        text = element.text,
                                        fontSize = (element.fontSizePt * scaleFactor).sp,
                                        color = Color(element.textColor),
                                        fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
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
                                val bmp = imagePreviewCache[element.id]
                                if (bmp != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFFE2E8F0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Image", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            is ShapeElement -> {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeCol = Color(element.strokeColor)
                                    val fillCol = element.fillColor?.let { Color(it) } ?: Color.Transparent
                                    when (element.shapeType) {
                                        ShapeType.RECTANGLE, ShapeType.HIGHLIGHT_BOX -> {
                                            if (element.fillColor != null) drawRect(color = fillCol)
                                            drawRect(
                                                color = strokeCol,
                                                style = Stroke(width = element.strokeWidthPt * scaleFactor)
                                            )
                                        }
                                        ShapeType.CIRCLE -> {
                                            if (element.fillColor != null) drawOval(color = fillCol)
                                            drawOval(
                                                color = strokeCol,
                                                style = Stroke(width = element.strokeWidthPt * scaleFactor)
                                            )
                                        }
                                        ShapeType.LINE, ShapeType.ARROW -> {
                                            drawLine(
                                                color = strokeCol,
                                                start = Offset(0f, size.height / 2f),
                                                end = Offset(size.width, size.height / 2f),
                                                strokeWidth = element.strokeWidthPt * scaleFactor
                                            )
                                        }
                                    }
                                }
                            }

                            is SignatureElement -> {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    if (element.pathPoints.size > 1) {
                                        val path = Path()
                                        val first = element.pathPoints.first()
                                        path.moveTo(first.first * scaleFactor, first.second * scaleFactor)
                                        for (i in 1 until element.pathPoints.size) {
                                            val pt = element.pathPoints[i]
                                            path.lineTo(pt.first * scaleFactor, pt.second * scaleFactor)
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color(element.inkColor),
                                            style = Stroke(width = element.inkWidthPt * scaleFactor, cap = StrokeCap.Round)
                                        )
                                    }
                                }
                            }
                        }

                        // Resize handle at bottom-right corner when selected
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(4.dp, 4.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(1.dp, Color.White, CircleShape)
                                    .pointerInput(element.id, scaleFactor) {
                                        detectDragGestures(
                                            onDragStart = { isElementInteractionActive = true },
                                            onDragEnd = { isElementInteractionActive = false },
                                            onDragCancel = { isElementInteractionActive = false },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val list = pageElementsMap[currentPageIndex] ?: return@detectDragGestures
                                                val idx = list.indexOfFirst { it.id == element.id }
                                                if (idx != -1) {
                                                    val cur = list[idx]
                                                    val newW = (cur.width + dragAmount.x / scaleFactor).coerceAtLeast(30f)
                                                    val newH = (cur.height + dragAmount.y / scaleFactor).coerceAtLeast(20f)
                                                    list[idx] = cur.copyWithSize(newW, newH)
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Page Setup
    if (showPageSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPageSettingsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Page Setup & Layout", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = docTitle,
                    onValueChange = { docTitle = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("multipage_title_input")
                )

                Text("Page Format:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageStandardSize.values().forEach { sizeOption ->
                        FilterChip(
                            selected = standardSize == sizeOption,
                            onClick = { standardSize = sizeOption },
                            label = { Text(sizeOption.label) },
                            modifier = Modifier.testTag("page_size_${sizeOption.name.lowercase()}")
                        )
                    }
                }

                Text("Orientation:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = orientation == PageOrientation.PORTRAIT,
                        onClick = { orientation = PageOrientation.PORTRAIT },
                        label = { Text("Portrait") }
                    )
                    FilterChip(
                        selected = orientation == PageOrientation.LANDSCAPE,
                        onClick = { orientation = PageOrientation.LANDSCAPE },
                        label = { Text("Landscape") }
                    )
                }

                Text("Margins:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageMargins.values().forEach { marginOption ->
                        FilterChip(
                            selected = margins == marginOption,
                            onClick = { margins = marginOption },
                            label = { Text(marginOption.label) }
                        )
                    }
                }

                Text("Page Background Color:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val colors = listOf(
                        Color.White to "White",
                        Color(0xFFFFFDD0) to "Cream",
                        Color(0xFFF1F5F9) to "Light Gray",
                        Color(0xFFFDFBF7) to "Warm"
                    )
                    colors.forEach { (c, _) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (pageBgColor == c) 2.5.dp else 1.dp,
                                    color = if (pageBgColor == c) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { pageBgColor = c }
                        )
                    }
                }

                Button(
                    onClick = { showPageSettingsSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Settings")
                }
            }
        }
    }

    // Modal: Add Text Element
    if (showAddTextModal) {
        AlertDialog(
            onDismissRequest = { showAddTextModal = false },
            title = { Text("Add Text Element") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTextValue,
                        onValueChange = { newTextValue = it },
                        label = { Text("Enter text content") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("add_text_input")
                    )

                    Text("Font Size: ${newTextFontSize.toInt()} pt")
                    Slider(
                        value = newTextFontSize,
                        onValueChange = { newTextFontSize = it },
                        valueRange = 10f..48f
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = newTextBold,
                            onClick = { newTextBold = !newTextBold },
                            label = { Text("Bold") }
                        )
                        FilterChip(
                            selected = newTextItalic,
                            onClick = { newTextItalic = !newTextItalic },
                            label = { Text("Italic") }
                        )
                    }

                    Text("Text Color:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Color(0xFF0F3B66), Color.Black, Color(0xFFDC2626), Color(0xFF16A34A), Color(0xFFD97706)).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (newTextColor == c) 2.5.dp else 1.dp,
                                        color = if (newTextColor == c) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { newTextColor = c }
                            )
                        }
                    }

                    Text("Background Highlight:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Color.Transparent, Color(0xFFFEF08A), Color(0xFFBAE6FD), Color(0xFFBBF7D0), Color.White).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (c == Color.Transparent) Color.LightGray else c)
                                    .border(
                                        width = if (newTextBgColor == c) 2.5.dp else 1.dp,
                                        color = if (newTextBgColor == c) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { newTextBgColor = c }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTextValue.isNotBlank()) {
                            val pageElements = pageElementsMap.getOrPut(currentPageIndex) { mutableListOf() }
                            val elem = TextElement(
                                id = UUID.randomUUID().toString(),
                                pageIndex = currentPageIndex,
                                x = margins.marginPt + 30f,
                                y = margins.marginPt + 30f,
                                width = 220f,
                                height = 60f,
                                text = newTextValue,
                                fontSizePt = newTextFontSize,
                                textColor = newTextColor.hashCode().toLong(),
                                backgroundColor = if (newTextBgColor == Color.Transparent) null else newTextBgColor.hashCode().toLong(),
                                isBold = newTextBold,
                                isItalic = newTextItalic
                            )
                            pageElements.add(elem)
                            selectedElementId = elem.id
                        }
                        showAddTextModal = false
                    },
                    modifier = Modifier.testTag("confirm_add_text_button")
                ) {
                    Text("Add Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Add Shape Element
    if (showAddShapeModal) {
        AlertDialog(
            onDismissRequest = { showAddShapeModal = false },
            title = { Text("Add Shape Element") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val pageElements = pageElementsMap.getOrPut(currentPageIndex) { mutableListOf() }
                            val elem = ShapeElement(
                                id = UUID.randomUUID().toString(),
                                pageIndex = currentPageIndex,
                                x = margins.marginPt + 20f,
                                y = margins.marginPt + 20f,
                                width = 160f,
                                height = 90f,
                                shapeType = ShapeType.RECTANGLE,
                                strokeColor = 0xFF0F3B66,
                                fillColor = 0x330F3B66,
                                strokeWidthPt = 2f
                            )
                            pageElements.add(elem)
                            selectedElementId = elem.id
                            showAddShapeModal = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_shape_rectangle")
                    ) {
                        Icon(Icons.Outlined.Square, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rectangle Box")
                    }

                    Button(
                        onClick = {
                            val pageElements = pageElementsMap.getOrPut(currentPageIndex) { mutableListOf() }
                            val elem = ShapeElement(
                                id = UUID.randomUUID().toString(),
                                pageIndex = currentPageIndex,
                                x = margins.marginPt + 20f,
                                y = margins.marginPt + 20f,
                                width = 120f,
                                height = 120f,
                                shapeType = ShapeType.CIRCLE,
                                strokeColor = 0xFF0F3B66,
                                fillColor = 0x220F3B66,
                                strokeWidthPt = 2f
                            )
                            pageElements.add(elem)
                            selectedElementId = elem.id
                            showAddShapeModal = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_shape_circle")
                    ) {
                        Icon(Icons.Outlined.Circle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Circle / Oval")
                    }

                    Button(
                        onClick = {
                            val pageElements = pageElementsMap.getOrPut(currentPageIndex) { mutableListOf() }
                            val elem = ShapeElement(
                                id = UUID.randomUUID().toString(),
                                pageIndex = currentPageIndex,
                                x = margins.marginPt + 10f,
                                y = margins.marginPt + 20f,
                                width = 280f,
                                height = 10f,
                                shapeType = ShapeType.LINE,
                                strokeColor = 0xFF0F3B66,
                                fillColor = null,
                                strokeWidthPt = 2f
                            )
                            pageElements.add(elem)
                            selectedElementId = elem.id
                            showAddShapeModal = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_shape_divider")
                    ) {
                        Icon(Icons.Outlined.HorizontalRule, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Horizontal Divider Line")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddShapeModal = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal: Signature Pad
    if (showSignaturePadModal) {
        var sigStrokes by remember { mutableStateOf(listOf<List<Offset>>()) }
        var activeSigStroke by remember { mutableStateOf(listOf<Offset>()) }

        AlertDialog(
            onDismissRequest = { showSignaturePadModal = false },
            title = { Text("Draw Signature") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Draw on pad below:", style = MaterialTheme.typography.bodySmall)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFFFAFAFA))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activeSigStroke = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activeSigStroke = activeSigStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (activeSigStroke.isNotEmpty()) {
                                            sigStrokes = sigStrokes + listOf(activeSigStroke)
                                            activeSigStroke = emptyList()
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (stroke in sigStrokes) {
                                if (stroke.size > 1) {
                                    val path = Path().apply {
                                        moveTo(stroke[0].x, stroke[0].y)
                                        for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                                    }
                                    drawPath(path, color = Color(0xFF0F3B66), style = Stroke(width = 4f, cap = StrokeCap.Round))
                                }
                            }
                            if (activeSigStroke.size > 1) {
                                val path = Path().apply {
                                    moveTo(activeSigStroke[0].x, activeSigStroke[0].y)
                                    for (i in 1 until activeSigStroke.size) lineTo(activeSigStroke[i].x, activeSigStroke[i].y)
                                }
                                drawPath(path, color = Color(0xFF0F3B66), style = Stroke(width = 4f, cap = StrokeCap.Round))
                            }
                        }
                    }
                    TextButton(onClick = {
                        sigStrokes = emptyList()
                        activeSigStroke = emptyList()
                    }) {
                        Text("Clear Signature Pad")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sigStrokes.isNotEmpty()) {
                            val flattenedPoints = sigStrokes.flatMap { list -> list.map { pt -> Pair(pt.x, pt.y) } }
                            val pageElements = pageElementsMap.getOrPut(currentPageIndex) { mutableListOf() }
                            val elem = SignatureElement(
                                id = UUID.randomUUID().toString(),
                                pageIndex = currentPageIndex,
                                x = margins.marginPt + 20f,
                                y = margins.marginPt + 50f,
                                width = 150f,
                                height = 75f,
                                pathPoints = flattenedPoints,
                                inkColor = 0xFF0F3B66,
                                inkWidthPt = 2.5f
                            )
                            pageElements.add(elem)
                            selectedElementId = elem.id
                        }
                        showSignaturePadModal = false
                    },
                    modifier = Modifier.testTag("confirm_stamp_signature")
                ) {
                    Text("Place Signature")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignaturePadModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export Confirmation Dialog
    if (showExportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCompiling) showExportConfirmDialog = false },
            title = { Text("Export Document as PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Document: $docTitle")
                    Text("Total Pages: $totalPages")
                    Text("Format: ${standardSize.label} (${if (orientation == PageOrientation.PORTRAIT) "Portrait" else "Landscape"})")
                    Text("All vector text, images, shapes, and signatures across all $totalPages pages will be assembled into a clean standard PDF document.")
                    if (isCompiling) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Compiling multi-page PDF...")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isCompiling = true
                        scope.launch {
                            try {
                                val compiledFile = DocumentCreatorEngine.compileMultiPageDocument(
                                    context = context,
                                    pageCount = totalPages,
                                    pageWidthPt = baseWidth,
                                    pageHeightPt = baseHeight,
                                    elementsByPage = pageElementsMap,
                                    outputFileName = docTitle
                                )
                                onRegisterGeneratedFile(Uri.fromFile(compiledFile), compiledFile.name)
                                exportedFileResult = compiledFile
                                showExportConfirmDialog = false
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isCompiling = false
                            }
                        }
                    },
                    enabled = !isCompiling,
                    modifier = Modifier.testTag("execute_export_multipage_pdf")
                ) {
                    Text("Compile & Export")
                }
            },
            dismissButton = {
                if (!isCompiling) {
                    TextButton(onClick = { showExportConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Universal Result Screen
    if (exportedFileResult != null) {
        val file = exportedFileResult!!
        UniversalResultDialog(
            title = "Document PDF Ready!",
            file = file,
            onDismiss = { exportedFileResult = null },
            onOpenFile = {
                val f = file
                exportedFileResult = null
                onOpenPdf(Uri.fromFile(f), f.name)
            },
            onAnotherAction = {
                exportedFileResult = null
                docTitle = "New_Document_${System.currentTimeMillis() % 10000}"
                totalPages = 1
                currentPageIndex = 0
                pageElementsMap.clear()
                pageElementsMap[0] = mutableListOf()
            },
            anotherActionLabel = "Create Another"
        )
    }
}

private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (_: Exception) {
        null
    }
}
