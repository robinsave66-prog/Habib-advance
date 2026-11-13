package com.example.feature.documentviewer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficeDocumentParserTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun docxPreservesParagraphAndTableOrder() {
        val documentXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>Before table</w:t></w:r></w:p>
                <w:tbl>
                  <w:tr><w:tc><w:p><w:r><w:t>Cell A</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Cell B</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
                <w:p><w:r><w:t>After table</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val file = zipFile("docx-order.docx", mapOf("word/document.xml" to documentXml))

        val result = parseDocxDocument(context, Uri.fromFile(file), file.name, file.length())

        assertEquals(3, result.docxBlocks.size)
        assertEquals("Before table", result.docxBlocks[0].paragraph)
        assertNotNull(result.docxBlocks[1].table)
        assertEquals(listOf("Cell A", "Cell B"), result.docxBlocks[1].table?.first())
        assertEquals("After table", result.docxBlocks[2].paragraph)
    }

    @Test
    fun xlsxUsesWorkbookRelationshipsAndPreservesBlankColumns() {
        val entries = mapOf(
            "xl/workbook.xml" to """
                <workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Second" sheetId="2" r:id="rId2"/>
                    <sheet name="First" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
            """.trimIndent(),
            "xl/_rels/workbook.xml.rels" to """
                <Relationships>
                  <Relationship Id="rId2" Target="worksheets/sheet2.xml"/>
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent(),
            "xl/sharedStrings.xml" to """
                <sst><si><t>Alpha</t></si><si><r><t>Multi</t></r><r><t>Run</t></r></si></sst>
            """.trimIndent(),
            "xl/worksheets/sheet1.xml" to """
                <worksheet><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c></row></sheetData></worksheet>
            """.trimIndent(),
            "xl/worksheets/sheet2.xml" to """
                <worksheet><sheetData><row r="1">
                  <c r="A1" t="s"><v>1</v></c>
                  <c r="C1" t="inlineStr"><is><t>Gap</t></is></c>
                  <c r="D1" t="b"><v>1</v></c>
                </row></sheetData></worksheet>
            """.trimIndent()
        )
        val file = zipFile("workbook-order.xlsx", entries)

        val result = parseXlsxDocument(context, Uri.fromFile(file), file.name, file.length())

        assertEquals(listOf("Second", "First"), result.spreadsheetSheets.keys.toList())
        assertEquals(listOf("MultiRun", "", "Gap", "TRUE"), result.spreadsheetSheets["Second"]?.first())
        assertEquals("Alpha", result.spreadsheetSheets["First"]?.first()?.first())
    }

    @Test
    fun pptxPreservesPresentationOrderAndBlankSlides() {
        val entries = mapOf(
            "ppt/presentation.xml" to """
                <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <p:sldIdLst><p:sldId id="2" r:id="rId2"/><p:sldId id="1" r:id="rId1"/></p:sldIdLst>
                </p:presentation>
            """.trimIndent(),
            "ppt/_rels/presentation.xml.rels" to """
                <Relationships>
                  <Relationship Id="rId2" Target="slides/slide2.xml"/>
                  <Relationship Id="rId1" Target="slides/slide1.xml"/>
                </Relationships>
            """.trimIndent(),
            "ppt/slides/slide1.xml" to """
                <p:sld><p:cSld><p:spTree>
                  <a:p><a:r><a:t>Title &amp; intro</a:t></a:r></a:p>
                  <a:p><a:r><a:t>Second line</a:t></a:r></a:p>
                </p:spTree></p:cSld></p:sld>
            """.trimIndent(),
            "ppt/slides/slide2.xml" to """
                <p:sld><p:cSld><p:spTree/></p:cSld></p:sld>
            """.trimIndent()
        )
        val file = zipFile("presentation-order.pptx", entries)

        val result = parsePptxDocument(context, Uri.fromFile(file), file.name, file.length())

        assertEquals(2, result.presentationSlides.size)
        assertEquals("", result.presentationSlides[0])
        assertEquals("Title & intro\nSecond line", result.presentationSlides[1])
    }

    private fun zipFile(name: String, entries: Map<String, String>): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entryName, content) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }
}
