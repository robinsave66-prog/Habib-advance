package com.example.feature.pdfeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfPageSelectionParserTest {
    @Test
    fun parsesSinglePagesAndRangesAsZeroBasedIndices() {
        assertEquals(listOf(0, 2, 3, 4, 7), parsePdfPageSelectionInput("1, 3-5, 8", 8))
    }

    @Test
    fun sortsAndDeduplicatesOverlappingSelections() {
        assertEquals(listOf(0, 1, 2, 3), parsePdfPageSelectionInput("3-4, 1-3, 2", 4))
    }

    @Test
    fun acceptsWhitespaceAroundRangeSeparator() {
        assertEquals(listOf(1, 2, 3), parsePdfPageSelectionInput("2 - 4", 4))
    }

    @Test
    fun rejectsEmptyInputAndInvalidDocumentSize() {
        assertNull(parsePdfPageSelectionInput("", 5))
        assertNull(parsePdfPageSelectionInput("1", 0))
        assertNull(parsePdfPageSelectionInput("   ", 5))
    }

    @Test
    fun rejectsZeroNegativeMalformedAndDescendingRanges() {
        listOf("0", "-1", "4-2", "1,,2", "1.5", "1-a").forEach {
            assertNull("Expected invalid selection: $it", parsePdfPageSelectionInput(it, 5))
        }
    }

    @Test
    fun rejectsPagesOutsideDocumentAndIntegerOverflow() {
        assertNull(parsePdfPageSelectionInput("6", 5))
        assertNull(parsePdfPageSelectionInput("1-6", 5))
        assertNull(parsePdfPageSelectionInput("999999999999999999999", 5))
    }
}
