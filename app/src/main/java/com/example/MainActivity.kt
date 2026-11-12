package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.sample.SamplePdfGenerator
import com.example.data.local.db.AppDatabase
import com.example.data.local.entity.DocumentType
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.DocumentRepository
import com.example.feature.documentcreator.CreateMultiPagePdfScreen
import com.example.feature.documentcreator.DocumentCreatorEngine
import com.example.feature.filemanager.FilterCategory
import com.example.feature.filemanager.FileManagerScreen
import com.example.feature.filemanager.FileManagerViewModel
import com.example.feature.filemanager.FileManagerViewModelFactory
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.home.HomeViewModelFactory
import com.example.feature.home.QuickFeatureItem
import com.example.feature.imagestudio.MultiImageToPdfScreen
import com.example.feature.imagestudio.MultiImageToPdfViewModel
import com.example.feature.imagestudio.MultiImageToPdfViewModelFactory
import com.example.feature.operations.CompressPdfScreen
import com.example.feature.operations.ConvertFilesHubScreen
import com.example.feature.operations.DocxConversionMode
import com.example.feature.operations.DocxConversionScreen
import com.example.feature.operations.MergeSplitMode
import com.example.feature.operations.MergeSplitPdfScreen
import com.example.feature.operations.OcrScannerScreen
import com.example.feature.operations.SignPdfScreen
import com.example.feature.documentviewer.DocumentViewerScreen
import com.example.feature.pdfeditor.PdfEditorScreen
import com.example.feature.pdfeditor.PdfEditorViewModel
import com.example.feature.pdfeditor.PdfEditorViewModelFactory
import com.example.feature.pdfviewer.PdfViewerScreen
import com.example.feature.pdfviewer.PdfViewerViewModel
import com.example.feature.pdfviewer.PdfViewerViewModelFactory
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.settings.SettingsViewModelFactory
import com.example.feature.tools.ToolsScreen
import com.example.ui.theme.HabibAdvanceEditorTheme
import kotlinx.coroutines.launch
import java.io.File

enum class ScreenTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    FILES("Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    RECENT("Recent", Icons.Filled.History, Icons.Outlined.History),
    TOOLS("Tools", Icons.Filled.Widgets, Icons.Outlined.Widgets),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val documentRepository by lazy { DocumentRepository(database.documentDao(), applicationContext) }
    private val preferencesRepository by lazy { PreferencesRepository(applicationContext) }

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(documentRepository, SamplePdfGenerator, applicationContext)
    }
    private val fileManagerViewModel: FileManagerViewModel by viewModels {
        FileManagerViewModelFactory(documentRepository)
    }
    private val pdfViewerViewModel: PdfViewerViewModel by viewModels {
        PdfViewerViewModelFactory(applicationContext, documentRepository, preferencesRepository)
    }
    private val pdfEditorViewModel: PdfEditorViewModel by viewModels {
        PdfEditorViewModelFactory(applicationContext)
    }
    private val multiImageToPdfViewModel: MultiImageToPdfViewModel by viewModels {
        MultiImageToPdfViewModelFactory(applicationContext)
    }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(applicationContext, preferencesRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val intentPdfUri: Uri? = intent?.data

        setContent {
            val userPrefs by preferencesRepository.userPreferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.example.data.preferences.UserPreferences()
            )

            val isDark = when (userPrefs.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            HabibAdvanceEditorTheme(darkTheme = isDark) {
                MainAppHost(
                    homeViewModel = homeViewModel,
                    fileManagerViewModel = fileManagerViewModel,
                    pdfViewerViewModel = pdfViewerViewModel,
                    pdfEditorViewModel = pdfEditorViewModel,
                    multiImageViewModel = multiImageToPdfViewModel,
                    settingsViewModel = settingsViewModel,
                    initialPdfUri = intentPdfUri
                )
            }
        }
    }
}

@Composable
fun MainAppHost(
    homeViewModel: HomeViewModel,
    fileManagerViewModel: FileManagerViewModel,
    pdfViewerViewModel: PdfViewerViewModel,
    pdfEditorViewModel: PdfEditorViewModel,
    multiImageViewModel: MultiImageToPdfViewModel,
    settingsViewModel: SettingsViewModel,
    initialPdfUri: Uri? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
    var viewingDocumentUri by remember { mutableStateOf<Uri?>(initialPdfUri) }
    var viewingDocumentName by remember { mutableStateOf<String?>(initialPdfUri?.lastPathSegment) }

    var editingDocumentUri by remember { mutableStateOf<Uri?>(null) }
    var editingDocumentName by remember { mutableStateOf<String?>(null) }
    var showImageToPdfStudio by remember { mutableStateOf(false) }

    // Operation Screen States
    var showDocxConversion by remember { mutableStateOf(false) }
    var docxConversionMode by remember { mutableStateOf(DocxConversionMode.PDF_TO_WORD) }

    var showMergeSplit by remember { mutableStateOf(false) }
    var mergeSplitMode by remember { mutableStateOf(MergeSplitMode.MERGE) }

    var showCompressPdf by remember { mutableStateOf(false) }
    var showOcrScanner by remember { mutableStateOf(false) }
    var showSignPdf by remember { mutableStateOf(false) }
    var showConvertHub by remember { mutableStateOf(false) }

    var showCreateMultiPagePdf by remember { mutableStateOf(false) }
    var showCreatePdfDialog by remember { mutableStateOf(false) }
    var newDocTitle by remember { mutableStateOf("") }
    var newDocContent by remember { mutableStateOf("") }

    var comingSoonFeature by remember { mutableStateOf<QuickFeatureItem?>(null) }

    // SAF Document Picker for Opening PDF in Reader
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            fileManagerViewModel.registerImportedFile(uri)
            viewingDocumentUri = uri
            viewingDocumentName = null
        }
    }

    // SAF Document Picker for Opening PDF directly in Editor
    val openPdfForEditorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            fileManagerViewModel.registerImportedFile(uri)
            editingDocumentUri = uri
            editingDocumentName = null
        }
    }

    // 1. Full-screen PDF Editor Mode
    if (editingDocumentUri != null) {
        PdfEditorScreen(
            documentUri = editingDocumentUri!!,
            documentName = editingDocumentName,
            viewModel = pdfEditorViewModel,
            onBackClick = {
                editingDocumentUri = null
                editingDocumentName = null
            },
            onOpenExportedPdf = { exportedUri, exportedName ->
                fileManagerViewModel.registerImportedFile(exportedUri, exportedName)
                editingDocumentUri = null
                editingDocumentName = null
                viewingDocumentUri = exportedUri
                viewingDocumentName = exportedName
            }
        )
        return
    }

    // 2. Full-screen Multi-Image to PDF Studio
    if (showImageToPdfStudio) {
        MultiImageToPdfScreen(
            viewModel = multiImageViewModel,
            onBackClick = { showImageToPdfStudio = false },
            onOpenPdf = { pdfUri, pdfName ->
                fileManagerViewModel.registerImportedFile(pdfUri, pdfName)
                showImageToPdfStudio = false
                viewingDocumentUri = pdfUri
                viewingDocumentName = pdfName
            }
        )
        return
    }

    // 3. Full-screen DOCX Conversion Screen (PDF to Word / Word to PDF)
    if (showDocxConversion) {
        DocxConversionScreen(
            initialMode = docxConversionMode,
            onBackClick = { showDocxConversion = false },
            onOpenPdf = { pdfUri, pdfName ->
                fileManagerViewModel.registerImportedFile(pdfUri, pdfName)
                showDocxConversion = false
                viewingDocumentUri = pdfUri
                viewingDocumentName = pdfName
            },
            onRegisterGeneratedFile = { uri, name ->
                fileManagerViewModel.registerImportedFile(uri, name)
            }
        )
        return
    }

    // 4. Full-screen Merge / Split Screen
    if (showMergeSplit) {
        MergeSplitPdfScreen(
            initialMode = mergeSplitMode,
            onBackClick = { showMergeSplit = false },
            onOpenPdf = { pdfUri, pdfName ->
                fileManagerViewModel.registerImportedFile(pdfUri, pdfName)
                showMergeSplit = false
                viewingDocumentUri = pdfUri
                viewingDocumentName = pdfName
            },
            onRegisterGeneratedFile = { uri, name ->
                fileManagerViewModel.registerImportedFile(uri, name)
            }
        )
        return
    }

    // 5. Full-screen Compress PDF Screen
    if (showCompressPdf) {
        CompressPdfScreen(
            onBackClick = { showCompressPdf = false },
            onOpenPdf = { pdfUri, pdfName ->
                fileManagerViewModel.registerImportedFile(pdfUri, pdfName)
                showCompressPdf = false
                viewingDocumentUri = pdfUri
                viewingDocumentName = pdfName
            },
            onRegisterGeneratedFile = { uri, name ->
                fileManagerViewModel.registerImportedFile(uri, name)
            }
        )
        return
    }

    // 6. Full-screen OCR Scanner Screen
    if (showOcrScanner) {
        OcrScannerScreen(
            onBackClick = { showOcrScanner = false },
            onOpenPdf = { pdfUri, pdfName ->
                fileManagerViewModel.registerImportedFile(pdfUri, pdfName)
                showOcrScanner = false
                viewingDocumentUri = pdfUri
                viewingDocumentName = pdfName
            },
            onRegisterGeneratedFile = { uri, name ->
                fileManagerViewModel.registerImportedFile(uri, name)
            }
        )
        return
    }

    // 7. Full-screen Sign PDF Screen
    if (showSignPdf) {
        SignPdfScreen(
            onBackClick = { showSignPdf = false },
            onOpenPdf = { pdfUri, pdfName ->
                fileManagerViewModel.registerImportedFile(pdfUri, pdfName)
                showSignPdf = false
                viewingDocumentUri = pdfUri
                viewingDocumentName = pdfName
            },
            onRegisterGeneratedFile = { uri, name ->
                fileManagerViewModel.registerImportedFile(uri, name)
            }
        )
        return
    }

    // 8. Full-screen Convert Files Hub Screen
    if (showConvertHub) {
        ConvertFilesHubScreen(
            onBackClick = { showConvertHub = false },
            onSelectConverter = { converterId ->
                showConvertHub = false
                when (converterId) {
                    "pdf_to_word" -> {
                        docxConversionMode = DocxConversionMode.PDF_TO_WORD
                        showDocxConversion = true
                    }
                    "word_to_pdf" -> {
                        docxConversionMode = DocxConversionMode.WORD_TO_PDF
                        showDocxConversion = true
                    }
                    "image_to_pdf" -> showImageToPdfStudio = true
                    "merge_pdf" -> {
                        mergeSplitMode = MergeSplitMode.MERGE
                        showMergeSplit = true
                    }
                    "split_pdf" -> {
                        mergeSplitMode = MergeSplitMode.SPLIT
                        showMergeSplit = true
                    }
                    "compress_pdf" -> showCompressPdf = true
                    "ocr_scanner" -> showOcrScanner = true
                    "sign_pdf" -> showSignPdf = true
                }
            }
        )
        return
    }

    // 8.5 Full-screen Multi-Page Document Creator Mode
    if (showCreateMultiPagePdf) {
        CreateMultiPagePdfScreen(
            onBackClick = { showCreateMultiPagePdf = false },
            onOpenPdf = { pdfUri, pdfName ->
                fileManagerViewModel.registerImportedFile(pdfUri, pdfName)
                showCreateMultiPagePdf = false
                viewingDocumentUri = pdfUri
                viewingDocumentName = pdfName
            },
            onRegisterGeneratedFile = { uri, name ->
                fileManagerViewModel.registerImportedFile(uri, name)
            }
        )
        return
    }

    // 9. Full-screen Universal Document Viewer Mode (PDF, DOCX, XLSX, PPTX, TXT, Images)
    if (viewingDocumentUri != null) {
        DocumentViewerScreen(
            documentUri = viewingDocumentUri!!,
            documentName = viewingDocumentName,
            pdfViewerViewModel = pdfViewerViewModel,
            onBackClick = {
                viewingDocumentUri = null
                viewingDocumentName = null
            },
            onEditPdfClick = { uri, name ->
                viewingDocumentUri = null
                viewingDocumentName = null
                editingDocumentUri = uri
                editingDocumentName = name
            },
            onConvertToPdfRequested = { pdfUri, pdfName ->
                fileManagerViewModel.registerImportedFile(pdfUri, pdfName)
                viewingDocumentUri = pdfUri
                viewingDocumentName = pdfName
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                ScreenTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (tab == ScreenTab.RECENT) {
                                fileManagerViewModel.setFilterCategory(FilterCategory.ALL)
                            }
                            currentTab = tab
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                ScreenTab.HOME -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onOpenPdfPicker = {
                            openPdfLauncher.launch(arrayOf("*/*"))
                        },
                        onOpenDocument = { uri, name ->
                            viewingDocumentUri = uri
                            viewingDocumentName = name
                        },
                        onCreateBlankPdf = { showCreateMultiPagePdf = true },
                        onConvertImageToPdf = {
                            showImageToPdfStudio = true
                        },
                        onOpenSettings = { currentTab = ScreenTab.SETTINGS },
                        onViewAllFiles = { currentTab = ScreenTab.FILES },
                        onFeatureClick = { feature ->
                            when (feature.id) {
                                "pdf_editor" -> openPdfForEditorLauncher.launch(arrayOf("application/pdf"))
                                "open_pdf" -> openPdfLauncher.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/plain", "image/*", "*/*"))
                                "create_pdf" -> showCreateMultiPagePdf = true
                                "image_to_pdf" -> showImageToPdfStudio = true
                                "pdf_to_word" -> {
                                    docxConversionMode = DocxConversionMode.PDF_TO_WORD
                                    showDocxConversion = true
                                }
                                "word_to_pdf" -> {
                                    docxConversionMode = DocxConversionMode.WORD_TO_PDF
                                    showDocxConversion = true
                                }
                                "merge_pdf" -> {
                                    mergeSplitMode = MergeSplitMode.MERGE
                                    showMergeSplit = true
                                }
                                "split_pdf" -> {
                                    mergeSplitMode = MergeSplitMode.SPLIT
                                    showMergeSplit = true
                                }
                                "compress_pdf" -> showCompressPdf = true
                                "ocr_scanner" -> showOcrScanner = true
                                "sign_pdf" -> showSignPdf = true
                                "convert_files" -> showConvertHub = true
                                else -> {
                                    if (feature.isComingSoon) comingSoonFeature = feature
                                }
                            }
                        }
                    )
                }

                ScreenTab.FILES, ScreenTab.RECENT -> {
                    FileManagerScreen(
                        viewModel = fileManagerViewModel,
                        onOpenDocument = { uri, name ->
                            viewingDocumentUri = uri
                            viewingDocumentName = name
                        },
                        onPickExternalFile = {
                            openPdfLauncher.launch(arrayOf("application/pdf", "*/*"))
                        }
                    )
                }

                ScreenTab.TOOLS -> {
                    ToolsScreen(
                        onFeatureClick = { tool ->
                            when (tool.id) {
                                "pdf_editor" -> openPdfForEditorLauncher.launch(arrayOf("application/pdf"))
                                "open_pdf" -> openPdfLauncher.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/plain", "image/*", "*/*"))
                                "create_pdf" -> showCreateMultiPagePdf = true
                                "image_to_pdf" -> showImageToPdfStudio = true
                                "pdf_to_word" -> {
                                    docxConversionMode = DocxConversionMode.PDF_TO_WORD
                                    showDocxConversion = true
                                }
                                "word_to_pdf" -> {
                                    docxConversionMode = DocxConversionMode.WORD_TO_PDF
                                    showDocxConversion = true
                                }
                                "merge_pdf" -> {
                                    mergeSplitMode = MergeSplitMode.MERGE
                                    showMergeSplit = true
                                }
                                "split_pdf" -> {
                                    mergeSplitMode = MergeSplitMode.SPLIT
                                    showMergeSplit = true
                                }
                                "compress_pdf" -> showCompressPdf = true
                                "ocr_scanner" -> showOcrScanner = true
                                "sign_pdf" -> showSignPdf = true
                                "convert_files" -> showConvertHub = true
                                else -> {
                                    if (tool.isComingSoon) comingSoonFeature = tool
                                }
                            }
                        }
                    )
                }

                ScreenTab.SETTINGS -> {
                    SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }

    // Create New Blank PDF Dialog
    if (showCreatePdfDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePdfDialog = false },
            title = { Text("Create New PDF Document") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newDocTitle,
                        onValueChange = { newDocTitle = it },
                        label = { Text("Document Title") },
                        placeholder = { Text("e.g. Project Proposal") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_pdf_title_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newDocContent,
                        onValueChange = { newDocContent = it },
                        label = { Text("Initial Text Content") },
                        placeholder = { Text("Enter document notes or content...") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_pdf_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = if (newDocTitle.isNotBlank()) newDocTitle else "New Document"
                        val lines = if (newDocContent.isNotBlank()) {
                            newDocContent.split("\n")
                        } else {
                            listOf("This is a blank PDF document created in Habib Advance Editor.")
                        }

                        scope.launch {
                            val newFile = DocumentCreatorEngine.createNewPdf(context, title, lines)
                            val newUri = Uri.fromFile(newFile)
                            fileManagerViewModel.registerImportedFile(newUri, newFile.name)
                            viewingDocumentUri = newUri
                            viewingDocumentName = newFile.name
                            Toast.makeText(context, "PDF Created successfully!", Toast.LENGTH_SHORT).show()
                        }

                        showCreatePdfDialog = false
                        newDocTitle = ""
                        newDocContent = ""
                    },
                    modifier = Modifier.testTag("confirm_create_pdf_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePdfDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Coming Soon / Roadmap Modal
    if (comingSoonFeature != null) {
        val feat = comingSoonFeature!!
        AlertDialog(
            onDismissRequest = { comingSoonFeature = null },
            icon = {
                Icon(
                    imageVector = feat.icon,
                    contentDescription = null,
                    tint = feat.iconColor,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "${feat.title} (${feat.badgeLabel ?: "Coming Soon"})",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This feature is planned for Phase 3.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Habib Advance Editor Phase 2 establishes complete Multi-Image to PDF studio and the full-featured PDF Editor (Text, Shapes, Images, Signatures & Vector Export). The modular engine interfaces for ${feat.title} are pre-architected in the engine package.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { comingSoonFeature = null },
                    modifier = Modifier.testTag("dismiss_coming_soon")
                ) {
                    Text("Got it")
                }
            }
        )
    }
}
