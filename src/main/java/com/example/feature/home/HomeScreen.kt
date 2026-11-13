package com.example.feature.home

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.DocumentType
import com.example.feature.filemanager.formatFileSize
import com.example.ui.theme.DocImageGreen
import com.example.ui.theme.DocPdfRed
import com.example.ui.theme.DocWordBlue
import com.example.ui.theme.HabibGoldAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QuickFeatureItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val isComingSoon: Boolean = false,
    val badgeLabel: String? = null
)

val QuickFeaturesList = listOf(
    QuickFeatureItem(
        id = "open_pdf",
        title = "Open Document",
        subtitle = "PDF, Office, Images, Text",
        icon = Icons.Default.FolderOpen,
        iconColor = Color(0xFF1E88E5),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "pdf_editor",
        title = "PDF Editor",
        subtitle = "Annotate & edit pages",
        icon = Icons.Default.EditNote,
        iconColor = Color(0xFFE53935),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "create_pdf",
        title = "Create PDF",
        subtitle = "New blank document",
        icon = Icons.Default.AddCircleOutline,
        iconColor = Color(0xFF43A047),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "pdf_to_word",
        title = "PDF to Word",
        subtitle = "Export to .docx",
        icon = Icons.Default.Description,
        iconColor = Color(0xFF1976D2),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "word_to_pdf",
        title = "Word to PDF",
        subtitle = "Compile to PDF",
        icon = Icons.Default.SwapHoriz,
        iconColor = Color(0xFF0D47A1),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "image_to_pdf",
        title = "Image to PDF",
        subtitle = "Convert photos to PDF",
        icon = Icons.Default.Image,
        iconColor = Color(0xFF2E7D32),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "merge_pdf",
        title = "Merge PDF",
        subtitle = "Combine files",
        icon = Icons.Default.Merge,
        iconColor = Color(0xFF8E24AA),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "split_pdf",
        title = "Split PDF",
        subtitle = "Extract page ranges",
        icon = Icons.Default.CallSplit,
        iconColor = Color(0xFFFB8C00),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "compress_pdf",
        title = "Compress PDF",
        subtitle = "Reduce file size",
        icon = Icons.Default.Compress,
        iconColor = Color(0xFF00ACC1),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "ocr_scanner",
        title = "OCR Scanner",
        subtitle = "Extract image text",
        icon = Icons.Default.AutoAwesome,
        iconColor = Color(0xFF5E35B1),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "sign_pdf",
        title = "Sign PDF",
        subtitle = "Digital ink signatures",
        icon = Icons.Default.Draw,
        iconColor = Color(0xFF0F3B66),
        isComingSoon = false,
        badgeLabel = "Active"
    ),
    QuickFeatureItem(
        id = "convert_files",
        title = "Convert Files",
        subtitle = "Multi-format suite",
        icon = Icons.Default.TextSnippet,
        iconColor = Color(0xFF3949AB),
        isComingSoon = false,
        badgeLabel = "Active"
    )
)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPdfPicker: () -> Unit,
    onOpenDocument: (Uri, String) -> Unit,
    onCreateBlankPdf: () -> Unit,
    onConvertImageToPdf: () -> Unit,
    onOpenSettings: () -> Unit,
    onViewAllFiles: () -> Unit,
    onFeatureClick: (QuickFeatureItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // A. Top Header Section
        item {
            HomeTopHeader(
                searchQuery = uiState.searchQuery,
                onSearchChange = viewModel::onSearchQueryChanged,
                onSettingsClick = onOpenSettings
            )
        }

        // B. Quick Access Features Grid Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Access Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "12 Features",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Quick Access Features - 3 column responsive cards
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(QuickFeaturesList.take(6)) { feature ->
                    FeatureCardItem(
                        feature = feature,
                        onClick = {
                            when (feature.id) {
                                "open_pdf" -> onOpenPdfPicker()
                                "create_pdf" -> onCreateBlankPdf()
                                "image_to_pdf" -> onConvertImageToPdf()
                                else -> onFeatureClick(feature)
                            }
                        }
                    )
                }
            }
        }

        // Secondary feature row (Remaining 6 features preview)
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(QuickFeaturesList.drop(6)) { feature ->
                    FeatureCardItem(
                        feature = feature,
                        onClick = { onFeatureClick(feature) }
                    )
                }
            }
        }

        // C. Recent Documents Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${uiState.recentDocuments.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                TextButton(
                    onClick = onViewAllFiles,
                    modifier = Modifier.testTag("view_all_files_button")
                ) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Recent Documents List
        if (uiState.recentDocuments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No recent documents",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Open a PDF or browse files to see them here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = uiState.recentDocuments,
                key = { it.uri }
            ) { doc ->
                RecentDocumentCard(
                    doc = doc,
                    onClick = { onOpenDocument(Uri.parse(doc.uri), doc.name) },
                    onFavoriteClick = { viewModel.toggleFavorite(doc) },
                    onRemoveClick = { viewModel.removeRecentDocument(doc.uri) }
                )
            }
        }
    }
}

@Composable
private fun HomeTopHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // App Identity Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Logo image with round container
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0D2040),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.habib_app_icon_1793764478802),
                            contentDescription = "Habib Advance Editor Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Habib Advance Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Professional Document Suite",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = HabibGoldAccent.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "OFFLINE",
                                    color = Color(0xFF946200),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "User Profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("home_user_profile")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Documents Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search your documents...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_input")
            )
        }
    }
}

@Composable
private fun FeatureCardItem(
    feature: QuickFeatureItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clickable { onClick() }
            .testTag("quick_feature_${feature.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = feature.iconColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            tint = feature.iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (feature.badgeLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (feature.isComingSoon) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = feature.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (feature.isComingSoon) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = feature.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentDocumentCard(
    doc: DocumentEntity,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
            .testTag("recent_doc_${doc.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = when (doc.documentType) {
                DocumentType.PDF -> Pair(Icons.Default.PictureAsPdf, DocPdfRed)
                DocumentType.WORD -> Pair(Icons.Default.Description, DocWordBlue)
                DocumentType.IMAGE -> Pair(Icons.Default.Image, DocImageGreen)
                else -> Pair(Icons.Default.Description, MaterialTheme.colorScheme.primary)
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = doc.documentType.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                    if (doc.pageCount > 0) {
                        Text(
                            text = "• ${doc.pageCount} pgs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (doc.sizeBytes > 0) {
                        Text(
                            text = "• ${formatFileSize(doc.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "• ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(doc.lastOpenedTimestamp))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (doc.isFavorite) Color(0xFFD4AF37) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("remove_recent_${doc.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
