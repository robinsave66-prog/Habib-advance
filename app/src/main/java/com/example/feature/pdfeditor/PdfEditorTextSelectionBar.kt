package com.example.feature.pdfeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PdfEditorTextSelectionBar(
    selectedWordCount: Int,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onChangeColor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
        shadowElevation = 10.dp,
        tonalElevation = 6.dp,
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .testTag("pdf_text_selection_toolbar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$selectedWordCount word${if (selectedWordCount > 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ToolbarActionItem(icon = Icons.Default.ContentCopy, title = "Copy", onClick = onCopy, testTag = "text_action_copy")
            ToolbarActionItem(icon = Icons.Default.ContentCut, title = "Cut", onClick = onCut, testTag = "text_action_cut")
            ToolbarActionItem(icon = Icons.Default.Edit, title = "Edit", onClick = onEdit, testTag = "text_action_edit")
            ToolbarActionItem(icon = Icons.Default.ContentPaste, title = "Paste", onClick = onPaste, testTag = "text_action_paste")
            ToolbarActionItem(icon = Icons.Default.FormatColorText, title = "Color", onClick = onChangeColor, testTag = "text_action_color")
            ToolbarActionItem(icon = Icons.Default.SelectAll, title = "All", onClick = onSelectAll, testTag = "text_action_all")
            ToolbarActionItem(icon = Icons.Default.Delete, title = "Delete", tint = MaterialTheme.colorScheme.error, onClick = onDelete, testTag = "text_action_delete")
        }
    }
}

@Composable
private fun ToolbarActionItem(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    testTag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
