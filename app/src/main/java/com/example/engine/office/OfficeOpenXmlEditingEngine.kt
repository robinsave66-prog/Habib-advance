package com.example.engine.office

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** A single XLSX cell value or formula update. */
data class XlsxCellEdit(
    val cellReference: String,
    val value: String = "",
    val formula: String? = null
)

/**
 * Formatting applied to the uniquely matched text run and its containing paragraph.
 * A null style leaves existing formatting untouched.
 */
data class OfficeTextStyle(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val fontSizePt: Float? = null,
    val fontColorHex: String? = null,
    val paragraphAlignment: String? = null
)

/** Native cell-level formatting options for an XLSX worksheet. */
data class XlsxCellFormat(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontSizePt: Float = 11f,
    val fontColorHex: String = "000000",
    val fillColorHex: String? = null,
    val horizontalAlignment: String = "GENERAL",
    val verticalAlignment: String = "CENTER",
    val wrapText: Boolean = false
)

/** Geometry uses Office EMUs (914400 EMUs per inch). */
data class DocxImageGeometry(val x: Long, val y: Long, val width: Long, val height: Long)

data class PptxShapeStyle(val fillColorHex: String? = null, val lineColorHex: String? = null)

/** Geometry uses PowerPoint EMUs (914400 EMUs per inch). */
data class PptxObjectGeometry(
    val x: Long,
    val y: Long,
    val width: Long,
    val height: Long
)

/** Native Open XML package editing for DOCX, XLSX and PPTX. */
class OfficeOpenXmlEditingEngine(private val context: Context) {
    suspend fun replaceDocxText(sourceUri: Uri, oldText: String, newText: String,
        outputFileName: String = "Edited_Document.docx",
        style: OfficeTextStyle? = null): File = withContext(Dispatchers.IO) {
        require(oldText.isNotEmpty()) { "Find text cannot be empty." }
        validateTextStyle(style)
        var matchesAcrossPackage = 0
        editTextPackage(sourceUri, outputFileName, "docx") { name, bytes ->
            if (name.matches(Regex("word/(document|header\\d*|footer\\d*|footnotes|endnotes)\\.xml"))) {
                val xml = parse(bytes)
                if (replaceUnique(xml, WORD_NS, "p", "t", oldText, newText, "DOCX", style)) {
                    matchesAcrossPackage++
                    require(matchesAcrossPackage == 1) { "Text occurs in more than one document part. Use a more specific phrase." }
                    serialize(xml)
                } else bytes
            } else bytes
        }
    }

    suspend fun replacePptxText(sourceUri: Uri, oldText: String, newText: String,
        outputFileName: String = "Edited_Presentation.pptx",
        style: OfficeTextStyle? = null): File = withContext(Dispatchers.IO) {
        require(oldText.isNotEmpty()) { "Find text cannot be empty." }
        validateTextStyle(style)
        var matchesAcrossPackage = 0
        editTextPackage(sourceUri, outputFileName, "pptx") { name, bytes ->
            if (name.matches(Regex("ppt/slides/slide\\d+\\.xml"))) {
                val xml = parse(bytes)
                if (replaceUnique(xml, DRAWING_NS, "p", "t", oldText, newText, "PPTX", style)) {
                    matchesAcrossPackage++
                    require(matchesAcrossPackage == 1) { "Text occurs in more than one slide. Use a more specific phrase." }
                    serialize(xml)
                } else bytes
            } else bytes
        }
    }

    suspend fun setXlsxCellValue(sourceUri: Uri, sheetName: String, cellReference: String,
        value: String, formula: String? = null,
        outputFileName: String = "Edited_Workbook.xlsx"): File =
        setXlsxCellValues(
            sourceUri, sheetName,
            listOf(XlsxCellEdit(cellReference, value, formula)),
            outputFileName
        )

    /**
     * Applies multiple edits to one worksheet in a single package rewrite.
     * Existing cell style attributes are retained. Formula results are intentionally
     * left for Excel/LibreOffice to calculate when the workbook is opened.
     */
    suspend fun setXlsxCellValues(sourceUri: Uri, sheetName: String, edits: List<XlsxCellEdit>,
        outputFileName: String = "Edited_Workbook.xlsx"): File = withContext(Dispatchers.IO) {
        require(sheetName.isNotBlank()) { "Select a worksheet." }
        require(edits.isNotEmpty()) { "Add at least one cell edit." }
        val normalized = edits.map { edit ->
            val match = Regex("^([A-Za-z]{1,3})([1-9][0-9]{0,6})$").matchEntire(edit.cellReference)
                ?: throw IllegalArgumentException("Cell reference must look like A1 or BC24.")
            val column = columnIndex(match.groupValues[1])
            val row = match.groupValues[2].toInt()
            require(column in 1..16_384 && row in 1..1_048_576) {
                "Cell reference is outside the Excel worksheet limits (A1:XFD1048576)."
            }
            require(edit.formula == null || edit.formula.isNotBlank()) { "Formula cannot be empty." }
            edit.copy(cellReference = edit.cellReference.uppercase())
        }
        require(normalized.map { it.cellReference }.distinct().size == normalized.size) {
            "Each cell can be edited only once per batch."
        }

        val source = copySource(sourceUri)
        val output = outputFile(outputFileName, "xlsx")
        try {
            val worksheet = resolveSheet(source, sheetName)
            var worksheetUpdated = false
            rewriteZip(source, output) { name, bytes ->
                when (name) {
                    worksheet -> {
                        val xml = parse(bytes)
                        val sheetData = xml.getElementsByTagNameNS(SHEET_NS, "sheetData").item(0) as? Element
                            ?: throw IllegalArgumentException("Worksheet sheetData is missing.")
                        normalized.forEach { edit -> applyCellEdit(xml, sheetData, edit) }
                        worksheetUpdated = true
                        serialize(xml)
                    }
                    "xl/workbook.xml" -> if (normalized.any { it.formula != null }) {
                        serialize(markWorkbookForRecalculation(parse(bytes)))
                    } else bytes
                    else -> bytes
                }
            }
            require(worksheetUpdated) { "The selected worksheet part is missing from the XLSX package." }
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally { source.delete() }
    }

    /**
     * Applies a new native cell style by creating a workbook style record and assigning
     * its index to the selected cell. Existing styles are not overwritten.
     */
    suspend fun formatXlsxCell(sourceUri: Uri, sheetName: String, cellReference: String,
        format: XlsxCellFormat, outputFileName: String = "Formatted_Workbook.xlsx"): File =
        withContext(Dispatchers.IO) {
            require(format.fontSizePt.isFinite() && format.fontSizePt in 1f..409f) {
                "Excel font size must be between 1 and 409 points."
            }
            require(format.fontColorHex.matches(Regex("^[0-9A-Fa-f]{6}$"))) {
                "Font color must be a six-digit RGB value."
            }
            require(format.fillColorHex == null || format.fillColorHex.matches(Regex("^[0-9A-Fa-f]{6}$"))) {
                "Fill color must be a six-digit RGB value."
            }
            require(format.horizontalAlignment.uppercase() in setOf("GENERAL","LEFT","CENTER","RIGHT","FILL","JUSTIFY","CENTERCONTINUOUS","DISTRIBUTED")) {
                "Unsupported horizontal alignment."
            }
            require(format.verticalAlignment.uppercase() in setOf("TOP","CENTER","BOTTOM","JUSTIFY","DISTRIBUTED")) {
                "Unsupported vertical alignment."
            }
            val normalized = cellReference.uppercase()
            require(Regex("^[A-Z]{1,3}[1-9][0-9]{0,6}$").matches(normalized) &&
                columnIndex(normalized) in 1..16384 &&
                normalized.filter { it.isDigit() }.toInt() in 1..1048576) {
                "Cell reference is outside the Excel worksheet limits."
            }
            val source = copySource(sourceUri)
            val output = outputFile(outputFileName, "xlsx")
            try {
                val worksheet = resolveSheet(source, sheetName)
                var changedSheet = false
                var changedStyles = false
                // ZIP entry order is not guaranteed: determine the new style index before
                // rewriting the worksheet, even when its entry precedes styles.xml.
                val styleIndex = ZipFile(source).use { packageZip ->
                    val stylesEntry = packageZip.getEntry("xl/styles.xml")
                        ?: throw IllegalArgumentException("Workbook styles.xml is missing.")
                    val stylesXml = parse(packageZip.getInputStream(stylesEntry).use { it.readBytes() })
                    val cellXfs = stylesXml.getElementsByTagNameNS(SHEET_NS, "cellXfs").item(0) as? Element
                    val existingStyles = cellXfs?.let { node ->
                        (0 until node.childNodes.length).count { node.childNodes.item(it) is Element }
                    } ?: 0
                    // appendXlsxStyle inserts a base XF when the style table is absent or empty.
                    if (existingStyles == 0) 1 else existingStyles
                }
                rewriteZip(source, output) { name, bytes ->
                    when (name) {
                        "xl/styles.xml" -> {
                            val xml = parse(bytes)
                            val appendedStyleIndex = appendXlsxStyle(xml, format)
                            check(appendedStyleIndex == styleIndex) { "Workbook style table changed during formatting." }
                            changedStyles = true
                            serialize(xml)
                        }
                        worksheet -> {
                            val xml = parse(bytes)
                            val data = xml.getElementsByTagNameNS(SHEET_NS, "sheetData").item(0) as? Element
                                ?: error("Worksheet sheetData is missing.")
                            val row = findOrCreateRow(xml, data, normalized.filter { it.isDigit() }.toInt())
                            val cells = (0 until row.childNodes.length).mapNotNull { row.childNodes.item(it) as? Element }
                                .filter { it.localName == "c" }
                            val cell = cells.firstOrNull { it.getAttribute("r") == normalized }
                                ?: xml.createElementNS(SHEET_NS, "c").also { created ->
                                    val next = cells.firstOrNull { columnIndex(it.getAttribute("r")) > columnIndex(normalized) }
                                    if (next == null) row.appendChild(created) else row.insertBefore(created, next)
                                }
                            cell.setAttribute("r", normalized)
                            cell.setAttribute("s", styleIndex.toString())
                            changedSheet = true
                            serialize(xml)
                        }
                        else -> bytes
                    }
                }
                require(changedStyles && changedSheet) { "Workbook styles or worksheet part is missing." }
                output
            } catch (error: Exception) {
                output.delete()
                throw error
            } finally { source.delete() }
        }

    private fun appendXlsxStyle(xml: Document, format: XlsxCellFormat): Int {
        val ns = SHEET_NS
        fun ensure(name: String): Element {
            return (0 until xml.documentElement.childNodes.length)
                .mapNotNull { xml.documentElement.childNodes.item(it) as? Element }
                .firstOrNull { it.namespaceURI == ns && it.localName == name }
                ?: xml.createElementNS(ns, name).also { xml.documentElement.appendChild(it) }
        }
        fun count(node: Element) {
            node.setAttribute("count", (0 until node.childNodes.length)
                .count { node.childNodes.item(it) is Element }.toString())
        }
        val fonts = ensure("fonts")
        if ((0 until fonts.childNodes.length).none { fonts.childNodes.item(it) is Element }) {
            val defaultFont = xml.createElementNS(ns, "font")
            xml.createElementNS(ns, "sz").also { it.setAttribute("val", "11"); defaultFont.appendChild(it) }
            xml.createElementNS(ns, "color").also { it.setAttribute("theme", "1"); defaultFont.appendChild(it) }
            xml.createElementNS(ns, "name").also { it.setAttribute("val", "Calibri"); defaultFont.appendChild(it) }
            fonts.appendChild(defaultFont); count(fonts)
        }
        val font = xml.createElementNS(ns, "font")
        if (format.bold) font.appendChild(xml.createElementNS(ns, "b"))
        if (format.italic) font.appendChild(xml.createElementNS(ns, "i"))
        if (format.underline) font.appendChild(xml.createElementNS(ns, "u"))
        xml.createElementNS(ns, "sz").also { it.setAttribute("val", format.fontSizePt.toString()); font.appendChild(it) }
        xml.createElementNS(ns, "color").also { it.setAttribute("rgb", "FF" + format.fontColorHex.uppercase()); font.appendChild(it) }
        xml.createElementNS(ns, "name").also { it.setAttribute("val", "Arial"); font.appendChild(it) }
        fonts.appendChild(font); count(fonts)
        val fills = ensure("fills")
        if ((0 until fills.childNodes.length).none { fills.childNodes.item(it) is Element }) {
            listOf("none", "gray125").forEach { patternType ->
                val fill = xml.createElementNS(ns, "fill")
                val pattern = xml.createElementNS(ns, "patternFill")
                pattern.setAttribute("patternType", patternType)
                fill.appendChild(pattern)
                fills.appendChild(fill)
            }
            count(fills)
        }
        val borders = ensure("borders")
        if ((0 until borders.childNodes.length).none { borders.childNodes.item(it) is Element }) {
            borders.appendChild(xml.createElementNS(ns, "border"))
            count(borders)
        }
        val baseStyles = ensure("cellStyleXfs")
        if ((0 until baseStyles.childNodes.length).none { baseStyles.childNodes.item(it) is Element }) {
            val base = xml.createElementNS(ns, "xf")
            base.setAttribute("numFmtId", "0")
            base.setAttribute("fontId", "0")
            base.setAttribute("fillId", "0")
            base.setAttribute("borderId", "0")
            baseStyles.appendChild(base)
            count(baseStyles)
        }
        var fillId = 0
        if (format.fillColorHex != null) {
            val fill = xml.createElementNS(ns, "fill")
            val pattern = xml.createElementNS(ns, "patternFill")
            pattern.setAttribute("patternType", "solid")
            xml.createElementNS(ns, "fgColor").also { it.setAttribute("rgb", "FF" + format.fillColorHex.uppercase()); pattern.appendChild(it) }
            xml.createElementNS(ns, "bgColor").also { it.setAttribute("indexed", "64"); pattern.appendChild(it) }
            fill.appendChild(pattern); fills.appendChild(fill); count(fills)
            fillId = (0 until fills.childNodes.length).count { fills.childNodes.item(it) is Element } - 1
        }
        val cellXfs = ensure("cellXfs")
        if ((0 until cellXfs.childNodes.length).none { cellXfs.childNodes.item(it) is Element }) {
            val defaultXf = xml.createElementNS(ns, "xf")
            defaultXf.setAttribute("numFmtId", "0"); defaultXf.setAttribute("fontId", "0")
            defaultXf.setAttribute("fillId", "0"); defaultXf.setAttribute("borderId", "0")
            cellXfs.appendChild(defaultXf); count(cellXfs)
        }
        val index = (0 until cellXfs.childNodes.length).count { cellXfs.childNodes.item(it) is Element }
        val xf = xml.createElementNS(ns, "xf")
        xf.setAttribute("numFmtId", "0")
        xf.setAttribute("fontId", ((0 until fonts.childNodes.length).count { fonts.childNodes.item(it) is Element } - 1).toString())
        xf.setAttribute("fillId", fillId.toString())
        xf.setAttribute("borderId", "0")
        xf.setAttribute("xfId", "0")
        xf.setAttribute("applyFont", "1")
        if (format.fillColorHex != null) xf.setAttribute("applyFill", "1")
        val alignment = xml.createElementNS(ns, "alignment")
        alignment.setAttribute("horizontal", format.horizontalAlignment.lowercase())
        alignment.setAttribute("vertical", format.verticalAlignment.lowercase())
        if (format.wrapText) alignment.setAttribute("wrapText", "1")
        xf.appendChild(alignment)
        xf.setAttribute("applyAlignment", "1")
        cellXfs.appendChild(xf); count(cellXfs)
        return index
    }

    /** Replaces the contents of a specific DOCX table cell without flattening the table. */
    suspend fun setDocxTableCellText(sourceUri: Uri, tableIndex: Int, rowIndex: Int, cellIndex: Int,
        text: String, outputFileName: String = "Edited_Table.docx"): File = withContext(Dispatchers.IO) {
        require(tableIndex >= 0 && rowIndex >= 0 && cellIndex >= 0) { "Table, row and cell indexes must be zero or greater." }
        editTextPackage(sourceUri, outputFileName, "docx") { name, bytes ->
            if (name != "word/document.xml") bytes else {
                val xml = parse(bytes)
                val tables = xml.getElementsByTagNameNS(WORD_NS, "tbl")
                require(tableIndex < tables.length) { "The selected DOCX table does not exist." }
                val rows = (0 until (tables.item(tableIndex) as Element).getElementsByTagNameNS(WORD_NS, "tr").length)
                    .map { (tables.item(tableIndex) as Element).getElementsByTagNameNS(WORD_NS, "tr").item(it) as Element }
                require(rowIndex < rows.size) { "The selected table row does not exist." }
                val cells = rows[rowIndex].getElementsByTagNameNS(WORD_NS, "tc")
                require(cellIndex < cells.length) { "The selected table cell does not exist." }
                val cell = cells.item(cellIndex) as Element
                val textNodes = cell.getElementsByTagNameNS(WORD_NS, "t")
                if (textNodes.length == 0) {
                    val paragraph = cell.getElementsByTagNameNS(WORD_NS, "p").item(0) as? Element
                        ?: xml.createElementNS(WORD_NS, "w:p").also { cell.appendChild(it) }
                    val run = xml.createElementNS(WORD_NS, "w:r")
                    xml.createElementNS(WORD_NS, "w:t").also { it.textContent = text; run.appendChild(it) }
                    paragraph.appendChild(run)
                } else {
                    textNodes.item(0).textContent = text
                    for (i in 1 until textNodes.length) textNodes.item(i).textContent = ""
                }
                serialize(xml)
            }
        }
    }

    /** Resize a named Word picture; floating (anchored) pictures can also be repositioned. */
    suspend fun setDocxImageGeometry(sourceUri: Uri, imageName: String, geometry: DocxImageGeometry,
        outputFileName: String = "Edited_Word_Layout.docx"): File = withContext(Dispatchers.IO) {
        require(imageName.isNotBlank()) { "Enter the picture name shown in Word's Selection Pane." }
        require(geometry.width > 0 && geometry.height > 0 && geometry.x >= 0 && geometry.y >= 0) {
            "Picture position and dimensions must be valid positive EMU values."
        }
        editTextPackage(sourceUri, outputFileName, "docx") { name, bytes ->
            if (name != "word/document.xml") bytes else {
                val xml = parse(bytes)
                val docPr = xml.getElementsByTagNameNS(WORD_DRAWING_NS, "docPr")
                val target = (0 until docPr.length).map { docPr.item(it) as Element }
                    .firstOrNull { it.getAttribute("name") == imageName }
                    ?: throw IllegalArgumentException("Word picture '$imageName' was not found.")
                var container: Element? = target.parentNode as? Element
                while (container != null && container.localName !in setOf("inline", "anchor")) {
                    container = container.parentNode as? Element
                }
                val picture = container ?: throw IllegalArgumentException("The selected Word picture has no editable layout container.")
                val extent = picture.getElementsByTagNameNS(WORD_DRAWING_NS, "extent").item(0) as? Element
                    ?: throw IllegalArgumentException("The selected Word picture has no size properties.")
                extent.setAttribute("cx", geometry.width.toString())
                extent.setAttribute("cy", geometry.height.toString())
                if (picture.localName == "anchor") {
                    val horizontal = picture.getElementsByTagNameNS(WORD_DRAWING_NS, "positionH").item(0) as? Element
                        ?: throw IllegalArgumentException("The floating picture has no horizontal position.")
                    val vertical = picture.getElementsByTagNameNS(WORD_DRAWING_NS, "positionV").item(0) as? Element
                        ?: throw IllegalArgumentException("The floating picture has no vertical position.")
                    val horizontalOffset = horizontal.getElementsByTagNameNS(WORD_DRAWING_NS, "posOffset").item(0) as? Element
                        ?: throw IllegalArgumentException("The horizontal position is alignment-based and cannot be changed numerically.")
                    val verticalOffset = vertical.getElementsByTagNameNS(WORD_DRAWING_NS, "posOffset").item(0) as? Element
                        ?: throw IllegalArgumentException("The vertical position is alignment-based and cannot be changed numerically.")
                    horizontalOffset.textContent = geometry.x.toString()
                    verticalOffset.textContent = geometry.y.toString()
                } else {
                    require(geometry.x == 0L && geometry.y == 0L) {
                        "This Word picture is inline. Resize it here; change text wrapping to a floating picture in Word before repositioning."
                    }
                }
                serialize(xml)
            }
        }
    }

    /** Applies native text and cell shading formatting to a selected Word table cell. */
    suspend fun formatDocxTableCell(sourceUri: Uri, tableIndex: Int, rowIndex: Int, cellIndex: Int,
        style: OfficeTextStyle, fillColorHex: String? = null,
        outputFileName: String = "Formatted_Word_Table.docx"): File = withContext(Dispatchers.IO) {
        require(tableIndex >= 0 && rowIndex >= 0 && cellIndex >= 0) { "Table, row and cell indexes must be zero or greater." }
        require(fillColorHex == null || fillColorHex.matches(Regex("^[0-9A-Fa-f]{6}$"))) {
            "Table cell fill must be a six-digit RGB value."
        }
        editTextPackage(sourceUri, outputFileName, "docx") { name, bytes ->
            if (name != "word/document.xml") bytes else {
                val xml = parse(bytes)
                val tables = xml.getElementsByTagNameNS(WORD_NS, "tbl")
                require(tableIndex < tables.length) { "The selected DOCX table does not exist." }
                val table = tables.item(tableIndex) as Element
                val rows = table.getElementsByTagNameNS(WORD_NS, "tr")
                require(rowIndex < rows.length) { "The selected table row does not exist." }
                val cells = (rows.item(rowIndex) as Element).getElementsByTagNameNS(WORD_NS, "tc")
                require(cellIndex < cells.length) { "The selected table cell does not exist." }
                val cell = cells.item(cellIndex) as Element
                fun direct(parent: Element, local: String): Element? =
                    (0 until parent.childNodes.length).mapNotNull { parent.childNodes.item(it) as? Element }
                        .firstOrNull { it.namespaceURI == WORD_NS && it.localName == local }
                fun ensure(parent: Element, local: String, first: Boolean = false): Element =
                    direct(parent, local) ?: xml.createElementNS(WORD_NS, "w:$local").also {
                        if (first) parent.insertBefore(it, parent.firstChild) else parent.appendChild(it)
                    }
                if (fillColorHex != null) {
                    val tcPr = ensure(cell, "tcPr", first = true)
                    val shading = ensure(tcPr, "shd")
                    shading.setAttributeNS(WORD_NS, "w:val", "clear")
                    shading.setAttributeNS(WORD_NS, "w:color", "auto")
                    shading.setAttributeNS(WORD_NS, "w:fill", fillColorHex.uppercase())
                }
                val paragraphs = cell.getElementsByTagNameNS(WORD_NS, "p")
                for (p in 0 until paragraphs.length) {
                    val paragraph = paragraphs.item(p) as Element
                    if (style.paragraphAlignment != null) {
                        val pPr = ensure(paragraph, "pPr", first = true)
                        val jc = ensure(pPr, "jc")
                        jc.setAttributeNS(WORD_NS, "w:val", style.paragraphAlignment.lowercase())
                    }
                    val runs = paragraph.getElementsByTagNameNS(WORD_NS, "r")
                    for (r in 0 until runs.length) {
                        val run = runs.item(r) as Element
                        val rPr = ensure(run, "rPr", first = true)
                        fun toggle(local: String, enabled: Boolean) {
                            val existing = direct(rPr, local)
                            if (enabled && existing == null) rPr.appendChild(xml.createElementNS(WORD_NS, "w:$local"))
                            if (!enabled && existing != null) rPr.removeChild(existing)
                        }
                        toggle("b", style.bold); toggle("i", style.italic); toggle("u", style.underline)
                        style.fontSizePt?.takeIf { it.isFinite() && it in 1f..409f }?.let { size ->
                            val sz = ensure(rPr, "sz")
                            sz.setAttributeNS(WORD_NS, "w:val", (size * 2f).toInt().toString())
                        }
                        style.fontColorHex?.let { color ->
                            require(color.matches(Regex("^[0-9A-Fa-f]{6}$"))) { "Text color must be a six-digit RGB value." }
                            val c = ensure(rPr, "color")
                            c.setAttributeNS(WORD_NS, "w:val", color.uppercase())
                        }
                    }
                }
                serialize(xml)
            }
        }
    }

    /** Sets a worksheet row's native height in points. */
    suspend fun setXlsxRowHeight(sourceUri: Uri, sheetName: String, rowNumber: Int, heightPt: Float,
        outputFileName: String = "Resized_Rows.xlsx"): File = withContext(Dispatchers.IO) {
        require(rowNumber in 1..1048576 && heightPt.isFinite() && heightPt in 1f..409f) {
            "Excel row number or height is outside supported limits."
        }
        val source = copySource(sourceUri); val output = outputFile(outputFileName, "xlsx")
        try {
            val worksheet = resolveSheet(source, sheetName)
            rewriteZip(source, output) { name, bytes ->
                if (name != worksheet) bytes else {
                    val xml = parse(bytes)
                    val data = xml.getElementsByTagNameNS(SHEET_NS, "sheetData").item(0) as? Element
                        ?: error("Worksheet sheetData is missing.")
                    val row = findOrCreateRow(xml, data, rowNumber)
                    row.setAttribute("ht", heightPt.toString())
                    row.setAttribute("customHeight", "1")
                    serialize(xml)
                }
            }
            output
        } catch (error: Exception) { output.delete(); throw error }
        finally { source.delete() }
    }

    /** Sets a worksheet column's native width in Excel character units. */
    suspend fun setXlsxColumnWidth(sourceUri: Uri, sheetName: String, column: String, width: Float,
        outputFileName: String = "Resized_Columns.xlsx"): File = withContext(Dispatchers.IO) {
        val normalized = column.uppercase()
        require(Regex("^[A-Z]{1,3}$").matches(normalized) && columnIndex(normalized) in 1..16384 &&
            width.isFinite() && width in 0.1f..255f) { "Excel column or width is outside supported limits." }
        val source = copySource(sourceUri); val output = outputFile(outputFileName, "xlsx")
        try {
            val worksheet = resolveSheet(source, sheetName)
            val targetColumn = columnIndex(normalized)
            rewriteZip(source, output) { name, bytes ->
                if (name != worksheet) bytes else {
                    val xml = parse(bytes)
                    val root = xml.documentElement
                    var cols = xml.getElementsByTagNameNS(SHEET_NS, "cols").item(0) as? Element
                    if (cols == null) {
                        cols = xml.createElementNS(SHEET_NS, "cols")
                        val sheetData = xml.getElementsByTagNameNS(SHEET_NS, "sheetData").item(0)
                            ?: error("Worksheet sheetData is missing.")
                        root.insertBefore(cols, sheetData)
                    }
                    val existing = (0 until cols.childNodes.length).mapNotNull { cols.childNodes.item(it) as? Element }
                        .firstOrNull { it.localName == "col" &&
                            targetColumn >= (it.getAttribute("min").toIntOrNull() ?: Int.MAX_VALUE) &&
                            targetColumn <= (it.getAttribute("max").toIntOrNull() ?: Int.MIN_VALUE) }
                    val col = existing ?: xml.createElementNS(SHEET_NS, "col").also { created ->
                        created.setAttribute("min", targetColumn.toString())
                        created.setAttribute("max", targetColumn.toString())
                        val next = (0 until cols.childNodes.length).mapNotNull { cols.childNodes.item(it) as? Element }
                            .firstOrNull { it.localName == "col" && (it.getAttribute("min").toIntOrNull() ?: Int.MAX_VALUE) > targetColumn }
                        if (next == null) cols.appendChild(created) else cols.insertBefore(created, next)
                    }
                    col.setAttribute("width", width.toString())
                    col.setAttribute("customWidth", "1")
                    serialize(xml)
                }
            }
            output
        } catch (error: Exception) { output.delete(); throw error }
        finally { source.delete() }
    }

    /** Applies fill and outline colors to a named PowerPoint shape without flattening it. */
    suspend fun setPptxShapeStyle(sourceUri: Uri, slideNumber: Int, objectName: String,
        style: PptxShapeStyle, outputFileName: String = "Formatted_Slides.pptx"): File =
        withContext(Dispatchers.IO) {
            require(slideNumber >= 1 && objectName.isNotBlank()) { "Select a valid slide and named shape." }
            require(style.fillColorHex == null || style.fillColorHex.matches(Regex("^[0-9A-Fa-f]{6}$"))) {
                "Shape fill must be a six-digit RGB value."
            }
            require(style.lineColorHex == null || style.lineColorHex.matches(Regex("^[0-9A-Fa-f]{6}$"))) {
                "Shape outline must be a six-digit RGB value."
            }
            require(style.fillColorHex != null || style.lineColorHex != null) { "Choose a fill or outline color." }
            editTextPackage(sourceUri, outputFileName, "pptx") { name, bytes ->
                if (name != "ppt/slides/slide$slideNumber.xml") bytes else {
                    val xml = parse(bytes)
                    val names = xml.getElementsByTagNameNS(PRESENTATION_NS, "cNvPr")
                    val target = (0 until names.length).map { names.item(it) as Element }
                        .firstOrNull { it.getAttribute("name") == objectName }
                        ?: throw IllegalArgumentException("PowerPoint shape '$objectName' was not found.")
                    var shape: Element? = target.parentNode as? Element
                    while (shape != null && !(shape.localName == "sp" && shape.namespaceURI == PRESENTATION_NS)) {
                        shape = shape.parentNode as? Element
                    }
                    val shapeProperties = shape?.getElementsByTagNameNS(DRAWING_NS, "spPr")?.item(0) as? Element
                        ?: throw IllegalArgumentException("Selected object is not an editable PowerPoint shape.")
                    fun addSolidFill(parent: Element, color: String) {
                        val fillNames = setOf("solidFill", "gradFill", "blipFill", "pattFill", "noFill", "grpFill")
                        val old = (0 until parent.childNodes.length).mapNotNull { parent.childNodes.item(it) as? Element }
                            .filter { it.namespaceURI == DRAWING_NS && it.localName in fillNames }
                        old.forEach { parent.removeChild(it) }
                        val solid = xml.createElementNS(DRAWING_NS, "a:solidFill")
                        xml.createElementNS(DRAWING_NS, "a:srgbClr").also {
                            it.setAttribute("val", color.uppercase()); solid.appendChild(it)
                        }
                        val line = (0 until parent.childNodes.length).mapNotNull { parent.childNodes.item(it) as? Element }
                            .firstOrNull { it.namespaceURI == DRAWING_NS && it.localName == "ln" }
                        if (line != null) parent.insertBefore(solid, line) else parent.appendChild(solid)
                    }
                    style.fillColorHex?.let { addSolidFill(shapeProperties, it) }
                    style.lineColorHex?.let { color ->
                        val line = (0 until shapeProperties.childNodes.length).mapNotNull { shapeProperties.childNodes.item(it) as? Element }
                            .firstOrNull { it.namespaceURI == DRAWING_NS && it.localName == "ln" }
                            ?: xml.createElementNS(DRAWING_NS, "a:ln").also { shapeProperties.appendChild(it) }
                        addSolidFill(line, color)
                    }
                    serialize(xml)
                }
            }
        }

    /** Moves/resizes a named PowerPoint shape or picture while retaining its content and effects. */
    suspend fun setPptxObjectGeometry(sourceUri: Uri, slideNumber: Int, objectName: String,
        geometry: PptxObjectGeometry, outputFileName: String = "Edited_Layout.pptx"): File =
        withContext(Dispatchers.IO) {
            require(slideNumber >= 1 && objectName.isNotBlank()) { "Select a valid slide and named object." }
            require(geometry.x >= 0 && geometry.y >= 0 && geometry.width > 0 && geometry.height > 0) {
                "Object position must be non-negative and size must be greater than zero."
            }
            editTextPackage(sourceUri, outputFileName, "pptx") { name, bytes ->
                if (name != "ppt/slides/slide$slideNumber.xml") bytes else {
                    val xml = parse(bytes)
                    val names = xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "cNvPr")
                    val target = (0 until names.length).map { names.item(it) as Element }
                        .firstOrNull { it.getAttribute("name") == objectName }
                        ?: throw IllegalArgumentException("PowerPoint object '$objectName' was not found on slide $slideNumber.")
                    var ancestor: org.w3c.dom.Node? = target.parentNode
                    var transform: Element? = null
                    while (ancestor != null && transform == null) {
                        if (ancestor is Element) {
                            transform = (ancestor as Element).getElementsByTagNameNS(DRAWING_NS, "xfrm")
                                .item(0) as? Element
                        }
                        ancestor = ancestor.parentNode
                    }
                    val xfrm = transform ?: throw IllegalArgumentException("Selected object has no editable transform.")
                    val off = (0 until xfrm.childNodes.length).mapNotNull { xfrm.childNodes.item(it) as? Element }
                        .firstOrNull { it.localName == "off" } ?: throw IllegalArgumentException("Object position is missing.")
                    val ext = (0 until xfrm.childNodes.length).mapNotNull { xfrm.childNodes.item(it) as? Element }
                        .firstOrNull { it.localName == "ext" } ?: throw IllegalArgumentException("Object size is missing.")
                    off.setAttribute("x", geometry.x.toString()); off.setAttribute("y", geometry.y.toString())
                    ext.setAttribute("cx", geometry.width.toString()); ext.setAttribute("cy", geometry.height.toString())
                    serialize(xml)
                }
            }
        }

    /** Changes the physical slide canvas size while keeping all existing objects intact. */
    suspend fun setPptxSlideSize(sourceUri: Uri, widthEmu: Long, heightEmu: Long,
        outputFileName: String = "Resized_Slides.pptx"): File = withContext(Dispatchers.IO) {
        require(widthEmu > 0 && heightEmu > 0) { "Slide dimensions must be greater than zero." }
        editTextPackage(sourceUri, outputFileName, "pptx") { name, bytes ->
            if (name != "ppt/presentation.xml") bytes else {
                val xml = parse(bytes)
                val size = xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "sldSz").item(0) as? Element
                    ?: throw IllegalArgumentException("Presentation slide size is missing.")
                size.setAttribute("cx", widthEmu.toString()); size.setAttribute("cy", heightEmu.toString())
                serialize(xml)
            }
        }
    }

    /**
     * Formula results are not evaluated by this editor. Ask compatible spreadsheet
     * applications to perform a full automatic recalculation when the workbook opens.
     */
    private fun markWorkbookForRecalculation(xml: Document): Document {
        val calculation = xml.getElementsByTagNameNS(SHEET_NS, "calcPr").item(0) as? Element
            ?: xml.createElementNS(SHEET_NS, "calcPr").also { created ->
                val workbook = xml.documentElement
                val extensionList = workbook.getElementsByTagNameNS(SHEET_NS, "extLst").item(0)
                if (extensionList != null && extensionList.parentNode === workbook) {
                    workbook.insertBefore(created, extensionList)
                } else {
                    workbook.appendChild(created)
                }
            }
        calculation.setAttribute("calcMode", "auto")
        calculation.setAttribute("fullCalcOnLoad", "1")
        calculation.setAttribute("forceFullCalc", "1")
        return xml
    }

    private fun applyCellEdit(xml: Document, sheetData: Element, edit: XlsxCellEdit) {
        val reference = edit.cellReference
        val rowNumber = reference.filter { it.isDigit() }.toInt()
        val row = findOrCreateRow(xml, sheetData, rowNumber)
        val cells = (0 until row.childNodes.length).mapNotNull {
            row.childNodes.item(it) as? Element
        }.filter { it.localName == "c" }
        val cell = cells.firstOrNull { it.getAttribute("r").equals(reference, true) }
            ?: xml.createElementNS(SHEET_NS, "c").also { newCell ->
                val targetColumn = columnIndex(reference)
                val nextCell = cells.firstOrNull { columnIndex(it.getAttribute("r")) > targetColumn }
                if (nextCell != null) row.insertBefore(newCell, nextCell) else row.appendChild(newCell)
            }

        // Do not remove cell attributes such as style index, metadata or protection.
        cell.setAttribute("r", reference)
        while (cell.hasChildNodes()) cell.removeChild(cell.firstChild)
        if (edit.formula != null) {
            cell.removeAttribute("t")
            xml.createElementNS(SHEET_NS, "f").also {
                it.textContent = edit.formula.removePrefix("=")
                cell.appendChild(it)
            }
            // Empty cached result: spreadsheet applications recalculate on open.
            xml.createElementNS(SHEET_NS, "v").also { cell.appendChild(it) }
        } else if (edit.value.isNotBlank() && edit.value.toDoubleOrNull() != null) {
            cell.removeAttribute("t")
            xml.createElementNS(SHEET_NS, "v").also {
                it.textContent = edit.value
                cell.appendChild(it)
            }
        } else {
            cell.setAttribute("t", "inlineStr")
            val inline = xml.createElementNS(SHEET_NS, "is")
            val text = xml.createElementNS(SHEET_NS, "t")
            if (edit.value.startsWith(" ") || edit.value.endsWith(" ")) {
                text.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve")
            }
            text.textContent = edit.value
            inline.appendChild(text)
            cell.appendChild(inline)
        }
    }
    private suspend fun editTextPackage(uri: Uri, name: String, extension: String,
        transform: (String, ByteArray) -> ByteArray): File {
        val source = copySource(uri)
        val output = outputFile(name, extension)
        try {
            var modified = false
            rewriteZip(source, output) { entry, bytes ->
                val changed = transform(entry, bytes)
                if (!changed.contentEquals(bytes)) modified = true
                changed
            }
            require(modified) { "No editable text was found in the document." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally { source.delete() }
    }

    private fun copySource(uri: Uri): File {
        val file = File(context.cacheDir, "office-source-" + System.nanoTime() + ".zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Cannot open source Office document.")
        require(file.length() > 0L) { "Selected Office document is empty." }
        return file
    }

    private fun outputFile(name: String, extension: String): File {
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val base = name.substringBeforeLast('.', name).trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "Edited_Document" }
        var candidate = File(directory, base + "." + extension)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, base + "(" + suffix + ")." + extension)
            suffix++
        }
        return candidate
    }

    private fun rewriteZip(source: File, output: File, transform: (String, ByteArray) -> ByteArray) {
        ZipFile(source).use { input ->
            ZipOutputStream(FileOutputStream(output)).use { target ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    val next = ZipEntry(entry.name).apply { time = entry.time; comment = entry.comment }
                    target.putNextEntry(next)
                    target.write(transform(entry.name, bytes))
                    target.closeEntry()
                }
            }
        }
    }

    private fun resolveSheet(file: File, name: String): String {
        ZipFile(file).use { zip ->
            val workbook = parse(zip.getInputStream(zip.getEntry("xl/workbook.xml")
                ?: error("XLSX workbook.xml is missing.")).use { it.readBytes() })
            val rels = parse(zip.getInputStream(zip.getEntry("xl/_rels/workbook.xml.rels")
                ?: error("XLSX workbook relationships are missing.")).use { it.readBytes() })
            val nodes = rels.getElementsByTagNameNS(PKG_REL_NS, "Relationship")
            val sheets = workbook.getElementsByTagNameNS(SHEET_NS, "sheet")
            for (i in 0 until sheets.length) {
                val sheet = sheets.item(i) as Element
                if (sheet.getAttribute("name") == name) {
                    val relationshipId = sheet.getAttributeNS(OFFICE_REL_NS, "id")
                    val relationship = (0 until nodes.length).map { nodes.item(it) as Element }
                        .firstOrNull { it.getAttribute("Id") == relationshipId }
                        ?: error("Worksheet relationship is missing.")
                    require(relationship.getAttribute("TargetMode") != "External") {
                        "External worksheet relationships are not supported."
                    }
                    val target = relationship.getAttribute("Target")
                    val resolved = java.net.URI("xl/").resolve(target).normalize().path.removePrefix("/")
                    require(resolved.startsWith("xl/") && !resolved.split("/").contains("..")) {
                        "Worksheet relationship points outside the XLSX package."
                    }
                    return resolved
                }
            }
            throw IllegalArgumentException("Worksheet '" + name + "' was not found.")
        }
    }

    private fun findOrCreateRow(xml: Document, data: Element, number: Int): Element {
        for (i in 0 until data.childNodes.length) {
            val row = data.childNodes.item(i) as? Element ?: continue
            if (row.localName == "row" && row.getAttribute("r").toIntOrNull() == number) return row
        }
        val newRow = xml.createElementNS(SHEET_NS, "row").also { it.setAttribute("r", number.toString()) }
        val nextRow = (0 until data.childNodes.length).mapNotNull { data.childNodes.item(it) as? Element }
            .firstOrNull { it.localName == "row" && (it.getAttribute("r").toIntOrNull() ?: Int.MAX_VALUE) > number }
        if (nextRow != null) data.insertBefore(newRow, nextRow) else data.appendChild(newRow)
        return newRow
    }

    private fun columnIndex(reference: String): Int {
        var result = 0
        reference.takeWhile { it.isLetter() }.uppercase().forEach { result = result * 26 + (it - 'A' + 1) }
        return result
    }

    /**
     * Replaces a phrase across text runs inside one paragraph. Open XML producers commonly
     * split a visible phrase across several runs (for example after applying bold to a word).
     * The replacement inherits the first affected run's formatting; other run properties and
     * paragraph structure are retained.
     */
    private fun replaceUnique(xml: Document, namespace: String, paragraphTag: String, textTag: String,
        old: String, replacement: String, format: String, style: OfficeTextStyle? = null): Boolean {
        val paragraphs = xml.getElementsByTagNameNS(namespace, paragraphTag)
        val matches = mutableListOf<List<org.w3c.dom.Node>>()
        val offsets = mutableListOf<Int>()
        var totalMatches = 0

        for (paragraphIndex in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(paragraphIndex)
            val textNodes = paragraph.childTextNodes(namespace, textTag, paragraphTag)
            if (textNodes.isEmpty()) continue
            val combined = textNodes.joinToString("") { it.textContent.orEmpty() }
            val count = occurrences(combined, old)
            if (count > 0) {
                totalMatches += count
                matches.add(textNodes)
                offsets.add(combined.indexOf(old))
            }
        }

        require(totalMatches <= 1) {
            "Text occurs more than once in this " + format + " part. Select a more specific phrase."
        }
        if (totalMatches == 0) return false

        val nodes = matches.single()
        val start = offsets.single()
        val end = start + old.length
        var cursor = 0
        var startNode = -1
        var endNode = -1
        var startOffset = 0
        var endOffset = 0

        nodes.forEachIndexed { index, node ->
            val length = node.textContent.orEmpty().length
            if (startNode < 0 && start >= cursor && start < cursor + length) {
                startNode = index
                startOffset = start - cursor
            }
            if (endNode < 0 && end > cursor && end <= cursor + length) {
                endNode = index
                endOffset = end - cursor
            }
            cursor += length
        }
        require(startNode >= 0 && endNode >= startNode) {
            "Unable to map the selected phrase to editable text runs."
        }

        if (startNode == endNode) {
            val text = nodes[startNode].textContent.orEmpty()
            nodes[startNode].textContent = text.substring(0, startOffset) + replacement + text.substring(endOffset)
            preserveSpaceIfNeeded(nodes[startNode] as? Element, nodes[startNode].textContent.orEmpty())
        } else {
            val firstText = nodes[startNode].textContent.orEmpty()
            nodes[startNode].textContent = firstText.substring(0, startOffset) + replacement
            preserveSpaceIfNeeded(nodes[startNode] as? Element, nodes[startNode].textContent.orEmpty())
            for (index in startNode + 1 until endNode) nodes[index].textContent = ""
            val lastText = nodes[endNode].textContent.orEmpty()
            nodes[endNode].textContent = lastText.substring(endOffset)
            preserveSpaceIfNeeded(nodes[endNode] as? Element, nodes[endNode].textContent.orEmpty())
        }
        if (style != null) {
            val run = nodes[startNode].parentNode as? Element
            if (run != null) {
                applyRunStyle(run, namespace, format, style)
                var ancestor = run.parentNode
                while (ancestor != null && !(ancestor is Element && ancestor.namespaceURI == namespace &&
                        ancestor.localName == paragraphTag)) {
                    ancestor = ancestor.parentNode
                }
                (ancestor as? Element)?.let { applyParagraphStyle(it, namespace, format, style) }
            }
        }
        return true
    }

    private fun validateTextStyle(style: OfficeTextStyle?) {
        if (style == null) return
        require(style.fontSizePt == null || (style.fontSizePt.isFinite() && style.fontSizePt in 1f..200f)) {
            "Font size must be between 1 and 200 points."
        }
        require(style.fontColorHex == null || style.fontColorHex.matches(Regex("^[0-9A-Fa-f]{6}$"))) {
            "Font color must be a six-digit RGB hex value without #."
        }
        require(style.paragraphAlignment == null ||
            style.paragraphAlignment.uppercase() in setOf("LEFT", "CENTER", "RIGHT", "JUSTIFY")) {
            "Paragraph alignment must be Left, Center, Right or Justify."
        }
    }

    private fun applyRunStyle(run: Element, namespace: String, format: String, style: OfficeTextStyle) {
        val docx = format == "DOCX"
        val prefix = if (docx) "w:" else "a:"
        val propertiesName = if (docx) "rPr" else "rPr"
        val properties = (0 until run.childNodes.length)
            .mapNotNull { run.childNodes.item(it) as? Element }
            .firstOrNull { it.namespaceURI == namespace && it.localName == propertiesName }
            ?: run.ownerDocument.createElementNS(namespace, prefix + propertiesName).also {
                run.insertBefore(it, run.firstChild)
            }

        if (docx) {
            fun setWordToggle(localName: String, enabled: Boolean) {
                val node = (0 until properties.childNodes.length)
                    .mapNotNull { properties.childNodes.item(it) as? Element }
                    .firstOrNull { it.namespaceURI == namespace && it.localName == localName }
                    ?: run.ownerDocument.createElementNS(namespace, prefix + localName).also { properties.appendChild(it) }
                node.setAttributeNS(namespace, prefix + "val", if (enabled) "1" else "0")
            }
            setWordToggle("b", style.bold)
            setWordToggle("i", style.italic)
            val underline = (0 until properties.childNodes.length)
                .mapNotNull { properties.childNodes.item(it) as? Element }
                .firstOrNull { it.namespaceURI == namespace && it.localName == "u" }
                ?: run.ownerDocument.createElementNS(namespace, prefix + "u").also { properties.appendChild(it) }
            underline.setAttributeNS(namespace, prefix + "val", if (style.underline) "single" else "none")
            style.fontSizePt?.let { size ->
                val node = (0 until properties.childNodes.length).mapNotNull { properties.childNodes.item(it) as? Element }
                    .firstOrNull { it.namespaceURI == namespace && it.localName == "sz" }
                    ?: run.ownerDocument.createElementNS(namespace, prefix + "sz").also { properties.appendChild(it) }
                node.setAttributeNS(namespace, prefix + "val", (size * 2f).toInt().toString())
            }
            style.fontColorHex?.let { color ->
                val node = (0 until properties.childNodes.length).mapNotNull { properties.childNodes.item(it) as? Element }
                    .firstOrNull { it.namespaceURI == namespace && it.localName == "color" }
                    ?: run.ownerDocument.createElementNS(namespace, prefix + "color").also { properties.appendChild(it) }
                node.setAttributeNS(namespace, prefix + "val", color.uppercase())
            }
        } else {
            properties.setAttribute("b", if (style.bold) "1" else "0")
            properties.setAttribute("i", if (style.italic) "1" else "0")
            properties.setAttribute("u", if (style.underline) "sng" else "none")
            style.fontSizePt?.let { properties.setAttribute("sz", (it * 100f).toInt().toString()) }
            style.fontColorHex?.let { color ->
                val existing = (0 until properties.childNodes.length)
                    .mapNotNull { properties.childNodes.item(it) as? Element }
                    .firstOrNull { it.namespaceURI == namespace && it.localName == "solidFill" }
                existing?.let { properties.removeChild(it) }
                val fill = run.ownerDocument.createElementNS(namespace, prefix + "solidFill")
                val rgb = run.ownerDocument.createElementNS(namespace, prefix + "srgbClr")
                rgb.setAttribute("val", color.uppercase())
                fill.appendChild(rgb)
                properties.appendChild(fill)
            }
        }
    }

    private fun applyParagraphStyle(paragraph: Element, namespace: String, format: String, style: OfficeTextStyle) {
        val alignment = style.paragraphAlignment?.uppercase() ?: return
        val docx = format == "DOCX"
        val prefix = if (docx) "w:" else "a:"
        val propertiesName = if (docx) "pPr" else "pPr"
        val properties = (0 until paragraph.childNodes.length)
            .mapNotNull { paragraph.childNodes.item(it) as? Element }
            .firstOrNull { it.namespaceURI == namespace && it.localName == propertiesName }
            ?: paragraph.ownerDocument.createElementNS(namespace, prefix + propertiesName).also {
                paragraph.insertBefore(it, paragraph.firstChild)
            }
        val value = when (alignment) {
            "CENTER" -> if (docx) "center" else "ctr"
            "RIGHT" -> if (docx) "right" else "r"
            "JUSTIFY" -> if (docx) "both" else "just"
            else -> if (docx) "left" else "l"
        }
        if (docx) {
            val jc = (0 until properties.childNodes.length).mapNotNull { properties.childNodes.item(it) as? Element }
                .firstOrNull { it.namespaceURI == namespace && it.localName == "jc" }
                ?: paragraph.ownerDocument.createElementNS(namespace, prefix + "jc").also { properties.appendChild(it) }
            jc.setAttributeNS(namespace, prefix + "val", value)
        } else {
            properties.setAttribute("algn", value)
        }
    }

    private fun org.w3c.dom.Node.childTextNodes(
        namespace: String,
        textTag: String,
        paragraphTag: String
    ): List<org.w3c.dom.Node> {
        val paragraph = this
        val found = (this as? Element)?.getElementsByTagNameNS(namespace, textTag) ?: return emptyList()
        return (0 until found.length).map { found.item(it) }.filter { textNode ->
            // Do not join text across a nested paragraph boundary (for example a text box).
            var ancestor = textNode.parentNode
            var nestedParagraph = false
            while (ancestor != null && ancestor !== paragraph) {
                if (ancestor is Element &&
                    ancestor.namespaceURI == namespace &&
                    ancestor.localName == paragraphTag
                ) {
                    nestedParagraph = true
                    break
                }
                ancestor = ancestor.parentNode
            }
            !nestedParagraph
        }
    }

    private fun preserveSpaceIfNeeded(element: Element?, value: String) {
        if (element == null) return
        if (value.startsWith(" ") || value.endsWith(" ")) {
            element.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve")
        }
    }

    private fun occurrences(text: String, needle: String): Int {
        var count = 0
        var start = 0
        while (true) {
            val at = text.indexOf(needle, start)
            if (at < 0) return count
            count++
            start = at + needle.length
        }
    }

    private fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(bytes.inputStream())
    }

    private fun serialize(xml: Document): ByteArray {
        val factory = TransformerFactory.newInstance().apply {
        }
        val transformer = factory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        return java.io.ByteArrayOutputStream().use { output ->
            transformer.transform(DOMSource(xml), StreamResult(output))
            output.toByteArray()
        }
    }

    companion object {
        private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        private const val WORD_DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
        private const val PRESENTATION_NS = "http://schemas.openxmlformats.org/presentationml/2006/main"
        private const val DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
        private const val OFFICE_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    }
}
