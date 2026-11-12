package com.example.feature.imagestudio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.sharing.FileSharingManager
import com.example.feature.filemanager.formatFileSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiImageToPdfScreen(
    viewModel: MultiImageToPdfViewModel,
    onBackClick: () -> Unit,
    onOpenPdf: (Uri, String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val images by viewModel.images.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var outputNameInput by remember { mutableStateOf("Images_Document_${System.currentTimeMillis() % 10000}") }
    var previewImageItem by remember { mutableStateOf<ImageItem?>(null) }
    var captionEditingItem by remember { mutableStateOf<ImageItem?>(null) }
    var captionTextInput by remember { mutableStateOf("") }
    var captionTextColorInput by remember { mutableStateOf(0xFF0F3B66) }
    var captionBgColorInput by remember { mutableStateOf<Long?>(0xCCFFFFFF) }

    // Multi-image picker launcher
    val pickMultipleMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
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
                        modifier = Modifier.testTag("multi_image_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Image to PDF Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${images.size} ${if (images.size == 1) "image" else "images"} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("pdf_page_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Page Settings")
                    }
                    if (images.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearAll() },
                            modifier = Modifier.testTag("clear_all_images_button")
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // + Add More Images Button
                    OutlinedButton(
                        onClick = {
                            pickMultipleMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("add_more_images_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (images.isEmpty()) "Select Images" else "+ Add More Images")
                    }

                    // Create PDF Button
                    Button(
                        onClick = {
                            if (images.isEmpty()) {
                                pickMultipleMedia.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                showNameDialog = true
                            }
                        },
                        enabled = images.isNotEmpty() && uiState !is MultiImagePdfState.Generating,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("create_pdf_from_images_button")
                    ) {
                        if (uiState is MultiImagePdfState.Generating) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Creating...")
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create PDF")
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
            if (images.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(88.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "No Images Added Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select one or multiple photos from your device.\nYou can add more photos anytime, reorder, rotate, and customize page margins.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            pickMultipleMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("select_initial_images_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Images from Gallery")
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Quick Settings Summary Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page: ${settings.pageSize.title.split(" ")[0]} • ${settings.orientation.name.lowercase().capitalize()} • Margin: ${settings.marginPt.toInt()}pt",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap photo to preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Grid of selected images with reorder buttons, rotation, and deletion
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = images,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            ImageThumbnailCard(
                                item = item,
                                pageNumber = index + 1,
                                canMoveLeft = index > 0,
                                canMoveRight = index < images.size - 1,
                                onClick = { previewImageItem = item },
                                onRotate = { viewModel.rotateImage(item.id) },
                                onDelete = { viewModel.removeImage(item.id) },
                                onMoveLeft = { viewModel.moveImage(index, index - 1) },
                                onMoveRight = { viewModel.moveImage(index, index + 1) },
                                onEditCaption = {
                                    captionEditingItem = item
                                    captionTextInput = item.caption
                                    captionTextColorInput = item.captionTextColor
                                    captionBgColorInput = item.captionBgColor
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // PDF Page Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("PDF Page Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Page Format",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageSizeStandard.values().forEach { standard ->
                            FilterChip(
                                selected = settings.pageSize == standard,
                                onClick = { viewModel.updateSettings(settings.copy(pageSize = standard)) },
                                label = { Text(standard.name) }
                            )
                        }
                    }

                    Text(
                        text = "Page Orientation",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageOrientation.values().forEach { orient ->
                            FilterChip(
                                selected = settings.orientation == orient,
                                onClick = { viewModel.updateSettings(settings.copy(orientation = orient)) },
                                label = { Text(orient.name.lowercase().capitalize()) }
                            )
                        }
                    }

                    Text(
                        text = "Scaling Mode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.scaleMode == ImageScaleMode.FIT_PAGE,
                            onClick = { viewModel.updateSettings(settings.copy(scaleMode = ImageScaleMode.FIT_PAGE)) },
                            label = { Text("Fit Page") }
                        )
                        FilterChip(
                            selected = settings.scaleMode == ImageScaleMode.STRETCH_FILL,
                            onClick = { viewModel.updateSettings(settings.copy(scaleMode = ImageScaleMode.STRETCH_FILL)) },
                            label = { Text("Stretch Fill") }
                        )
                    }

                    Text(
                        text = "Page Margins: ${settings.marginPt.toInt()} pt",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = settings.marginPt,
                        onValueChange = { viewModel.updateSettings(settings.copy(marginPt = it)) },
                        valueRange = 0f..72f,
                        steps = 5
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Apply")
                }
            }
        )
    }

    // Name PDF Dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Save Generated PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter a file name for the ${images.size}-page PDF document:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = outputNameInput,
                        onValueChange = { outputNameInput = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_file_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNameDialog = false
                        viewModel.generatePdf(outputNameInput) { /* state observer handles success */ }
                    },
                    modifier = Modifier.testTag("confirm_create_pdf_button")
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Success Result Dialog with UniversalResultDialog
    if (uiState is MultiImagePdfState.Success) {
        val success = uiState as MultiImagePdfState.Success
        com.example.ui.components.UniversalResultDialog(
            title = "PDF Created Successfully!",
            file = success.generatedFile,
            additionalDetails = listOf(
                "Pages" to "${success.pageCount}",
                "Page Setup" to "${settings.pageSize.name} • ${settings.orientation.name}",
                "Quality" to "${settings.compressQualityPercent}%"
            ),
            onDismiss = { viewModel.resetState() },
            onOpenFile = {
                val pdfUri = Uri.fromFile(success.generatedFile)
                val name = success.generatedFile.name
                viewModel.resetState()
                onOpenPdf(pdfUri, name)
            },
            onAnotherAction = {
                viewModel.resetState()
            },
            anotherActionLabel = "Create Another PDF"
        )
    }

    // Single Image Preview Modal
    if (previewImageItem != null) {
        val item = previewImageItem!!
        AlertDialog(
            onDismissRequest = { previewImageItem = null },
            title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(item.rotationDegrees)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { previewImageItem = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Caption Editing Dialog
    if (captionEditingItem != null) {
        val targetItem = captionEditingItem!!
        AlertDialog(
            onDismissRequest = { captionEditingItem = null },
            title = { Text("Edit Page Caption", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Add a title, note, or watermark caption to this page:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = captionTextInput,
                        onValueChange = { captionTextInput = it },
                        label = { Text("Caption Text") },
                        placeholder = { Text("e.g., Page Title, Date, Reference") },
                        modifier = Modifier.fillMaxWidth().testTag("image_caption_input")
                    )

                    // Text Color Selector
                    Text("Text Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            0xFF000000 to "Black",
                            0xFF0F3B66 to "Navy",
                            0xFFDC2626 to "Red",
                            0xFF16A34A to "Green",
                            0xFFFFFFFF to "White"
                        ).forEach { (colorLong, name) ->
                            val isSelected = captionTextColorInput == colorLong
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorLong))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { captionTextColorInput = colorLong }
                            )
                        }
                    }

                    // Background Color Selector
                    Text("Background Banner", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            null to "None",
                            0xCCFFFFFF to "White",
                            0xCC000000 to "Dark",
                            0xCCFEF08A to "Yellow",
                            0xCCBFDBFE to "Blue"
                        ).forEach { (bgColorLong, label) ->
                            FilterChip(
                                selected = captionBgColorInput == bgColorLong,
                                onClick = { captionBgColorInput = bgColorLong },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateImageCaption(
                            id = targetItem.id,
                            caption = captionTextInput,
                            textColor = captionTextColorInput,
                            bgColor = captionBgColorInput
                        )
                        captionEditingItem = null
                    },
                    modifier = Modifier.testTag("apply_caption_button")
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        captionEditingItem = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ImageThumbnailCard(
    item: ImageItem,
    pageNumber: Int,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onClick: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onEditCaption: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("image_thumb_$pageNumber")
    ) {
        Column {
            // Thumbnail Image Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFFE2E8F0))
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = "Page $pageNumber",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(item.rotationDegrees)
                )

                // Page badge
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "Pg $pageNumber",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Delete top right icon
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clickable { onDelete() }
                        .testTag("delete_thumb_$pageNumber")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Caption badge if set
            if (item.caption.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditCaption() }
                ) {
                    Text(
                        text = "Caption: ${item.caption}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Toolbar under each card: Reorder left, Edit Caption, Rotate, Reorder right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveLeft,
                    enabled = canMoveLeft,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Move earlier",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onEditCaption,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("caption_thumb_$pageNumber")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Caption",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onRotate,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("rotate_thumb_$pageNumber")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate 90 degrees",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onMoveRight,
                    enabled = canMoveRight,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move later",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
