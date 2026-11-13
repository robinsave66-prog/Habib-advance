package com.example.engine.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTesseractOcrEngineTest {
    @Test
    fun supportsEnglishBengaliAndCombinedOfflineRecognition() {
        assertEquals("eng", OcrLanguage.ENGLISH.tessCode)
        assertEquals("ben", OcrLanguage.BENGALI.tessCode)
        assertEquals("ben+eng", OcrLanguage.BENGALI_AND_ENGLISH.tessCode)
    }

    @Test
    fun exposesOnlyTheRequestedOcrLanguages() {
        assertEquals(
            setOf("eng", "ben", "ben+eng"),
            OcrLanguage.values().map { it.tessCode }.toSet()
        )
        assertTrue(OcrLanguage.values().none { it.label.contains("Arabic", ignoreCase = true) })
    }
}
