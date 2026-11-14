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
        private const val DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
        private const val OFFICE_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    }
}
