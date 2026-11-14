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
            assertEquals("Header text stays", z.getInputStream(z.getEntry("word/header1.xml")).bufferedReader().readText().let {
                Regex("<w:t>(.*?)</w:t>").find(it)?.groupValues?.get(1)
            })
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

    @Test fun xlsxBatchEditsMultipleCellsAndFormulaInOnePass() {
        val file = zip("office-batch.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="./worksheets/sheet3.xml"/></Relationships>""",
            "xl/worksheets/sheet3.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" s="4"><v>12</v></c></row></sheetData></worksheet>""",
            "xl/styles.xml" to "<styleSheet/>"
        ))
        val out = runBlocking {
            engine.setXlsxCellValues(
                Uri.fromFile(file), "Data",
                listOf(
                    XlsxCellEdit("A1", "Updated"),
                    XlsxCellEdit("B1", "42"),
                    XlsxCellEdit("C1", formula = "=SUM(B1:B4)")
                ),
                "edited-batch.xlsx"
            )
        }
        ZipFile(out).use { zip ->
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("xl/worksheets/sheet3.xml")))
            val cells = xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "c")
            assertEquals(3, cells.length)
            val first = cells.item(0) as org.w3c.dom.Element
            assertEquals("4", first.getAttribute("s"))
            assertEquals("Updated", xml.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "t").item(0).textContent)
            val formulaCell = cells.item(2) as org.w3c.dom.Element
            assertEquals("C1", formulaCell.getAttribute("r"))
            val workbook = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("xl/workbook.xml")))
            val calc = workbook.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/spreadsheetml/2006/main", "calcPr"
            ).item(0) as org.w3c.dom.Element
            assertEquals("auto", calc.getAttribute("calcMode"))
            assertEquals("1", calc.getAttribute("fullCalcOnLoad"))
            assertEquals("1", calc.getAttribute("forceFullCalc"))
            assertEquals("SUM(B1:B4)", formulaCell.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "f").item(0).textContent)
        }
    }

    @Test fun xlsxBatchRejectsDuplicateCellReferences() {
        val file = zip("office-duplicate-cells.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>"""
        ))
        var rejected = false
        try {
            runBlocking {
                engine.setXlsxCellValues(
                    Uri.fromFile(file), "Data",
                    listOf(XlsxCellEdit("a1", "one"), XlsxCellEdit("A1", "two")),
                    "duplicate-cells.xlsx"
                )
            }
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun xlsxRejectsReferencesOutsideExcelWorksheetLimits() {
        val file = zip("office-out-of-range.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>"""
        ))
        val invalidReferences = listOf("XFE1", "A1048577", "AAAA1")
        invalidReferences.forEach { reference ->
            var rejected = false
            try {
                runBlocking {
                    engine.setXlsxCellValue(
                        Uri.fromFile(file), "Data", reference, "invalid",
                        outputFileName = "out-of-range-$reference.xlsx"
                    )
                }
            } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Expected $reference to be rejected", rejected)
        }
    }

    @Test fun xlsxRejectsMissingWorksheetPackagePart() {
        val file = zip("office-missing-sheet-part.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="worksheets/missing.xml"/></Relationships>"""
        ))
        var rejected = false
        try {
            runBlocking {
                engine.setXlsxCellValue(
                    Uri.fromFile(file), "Data", "A1", "value",
                    outputFileName = "missing-sheet-part-out.xlsx"
                )
            }
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun pptxEditsSlideTextRun() {
        val file = zip("office-test.pptx", mapOf(
            "ppt/slides/slide1.xml" to """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:rPr b="1"/><a:t>Quarterly Report</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"""
        ))
        val out = runBlocking { engine.replacePptxText(Uri.fromFile(file), "Quarterly", "Annual", "edited-test.pptx") }
        ZipFile(out).use { z ->
            val xml = z.getInputStream(z.getEntry("ppt/slides/slide1.xml")).bufferedReader().readText()
            assertTrue(xml.contains("Annual Report"))
            assertTrue(xml.contains("b=\"1\""))
        }
    }

    @Test fun docxReplacementCanSpanMultipleFormattedRuns() {
        val file = zip("office-split-runs.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Hello </w:t></w:r><w:r><w:rPr><w:i/></w:rPr><w:t>Habib</w:t></w:r><w:r><w:t>!</w:t></w:r></w:p></w:body></w:document>"""
        ))
        val out = runBlocking { engine.replaceDocxText(Uri.fromFile(file), "Hello Habib", "New Editor", "edited-split-runs.docx") }
        ZipFile(out).use { zip ->
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")))
            val texts = xml.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "t"
            )
            val combined = (0 until texts.length).joinToString("") { texts.item(it).textContent }
            assertEquals("New Editor!", combined)
            assertTrue(zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText().contains("w:i"))
        }
    }

    @Test fun pptxReplacementCanSpanMultipleTextRuns() {
        val file = zip("office-split-runs.pptx", mapOf(
            "ppt/slides/slide1.xml" to """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:rPr b="1"/><a:t>Quarter</a:t></a:r><a:r><a:rPr i="1"/><a:t>ly Report</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"""
        ))
        val out = runBlocking { engine.replacePptxText(Uri.fromFile(file), "Quarterly Report", "Annual Review", "edited-split-runs.pptx") }
        ZipFile(out).use { zip ->
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("ppt/slides/slide1.xml")))
            val texts = xml.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/drawingml/2006/main", "t"
            )
            val combined = (0 until texts.length).joinToString("") { texts.item(it).textContent }
            assertEquals("Annual Review", combined)
            assertTrue(zip.getInputStream(zip.getEntry("ppt/slides/slide1.xml")).bufferedReader().readText().contains("i=\"1\""))
        }
    }

    @Test fun docxReplacementDoesNotJoinTextAcrossNestedParagraphs() {
        val file = zip("office-nested-paragraph.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Alpha</w:t></w:r><w:r><w:drawing><w:txbxContent><w:p><w:r><w:t>Beta</w:t></w:r></w:p></w:txbxContent></w:drawing></w:r></w:p></w:body></w:document>"""
        ))
        var rejected = false
        try {
            runBlocking {
                engine.replaceDocxText(
                    Uri.fromFile(file), "AlphaBeta", "Changed",
                    "nested-paragraph-out.docx"
                )
            }
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Text in separate nested paragraphs must not be treated as one phrase", rejected)
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


    @Test fun docxEditsHeaderTextPartWithoutChangingBody() {
        val file = zip("office-header.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Body stays</w:t></w:r></w:p></w:body></w:document>""",
            "word/header1.xml" to """<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:t>Confidential Header</w:t></w:r></w:p></w:hdr>"""
        ))
        val out = runBlocking {
            engine.replaceDocxText(Uri.fromFile(file), "Confidential", "Public", "edited-header.docx")
        }
        ZipFile(out).use { zip ->
            val body = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
            val header = zip.getInputStream(zip.getEntry("word/header1.xml")).bufferedReader().readText()
            assertTrue(body.contains("Body stays"))
            assertTrue(header.contains("Public Header"))
        }
    }

    @Test fun pptxRepeatedPhraseAcrossSlidesIsRejected() {
        val slide = """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>Quarterly Report</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"""
        val file = zip("office-repeated-slides.pptx", mapOf(
            "ppt/slides/slide1.xml" to slide,
            "ppt/slides/slide2.xml" to slide
        ))
        var rejected = false
        try {
            runBlocking {
                engine.replacePptxText(Uri.fromFile(file), "Quarterly Report", "Annual Report", "repeated-slides-out.pptx")
            }
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("Ambiguous text across slides must be rejected", rejected)
        assertFalse(File(context.filesDir, "documents/repeated-slides-out.pptx").exists())
    }

    @Test fun docxRejectsDoctypeAndExternalEntityPayload() {
        val file = zip("office-xxe.docx", mapOf(
            "word/document.xml" to """<!DOCTYPE w:document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>"""
        ))
        var rejected = false
        try {
            runBlocking { engine.replaceDocxText(Uri.fromFile(file), "root", "changed", "xxe-out.docx") }
        } catch (_: Exception) { rejected = true }
        assertTrue("DOCTYPE and external entity XML must be rejected", rejected)
        assertFalse(File(context.filesDir, "documents/xxe-out.docx").exists())
    }

    @Test fun xlsxRejectsWorksheetRelationshipEscapingPackageRoot() {
        val file = zip("office-escaping-sheet.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="../../outside.xml"/></Relationships>"""
        ))
        var rejected = false
        try {
            runBlocking { engine.setXlsxCellValue(Uri.fromFile(file), "Data", "A1", "unsafe", "escaping-sheet-out.xlsx") }
        } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("Worksheet relationship must remain inside package root", rejected)
        assertFalse(File(context.filesDir, "documents/escaping-sheet-out.xlsx").exists())
    }


    @Test fun docxAppliesTextAndParagraphFormattingWhenRequested() {
        val file = zip("office-docx-style.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Style me</w:t></w:r></w:p></w:body></w:document>"""
        ))
        val out = runBlocking {
            engine.replaceDocxText(
                Uri.fromFile(file), "Style me", "Styled",
                "office-docx-style-out.docx",
                OfficeTextStyle(true, true, true, 18f, "1E88E5", "CENTER")
            )
        }
        ZipFile(out).use { zip ->
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")))
            val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            val bold = xml.getElementsByTagNameNS(ns, "b").item(0) as org.w3c.dom.Element
            val italic = xml.getElementsByTagNameNS(ns, "i").item(0) as org.w3c.dom.Element
            val underline = xml.getElementsByTagNameNS(ns, "u").item(0) as org.w3c.dom.Element
            val size = xml.getElementsByTagNameNS(ns, "sz").item(0) as org.w3c.dom.Element
            val color = xml.getElementsByTagNameNS(ns, "color").item(0) as org.w3c.dom.Element
            val alignment = xml.getElementsByTagNameNS(ns, "jc").item(0) as org.w3c.dom.Element
            assertEquals("1", bold.getAttributeNS(ns, "val"))
            assertEquals("1", italic.getAttributeNS(ns, "val"))
            assertEquals("single", underline.getAttributeNS(ns, "val"))
            assertEquals("36", size.getAttributeNS(ns, "val"))
            assertEquals("1E88E5", color.getAttributeNS(ns, "val"))
            assertEquals("center", alignment.getAttributeNS(ns, "val"))
        }
    }

    @Test fun pptxAppliesTextAndParagraphFormattingWhenRequested() {
        val file = zip("office-pptx-style.pptx", mapOf(
            "ppt/slides/slide1.xml" to """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>Style me</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"""
        ))
        val out = runBlocking {
            engine.replacePptxText(
                Uri.fromFile(file), "Style me", "Styled",
                "office-pptx-style-out.pptx",
                OfficeTextStyle(true, false, true, 20f, "FF5500", "RIGHT")
            )
        }
        ZipFile(out).use { zip ->
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("ppt/slides/slide1.xml")))
            val ns = "http://schemas.openxmlformats.org/drawingml/2006/main"
            val run = xml.getElementsByTagNameNS(ns, "rPr").item(0) as org.w3c.dom.Element
            assertEquals("1", run.getAttribute("b"))
            assertEquals("0", run.getAttribute("i"))
            assertEquals("sng", run.getAttribute("u"))
            assertEquals("2000", run.getAttribute("sz"))
            assertEquals("FF5500", xml.getElementsByTagNameNS(ns, "srgbClr").item(0).attributes.getNamedItem("val").nodeValue)
            assertEquals("r", (xml.getElementsByTagNameNS(ns, "pPr").item(0) as org.w3c.dom.Element).getAttribute("algn"))
        }
    }

    @Test fun officeTextStyleRejectsInvalidFontSizeAndColor() {
        val file = zip("office-invalid-style.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Style me</w:t></w:r></w:p></w:body></w:document>"""
        ))
        listOf(
            OfficeTextStyle(false, false, false, 0f, "000000", "LEFT"),
            OfficeTextStyle(false, false, false, 12f, "#FFFFFF", "LEFT"),
            OfficeTextStyle(false, false, false, 12f, "GGGGGG", "LEFT")
        ).forEachIndexed { index, style ->
            var rejected = false
            try {
                runBlocking {
                    engine.replaceDocxText(
                        Uri.fromFile(file), "Style me", "Styled",
                        "office-invalid-style-$index.docx", style
                    )
                }
            } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Invalid style $index must be rejected", rejected)
        }
    }

    @Test fun xlsxAppliesNativeCellFontFillAndAlignmentStyle() {
        val file = zip("office-format.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1"><v>12</v></c></row></sheetData></worksheet>""",
            "xl/styles.xml" to "<styleSheet/>"
        ))
        val out = runBlocking {
            engine.formatXlsxCell(Uri.fromFile(file), "Data", "A1",
                XlsxCellFormat(bold = true, italic = true, fontSizePt = 14f,
                    fontColorHex = "112233", fillColorHex = "AABBCC",
                    horizontalAlignment = "CENTER", wrapText = true),
                "formatted-cell.xlsx")
        }
        ZipFile(out).use { zip ->
            val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
            val sheet = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")))
            val cell = sheet.getElementsByTagNameNS(ns, "c").item(0) as org.w3c.dom.Element
            assertEquals("1", cell.getAttribute("s"))
            val styles = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("xl/styles.xml")))
            assertEquals("1", styles.getElementsByTagNameNS(ns, "b").length.toString())
            assertEquals("CENTER", styles.getElementsByTagNameNS(ns, "alignment").item(0)
                .let { (it as org.w3c.dom.Element).getAttribute("horizontal").uppercase() })
            val formattedFont = styles.getElementsByTagNameNS(ns, "font").item(1) as org.w3c.dom.Element
            assertEquals("FF112233", (formattedFont.getElementsByTagNameNS(ns, "color").item(0) as org.w3c.dom.Element).getAttribute("rgb"))
        }
    }

    @Test fun docxCanEditIndividualTableCellWithoutFlatteningTable() {
        val file = zip("table-cell.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:tbl><w:tr><w:tc><w:p><w:r><w:t>Old</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Keep</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"""
        ))
        val out = runBlocking { engine.setDocxTableCellText(Uri.fromFile(file), 0, 0, 0, "Updated", "table-cell-edited.docx") }
        ZipFile(out).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
            assertTrue(xml.contains("Updated")); assertTrue(xml.contains("Keep")); assertFalse(xml.contains(">Old<"))
            assertTrue(xml.contains("<w:tbl"))
        }
    }

    @Test fun pptxCanMoveAndResizeNamedPictureOrShapeAndResizeSlideCanvas() {
        val file = zip("ppt-layout.pptx", mapOf(
            "ppt/slides/slide1.xml" to """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:nvSpPr><p:cNvPr id="2" name="TitleBox"/></p:nvSpPr><p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm></p:spPr></p:sp></p:spTree></p:cSld></p:sld>""",
            "ppt/presentation.xml" to """<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldSz cx="9144000" cy="6858000"/></p:presentation>"""
        ))
        val moved = runBlocking {
            engine.setPptxObjectGeometry(Uri.fromFile(file), 1, "TitleBox",
                PptxObjectGeometry(1000, 2000, 3000, 4000, 30f), "ppt-object-moved.pptx")
        }
        val resized = runBlocking { engine.setPptxSlideSize(Uri.fromFile(moved), 8000000, 4500000, "ppt-slide-resized.pptx") }
        ZipFile(resized).use { zip ->
            val slide = zip.getInputStream(zip.getEntry("ppt/slides/slide1.xml")).bufferedReader().readText()
            assertTrue(Regex("x=.1000. y=.2000.").containsMatchIn(slide))
            assertTrue(Regex("cx=.3000. cy=.4000.").containsMatchIn(slide))
            assertTrue(Regex("rot=.1800000.").containsMatchIn(slide))
            val presentation = zip.getInputStream(zip.getEntry("ppt/presentation.xml")).bufferedReader().readText()
            assertTrue(Regex("cx=.8000000. cy=.4500000.").containsMatchIn(presentation))
        }
    }

    @Test fun docxCanResizeAndRepositionFloatingPictureByName() {
        val file = zip("docx-image-layout.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><w:body><w:p><w:r><w:drawing><wp:anchor><wp:positionH relativeFrom="page"><wp:posOffset>0</wp:posOffset></wp:positionH><wp:positionV relativeFrom="page"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="100" cy="100"/><wp:docPr id="1" name="Picture 1"/></wp:anchor></w:drawing></w:r></w:p></w:body></w:document>"""
        ))
        val out = runBlocking {
            engine.setDocxImageGeometry(Uri.fromFile(file), "Picture 1",
                DocxImageGeometry(1000, 2000, 3000, 4000), "docx-image-layout-edited.docx")
        }
        ZipFile(out).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
            assertTrue(xml.contains("<wp:posOffset>1000</wp:posOffset>"))
            assertTrue(xml.contains("<wp:posOffset>2000</wp:posOffset>"))
            assertTrue(Regex("cx=.3000. cy=.4000.").containsMatchIn(xml))
        }
    }

    @Test fun docxCanFormatTableCellTextAlignmentAndFill() {
        val file = zip("docx-table-style.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:tbl><w:tr><w:tc><w:tcPr/><w:p><w:r><w:t>Cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"""
        ))
        val out = runBlocking {
            engine.formatDocxTableCell(Uri.fromFile(file), 0, 0, 0,
                OfficeTextStyle(true, true, true, 14f, "112233", "CENTER"),
                "AABBCC", "docx-table-style-edited.docx")
        }
        ZipFile(out).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
            assertTrue(xml.contains("""w:fill="AABBCC""""))
            assertTrue(xml.contains("""w:val="center""""))
            assertTrue(xml.contains("""w:val="112233""""))
            assertTrue(xml.contains("""w:val="28""""))
            assertTrue(xml.contains("<w:b")); assertTrue(xml.contains("<w:i")); assertTrue(xml.contains("<w:u"))
        }
    }

    @Test fun xlsxCanSetRowHeightAndColumnWidthWithoutChangingCellValues() {
        val file = zip("xlsx-dimensions.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Data" sheetId="1" r:id="rId7"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1"><v>12</v></c></row></sheetData></worksheet>"""
        ))
        val rowSized = runBlocking { engine.setXlsxRowHeight(Uri.fromFile(file), "Data", 1, 32f, "xlsx-row-sized.xlsx") }
        val out = runBlocking { engine.setXlsxColumnWidth(Uri.fromFile(rowSized), "Data", "B", 24f, "xlsx-column-sized.xlsx") }
        ZipFile(out).use { zip ->
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")))
            val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
            val row = xml.getElementsByTagNameNS(ns, "row").item(0) as org.w3c.dom.Element
            assertEquals("32.0", row.getAttribute("ht"))
            assertEquals("1", row.getAttribute("customHeight"))
            val col = xml.getElementsByTagNameNS(ns, "col").item(0) as org.w3c.dom.Element
            assertEquals("2", col.getAttribute("min"))
            assertEquals("2", col.getAttribute("max"))
            assertEquals("24.0", col.getAttribute("width"))
            assertEquals("1", col.getAttribute("customWidth"))
            assertEquals("12", xml.getElementsByTagNameNS(ns, "v").item(0).textContent)
        }
    }

    @Test fun pptxCanChangeNativeShapeFillAndOutlineColors() {
        val file = zip("ppt-shape-style.pptx", mapOf(
            "ppt/slides/slide1.xml" to """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:nvSpPr><p:cNvPr id="2" name="Box"/></p:nvSpPr><p:spPr><a:xfrm/><a:prstGeom prst="rect"/></p:spPr></p:sp></p:spTree></p:cSld></p:sld>"""
        ))
        val out = runBlocking {
            engine.setPptxShapeStyle(Uri.fromFile(file), 1, "Box",
                PptxShapeStyle("112233", "AABBCC"), "ppt-shape-style-edited.pptx")
        }
        ZipFile(out).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("ppt/slides/slide1.xml")).bufferedReader().readText()
            assertTrue(xml.contains("""<a:srgbClr val="112233""""))
            assertTrue(xml.contains("""<a:srgbClr val="AABBCC""""))
        }
    }

    @Test fun xlsxCanHideAndShowAWorksheetButNeverHideTheLastVisibleSheet() {
        val file = zip("xlsx-sheet-visibility.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheets><sheet name="Data" sheetId="1"/><sheet name="Summary" sheetId="2"/></sheets></workbook>"""
        ))
        val hidden = runBlocking { engine.setXlsxSheetVisibility(Uri.fromFile(file), "Data", false, "xlsx-hidden.xlsx") }
        val shown = runBlocking { engine.setXlsxSheetVisibility(Uri.fromFile(hidden), "Data", true, "xlsx-shown.xlsx") }
        ZipFile(hidden).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("xl/workbook.xml")).bufferedReader().readText()
            assertTrue(Regex("""name="Data"[^>]*state="hidden"""").containsMatchIn(xml))
        }
        ZipFile(shown).use { zip ->
            val xml = zip.getInputStream(zip.getEntry("xl/workbook.xml")).bufferedReader().readText()
            assertFalse(Regex("""name="Data"[^>]*state="hidden"""").containsMatchIn(xml))
        }
        val onlySheet = zip("xlsx-one-sheet.xlsx", mapOf(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheets><sheet name="Only" sheetId="1"/></sheets></workbook>"""
        ))
        try {
            runBlocking { engine.setXlsxSheetVisibility(Uri.fromFile(onlySheet), "Only", false, "invalid-hidden.xlsx") }
            throw AssertionError("Hiding the last visible worksheet must be rejected.")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("at least one worksheet visible"))
        }
    }


    @Test fun docxCanChangePageSizeOrientationAndMarginsNatively() {
        val file = zip("docx-page-layout.docx", mapOf(
            "word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Text</w:t></w:r></w:p><w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:bottom="1440" w:left="1440" w:right="1440"/></w:sectPr></w:body></w:document>"""
        ))
        val out = runBlocking {
            engine.setDocxPageLayout(Uri.fromFile(file),
                DocxPageLayout(16838, 11906, 720, 720, 900, 900, 500, 500),
                "docx-page-layout-edited.docx")
        }
        ZipFile(out).use { zip ->
            val ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")))
            val pageSize = xml.getElementsByTagNameNS(ns, "pgSz").item(0) as org.w3c.dom.Element
            assertEquals("16838", pageSize.getAttributeNS(ns, "w"))
            assertEquals("11906", pageSize.getAttributeNS(ns, "h"))
            assertEquals("landscape", pageSize.getAttributeNS(ns, "orient"))
            val margins = xml.getElementsByTagNameNS(ns, "pgMar").item(0) as org.w3c.dom.Element
            assertEquals("720", margins.getAttributeNS(ns, "top"))
            assertEquals("900", margins.getAttributeNS(ns, "left"))
            assertEquals("500", margins.getAttributeNS(ns, "header"))
            assertTrue(zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText().contains("Text"))
        }
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
