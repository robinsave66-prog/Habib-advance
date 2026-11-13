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

/** Native Open XML package editing for DOCX, XLSX and PPTX. */
class OfficeOpenXmlEditingEngine(private val context: Context) {
    suspend fun replaceDocxText(sourceUri: Uri, oldText: String, newText: String,
        outputFileName: String = "Edited_Document.docx"): File = withContext(Dispatchers.IO) {
        require(oldText.isNotEmpty()) { "Find text cannot be empty." }
        var matchesAcrossPackage = 0
        editTextPackage(sourceUri, outputFileName, "docx") { name, bytes ->
            if (name.matches(Regex("word/(document|header\\d*|footer\\d*|footnotes|endnotes)\\.xml"))) {
                val xml = parse(bytes)
                if (replaceUnique(xml, WORD_NS, "t", oldText, newText, "DOCX")) {
                    matchesAcrossPackage++
                    require(matchesAcrossPackage == 1) { "Text occurs in more than one document part. Use a more specific phrase." }
                    serialize(xml)
                } else bytes
            } else bytes
        }
    }

    suspend fun replacePptxText(sourceUri: Uri, oldText: String, newText: String,
        outputFileName: String = "Edited_Presentation.pptx"): File = withContext(Dispatchers.IO) {
        require(oldText.isNotEmpty()) { "Find text cannot be empty." }
        var matchesAcrossPackage = 0
        editTextPackage(sourceUri, outputFileName, "pptx") { name, bytes ->
            if (name.matches(Regex("ppt/slides/slide\\d+\\.xml"))) {
                val xml = parse(bytes)
                if (replaceUnique(xml, DRAWING_NS, "t", oldText, newText, "PPTX")) {
                    matchesAcrossPackage++
                    require(matchesAcrossPackage == 1) { "Text occurs in more than one slide. Use a more specific phrase." }
                    serialize(xml)
                } else bytes
            } else bytes
        }
    }

    suspend fun setXlsxCellValue(sourceUri: Uri, sheetName: String, cellReference: String,
        value: String, formula: String? = null,
        outputFileName: String = "Edited_Workbook.xlsx"): File = withContext(Dispatchers.IO) {
        require(sheetName.isNotBlank()) { "Select a worksheet." }
        require(cellReference.matches(Regex("[A-Za-z]{1,3}[1-9][0-9]{0,6}"))) {
            "Cell reference must look like A1 or BC24."
        }
        require(formula == null || formula.isNotBlank()) { "Formula cannot be empty." }
        val source = copySource(sourceUri)
        val output = outputFile(outputFileName, "xlsx")
        try {
            val worksheet = resolveSheet(source, sheetName)
            rewriteZip(source, output) { name, bytes ->
                if (name != worksheet) bytes else {
                    val xml = parse(bytes)
                    val sheetData = xml.getElementsByTagNameNS(SHEET_NS, "sheetData").item(0) as? Element
                        ?: throw IllegalArgumentException("Worksheet sheetData is missing.")
                    val rowNumber = cellReference.filter { it.isDigit() }.toInt()
                    val row = findOrCreateRow(xml, sheetData, rowNumber)
                    val cells = (0 until row.childNodes.length).mapNotNull {
                        row.childNodes.item(it) as? Element
                    }.filter { it.localName == "c" }
                    val cell = cells.firstOrNull { it.getAttribute("r").equals(cellReference, true) }
                        ?: xml.createElementNS(SHEET_NS, "c").also { newCell ->
                            val targetColumn = columnIndex(cellReference)
                            val nextCell = cells.firstOrNull { columnIndex(it.getAttribute("r")) > targetColumn }
                            if (nextCell != null) row.insertBefore(newCell, nextCell) else row.appendChild(newCell)
                        }
                    cell.setAttribute("r", cellReference.uppercase())
                    while (cell.hasChildNodes()) cell.removeChild(cell.firstChild)
                    if (formula != null) {
                        cell.removeAttribute("t")
                        xml.createElementNS(SHEET_NS, "f").also {
                            it.textContent = formula.removePrefix("=")
                            cell.appendChild(it)
                        }
                        xml.createElementNS(SHEET_NS, "v").also { cell.appendChild(it) }
                    } else if (value.isNotBlank() && value.toDoubleOrNull() != null) {
                        cell.removeAttribute("t")
                        xml.createElementNS(SHEET_NS, "v").also {
                            it.textContent = value
                            cell.appendChild(it)
                        }
                    } else {
                        cell.setAttribute("t", "inlineStr")
                        val inline = xml.createElementNS(SHEET_NS, "is")
                        val text = xml.createElementNS(SHEET_NS, "t")
                        if (value.startsWith(" ") || value.endsWith(" ")) {
                            text.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve")
                        }
                        text.textContent = value
                        inline.appendChild(text)
                        cell.appendChild(inline)
                    }
                    serialize(xml)
                }
            }
            output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally { source.delete() }
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
            val targets = mutableMapOf<String, String>()
            val nodes = rels.getElementsByTagNameNS(PKG_REL_NS, "Relationship")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as Element
                targets[node.getAttribute("Id")] = node.getAttribute("Target")
            }
            val sheets = workbook.getElementsByTagNameNS(SHEET_NS, "sheet")
            for (i in 0 until sheets.length) {
                val sheet = sheets.item(i) as Element
                if (sheet.getAttribute("name") == name) {
                    val target = targets[sheet.getAttributeNS(OFFICE_REL_NS, "id")]
                        ?: error("Worksheet relationship is missing.")
                    return when {
                        target.startsWith("/") -> target.removePrefix("/")
                        target.startsWith("xl/") -> target
                        else -> "xl/" + target
                    }
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

    private fun replaceUnique(xml: Document, namespace: String, tag: String,
        old: String, replacement: String, format: String): Boolean {
        val nodes = xml.getElementsByTagNameNS(namespace, tag)
        val matches = (0 until nodes.length).map { nodes.item(it) }
            .filter { it.textContent.contains(old) }
        val count = matches.sumOf { occurrences(it.textContent, old) }
        require(count <= 1) { "Text occurs more than once in this " + format + " part. Select a more specific phrase." }
        if (count == 0) return false
        matches.single().textContent = matches.single().textContent.replace(old, replacement)
        return true
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
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
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
