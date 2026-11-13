package com.example.engine.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Model objects for Future Phase 2 Advanced PDF Editor.
 * All coordinates are defined in PDF Page Points (72 DPI) rather than display pixels.
 */
sealed class EditorElement(
    open val id: String = UUID.randomUUID().toString(),
    open val pageIndex: Int,
    open val x: Float, // PDF points from left
    open val y: Float, // PDF points from top
    open val width: Float,
    open val height: Float,
    open val rotationDegrees: Float = 0f,
    open val zIndex: Int = 0,
    open val isLocked: Boolean = false,
    open val opacity: Float = 1.0f
) {
    abstract fun copyWithPosition(x: Float, y: Float): EditorElement
    abstract fun copyWithSize(width: Float, height: Float): EditorElement
    abstract fun copyWithRotation(rotationDegrees: Float): EditorElement
}

data class TextElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageIndex: Int,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDegrees: Float = 0f,
    override val zIndex: Int = 0,
    override val isLocked: Boolean = false,
    override val opacity: Float = 1.0f,
    val text: String,
    val fontSizePt: Float = 14f,
    val fontName: String = "Helvetica",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColor: Long = 0xFF000000,
    val backgroundColor: Long? = null,
    val textAlign: String = "LEFT"
) : EditorElement(id, pageIndex, x, y, width, height, rotationDegrees, zIndex, isLocked, opacity) {
    override fun copyWithPosition(x: Float, y: Float) = copy(x = x, y = y)
    override fun copyWithSize(width: Float, height: Float) = copy(width = width, height = height)
    override fun copyWithRotation(rotationDegrees: Float) = copy(rotationDegrees = rotationDegrees)
}

data class ImageElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageIndex: Int,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDegrees: Float = 0f,
    override val zIndex: Int = 0,
    override val isLocked: Boolean = false,
    override val opacity: Float = 1.0f,
    val imageUri: String,
    val maintainAspectRatio: Boolean = true
) : EditorElement(id, pageIndex, x, y, width, height, rotationDegrees, zIndex, isLocked, opacity) {
    override fun copyWithPosition(x: Float, y: Float) = copy(x = x, y = y)
    override fun copyWithSize(width: Float, height: Float) = copy(width = width, height = height)
    override fun copyWithRotation(rotationDegrees: Float) = copy(rotationDegrees = rotationDegrees)
}

enum class ShapeType { RECTANGLE, CIRCLE, LINE, ARROW, HIGHLIGHT_BOX }

data class ShapeElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageIndex: Int,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDegrees: Float = 0f,
    override val zIndex: Int = 0,
    override val isLocked: Boolean = false,
    override val opacity: Float = 1.0f,
    val shapeType: ShapeType,
    val strokeColor: Long = 0xFF000000,
    val fillColor: Long? = null,
    val strokeWidthPt: Float = 2f
) : EditorElement(id, pageIndex, x, y, width, height, rotationDegrees, zIndex, isLocked, opacity) {
    override fun copyWithPosition(x: Float, y: Float) = copy(x = x, y = y)
    override fun copyWithSize(width: Float, height: Float) = copy(width = width, height = height)
    override fun copyWithRotation(rotationDegrees: Float) = copy(rotationDegrees = rotationDegrees)
}

data class SignatureElement(
    override val id: String = UUID.randomUUID().toString(),
    override val pageIndex: Int,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    override val rotationDegrees: Float = 0f,
    override val zIndex: Int = 0,
    override val isLocked: Boolean = false,
    override val opacity: Float = 1.0f,
    val pathPoints: List<Pair<Float, Float>>,
    val inkColor: Long = 0xFF0F3B66,
    val inkWidthPt: Float = 2.5f
) : EditorElement(id, pageIndex, x, y, width, height, rotationDegrees, zIndex, isLocked, opacity) {
    override fun copyWithPosition(x: Float, y: Float) = copy(x = x, y = y)
    override fun copyWithSize(width: Float, height: Float) = copy(width = width, height = height)
    override fun copyWithRotation(rotationDegrees: Float) = copy(rotationDegrees = rotationDegrees)
}

data class EditorState(
    val currentPageIndex: Int = 0,
    val elements: List<EditorElement> = emptyList(),
    val selectedElementIds: Set<String> = emptySet(),
    val undoStack: List<List<EditorElement>> = emptyList(),
    val redoStack: List<List<EditorElement>> = emptyList(),
    val clipboard: List<EditorElement> = emptyList(),
    val zoomLevel: Float = 1.0f
)

interface IPdfEditorEngine {
    fun addElement(element: EditorElement)
    fun removeElement(elementId: String)
    fun updateElement(element: EditorElement)
    fun selectElement(elementId: String, addToSelection: Boolean = false)
    fun clearSelection()
    fun moveSelected(deltaXPt: Float, deltaYPt: Float)
    fun undo(): Boolean
    fun redo(): Boolean
    fun copy()
    fun paste()
    fun bringToFront(elementId: String)
    fun sendToBack(elementId: String)
    suspend fun savePdfWithEdits(outputPath: String): Boolean
}
