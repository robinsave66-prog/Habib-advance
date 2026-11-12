package com.example.feature.pdfviewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.sharing.FileSharingManager
import com.example.data.preferences.PdfFitMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentUri: Uri,
    documentName: String? = null,
    viewModel: PdfViewerViewModel,
    onBackClick: () -> Unit,
    onEditClick: ((Uri, String) -> Unit)? = null
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showJumpDialog by remember { mutableStateOf(false) }
    var targetPageInput by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var showThumbnailsSheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Smooth Zoom & Pan States
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(documentUri) {
        viewModel.loadDocument(documentUri, documentName)
    }

    // Reset pan when switching pages
    if (uiState is ViewerUiState.Loaded) {
        val state = uiState as ViewerUiState.Loaded
        LaunchedEffect(state.currentPage) {
            if (zoomScale <= 1.05f) {
                panOffsetX = 0f
                panOffsetY = 0f
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
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = {
                                searchInput = it
                                viewModel.searchInPdf(it)
                            },
                            placeholder = { Text("Find text in document...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.searchInPdf(searchInput) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("viewer_search_input")
                        )
                    } else {
                        Column {
                            val titleText = when (val s = uiState) {
                                is ViewerUiState.Loaded -> s.documentInfo.title
                                else -> documentName ?: "Document"
                            }
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (uiState is ViewerUiState.Loaded) {
                                val state = uiState as ViewerUiState.Loaded
                                Text(
                                    text = "Page ${state.currentPage} of ${state.totalPages} • ${(zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (uiState is ViewerUiState.Loaded) {
                        val state = uiState as ViewerUiState.Loaded

                        if (isSearchActive) {
                            if (state.searchMatches.isNotEmpty()) {
                                Text(
                                    text = "${state.currentMatchIndex + 1}/${state.searchMatches.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { viewModel.previousSearchMatch() }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                                }
                                IconButton(onClick = { viewModel.nextSearchMatch() }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                                }
                            }
                            IconButton(onClick = {
                                isSearchActive = false
                                searchInput = ""
                                viewModel.clearSearch()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                            }
                        } else {
                            // Search toggle
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.testTag("viewer_toggle_search_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search in PDF")
                            }

                            // Thumbnails Sheet toggle
                            IconButton(
                                onClick = { showThumbnailsSheet = true },
                                modifier = Modifier.testTag("viewer_thumbnails_button")
                            ) {
                                Icon(Icons.Default.ViewCarousel, contentDescription = "Page Thumbnails")
                            }

                            // Jump to page button
                            TextButton(
                                onClick = {
                                    targetPageInput = state.currentPage.toString()
                                    showJumpDialog = true
                                },
                                modifier = Modifier.testTag("jump_to_page_button")
                            ) {
                                Text(
                                    text = "${state.currentPage}/${state.totalPages}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (onEditClick != null) {
                                IconButton(
                                    onClick = { onEditClick(documentUri, state.documentInfo.title) },
                                    modifier = Modifier.testTag("viewer_edit_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit in PDF Editor")
                                }
                            }

                            // Share via FileSharingManager
                            IconButton(
                                onClick = {
                                    FileSharingManager.shareDocument(
                                        context = context,
                                        uri = documentUri,
                                        fileName = state.documentInfo.title
                                    )
                                },
                                modifier = Modifier.testTag("share_pdf_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share PDF")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (uiState is ViewerUiState.Loaded) {
                val state = uiState as ViewerUiState.Loaded
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Quick Zoom Preset Chips (100%, 150%, 200%, 300%, 350%, Fit Width, Fit Page)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = (zoomScale in 0.95f..1.05f),
                                onClick = {
                                    zoomScale = 1.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                label = { Text("100%") },
                                modifier = Modifier.testTag("zoom_preset_100")
                            )
                            FilterChip(
                                selected = (zoomScale in 1.45f..1.55f),
                                onClick = {
                                    zoomScale = 1.5f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                label = { Text("150%") },
                                modifier = Modifier.testTag("zoom_preset_150")
                            )
                            FilterChip(
                                selected = (zoomScale in 1.95f..2.05f),
                                onClick = {
                                    zoomScale = 2.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                label = { Text("200%") },
                                modifier = Modifier.testTag("zoom_preset_200")
                            )
                            FilterChip(
                                selected = (zoomScale in 2.95f..3.05f),
                                onClick = {
                                    zoomScale = 3.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                label = { Text("300%") },
                                modifier = Modifier.testTag("zoom_preset_300")
                            )
                            FilterChip(
                                selected = (zoomScale in 3.45f..3.55f),
                                onClick = {
                                    zoomScale = 3.5f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                label = { Text("350%") },
                                modifier = Modifier.testTag("zoom_preset_350")
                            )

                            // Fit Width & Fit Page
                            FilterChip(
                                selected = state.fitMode == PdfFitMode.FIT_WIDTH,
                                onClick = {
                                    viewModel.setFitMode(PdfFitMode.FIT_WIDTH)
                                    zoomScale = 1.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                label = { Text("Fit Width") },
                                modifier = Modifier.testTag("fit_mode_width")
                            )
                            FilterChip(
                                selected = state.fitMode == PdfFitMode.FIT_PAGE,
                                onClick = {
                                    viewModel.setFitMode(PdfFitMode.FIT_PAGE)
                                    zoomScale = 0.85f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                },
                                label = { Text("Fit Page") },
                                modifier = Modifier.testTag("fit_mode_page")
                            )
                        }

                        // Navigation Strip & Zoom Steppers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Page Prev / Next Steppers
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (state.currentPage > 1) {
                                            viewModel.onPageChanged(state.currentPage - 1)
                                            if (zoomScale <= 1.05f) {
                                                panOffsetX = 0f
                                                panOffsetY = 0f
                                            }
                                        }
                                    },
                                    enabled = state.currentPage > 1,
                                    modifier = Modifier.testTag("viewer_prev_page")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowLeft, contentDescription = "Previous Page")
                                }

                                Text(
                                    text = "Page ${state.currentPage} of ${state.totalPages}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        if (state.currentPage < state.totalPages) {
                                            viewModel.onPageChanged(state.currentPage + 1)
                                            if (zoomScale <= 1.05f) {
                                                panOffsetX = 0f
                                                panOffsetY = 0f
                                            }
                                        }
                                    },
                                    enabled = state.currentPage < state.totalPages,
                                    modifier = Modifier.testTag("viewer_next_page")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = "Next Page")
                                }
                            }

                            // Zoom +/- Controls
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.8f)
                                        if (zoomScale <= 1.0f) {
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        }
                                    },
                                    modifier = Modifier.testTag("zoom_out_button")
                                ) {
                                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                                }
                                Text(
                                    text = "${(zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = {
                                        zoomScale = (zoomScale + 0.25f).coerceAtMost(3.5f)
                                    },
                                    modifier = Modifier.testTag("zoom_in_button")
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is ViewerUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text("Rendering document pages...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            is ViewerUiState.PasswordProtected -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(24.dp)) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("Password Protected Document", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            is ViewerUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(24.dp)) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Text("Unable to Open PDF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            is ViewerUiState.Loaded -> {
                // Continuous document mode: lazily render every page in one vertical stream.
                val listState = rememberLazyListState()

                LaunchedEffect(listState, state.totalPages) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .distinctUntilChanged()
                        .collect { visibleIndex ->
                            viewModel.onPageChanged((visibleIndex + 1).coerceIn(1, state.totalPages))
                        }
                }

                // Keep page navigation, search matches and jump-to-page synchronized with scrolling.
                LaunchedEffect(state.currentPage, state.totalPages) {
                    val targetIndex = (state.currentPage - 1).coerceIn(0, state.totalPages - 1)
                    if (listState.firstVisibleItemIndex != targetIndex) {
                        listState.animateScrollToItem(targetIndex)
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFFE2E8F0))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(0.8f, 3.5f)
                                if (zoomScale > 1.0f) {
                                    panOffsetX += pan.x
                                    panOffsetY += pan.y
                                } else {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    zoomScale = if (zoomScale > 1.2f) 1.0f else 2.0f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            )
                        }
                ) {
                    val pageWidth = maxWidth * zoomScale
                    val density = LocalDensity.current
                    val pageWidthPx = with(density) { pageWidth.toPx().toInt() }.coerceIn(600, 3200)

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .width(pageWidth)
                                .fillMaxHeight()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.totalPages, key = { it }) { pageIndex ->
                                    val dimensions = viewModel.getPageDimensions(pageIndex) ?: Pair(595, 842)
                                    val pageAspectRatio = dimensions.first.toFloat()
                                        .coerceAtLeast(100f) / dimensions.second.toFloat().coerceAtLeast(100f)
                                    val targetHeightPx = (pageWidthPx / pageAspectRatio).toInt().coerceIn(600, 4200)

                                    val pageBitmap by produceState<Bitmap?>(
                                        initialValue = null,
                                        key1 = pageIndex,
                                        key2 = pageWidthPx
                                    ) {
                                        value = viewModel.renderPageBitmap(pageIndex, pageWidthPx, targetHeightPx)
                                    }

                                    val isMatch = state.searchMatches.any { it.pageNumber == pageIndex + 1 }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(pageAspectRatio)
                                            .shadow(7.dp, RoundedCornerShape(3.dp))
                                            .background(Color.White, RoundedCornerShape(3.dp))
                                            .border(
                                                width = if (isMatch) 3.dp else 1.dp,
                                                color = if (isMatch) Color(0xFFF59E0B) else Color(0xFFCBD5E1),
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                            .testTag("pdf_rendered_page_${pageIndex + 1}")
                                    ) {
                                        if (pageBitmap != null && !pageBitmap!!.isRecycled) {
                                            Image(
                                                bitmap = pageBitmap!!.asImageBitmap(),
                                                contentDescription = "PDF Page ${pageIndex + 1}",
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(30.dp),
                                                    strokeWidth = 2.5.dp
                                                )
                                            }
                                        }

                                        if (isMatch) {
                                            Surface(
                                                color = Color(0xFFF59E0B),
                                                shape = RoundedCornerShape(bottomStart = 8.dp),
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Text(
                                                    text = "SEARCH MATCH",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Surface(
                                            color = Color.Black.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(topEnd = 6.dp, bottomStart = 6.dp),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = "${pageIndex + 1} / ${state.totalPages}",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Page Thumbnails Bottom Sheet
                if (showThumbnailsSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showThumbnailsSheet = false },
                        sheetState = rememberModalBottomSheetState()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Page Thumbnails (${state.totalPages} Pages)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.totalPages) { pIdx ->
                                    val pageNum = pIdx + 1
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (state.currentPage == pageNum) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .size(80.dp, 110.dp)
                                            .border(
                                                width = if (state.currentPage == pageNum) 2.dp else 0.5.dp,
                                                color = if (state.currentPage == pageNum) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel.onPageChanged(pageNum)
                                                zoomScale = 1.0f
                                                panOffsetX = 0f
                                                panOffsetY = 0f
                                                showThumbnailsSheet = false
                                            }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Page $pageNum", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
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

    // Jump to Page Dialog
    if (showJumpDialog && uiState is ViewerUiState.Loaded) {
        val state = uiState as ViewerUiState.Loaded
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column {
                    Text("Enter page number (1 to ${state.totalPages}):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetPageInput,
                        onValueChange = { targetPageInput = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("jump_page_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = targetPageInput.toIntOrNull()
                        if (target != null && target in 1..state.totalPages) {
                            viewModel.onPageChanged(target)
                            zoomScale = 1.0f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        }
                        showJumpDialog = false
                    },
                    modifier = Modifier.testTag("confirm_jump_page_button")
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
