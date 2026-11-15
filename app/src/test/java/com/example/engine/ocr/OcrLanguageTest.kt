package com.example.engine.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrLanguageTest {
    @Test
    fun onlyEnglishBengaliAndCombinedModesAreAvailable() {
        assertEquals(listOf("eng", "ben", "ben+eng"), OcrLanguage.values().map { it.tessCode })
    }

    @Test
    fun combinedModeRequestsBothOfflineModels() {
        assertEquals("ben+eng", OcrLanguage.BENGALI_AND_ENGLISH.tessCode)
    }
}
