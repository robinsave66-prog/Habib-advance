package com.example.engine.office

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficeOpenXmlEditingEngineTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val engine by lazy { OfficeOpenXmlEditingEngine(context) }

    @Test fun docxEditsNativeTextAndPreservesFormattingAndMedia() {
        val file = zip("office-test.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Hello Habib</w:t></w:r></w:p></w:body></w:document>""",
            "word/header1.xml" to """<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:t>Header text stays</w:t></w:r></w:p></w:hdr>""",
            "word/media/image1.png" to "image-bytes"
        ))
        val out = runBlocking { engine.replaceDocxText(Uri.fromFile(file), "Habib", "Editor", "edited-test.docx") }
        ZipFile(out).use { z ->
            val xml = z.getInputStream(z.getEntry("word/document.xml")).bufferedReader().readText()
            assertTrue(xml.contains("Hello Editor"))
            assertTrue(xml.contains("w:b"))
            assertEquals("image-bytes", z.getInputStream(z.getEntry("word/media/image1.png")).bufferedReader().readText())
        }
    }

    @Test fun xlsxEditsCellByWorksheetRelationshipAndKeepsStyle() {
        val file = zip("office-test.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="worksheets/sheet3.xml"/></Relationships>""",
            "xl/worksheets/sheet3.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" s="4"><v>12</v></c></row></sheetData></worksheet>""",
            "xl/styles.xml" to "<styleSheet/>"
        ))
        val out = runBlocking { engine.setXlsxCellValue(Uri.fromFile(file), "Data", "A1", "Updated", outputFileName = "edited-test.xlsx") }
        ZipFile(out).use { z ->
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(z.getInputStream(z.getEntry("xl/worksheets/sheet3.xml")))
            val cell = xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "c").item(0) as org.w3c.dom.Element
            assertEquals("4", cell.getAttribute("s"))
            assertEquals("inlineStr", cell.getAttribute("t"))
            assertEquals("Updated", xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "t").item(0).textContent)
        }
    }

    @Test fun pptxEditsSlideTextRun() {
        val file = zip("office-test.pptx", mapOf(
            "ppt/slides/slide1.xml" to """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:rPr b="1"/><a:t>Quarterly Report</a:t></a:r></a:p></p:txBody></p:spTree></p:cSld></p:sld>"""
        ))
        val out = runBlocking { engine.replacePptxText(Uri.fromFile(file), "Quarterly", "Annual", "edited-test.pptx") }
        ZipFile(out).use { z ->
            val xml = z.getInputStream(z.getEntry("ppt/slides/slide1.xml")).bufferedReader().readText()
            assertTrue(xml.contains("Annual Report"))
            assertTrue(xml.contains("b=\"1\""))
        }
    }

    @Test fun repeatedDocxTextIsRejectedRatherThanChangingMultipleLocations() {
        val file = zip("office-ambiguous.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>same same</w:t></w:r></w:p></w:body></w:document>"""
        ))
        var rejected = false
        try { runBlocking { engine.replaceDocxText(Uri.fromFile(file), "same", "new", "ambiguous-out.docx") } }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    private fun zip(name: String, entries: Map<String, String>): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (entry, content) ->
                out.putNextEntry(ZipEntry(entry))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }
}
