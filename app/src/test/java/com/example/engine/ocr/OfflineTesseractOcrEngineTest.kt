package com.example.engine.ocr

import org.junit.Assert.assertEquals
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineTesseractOcrEngineTest {
    @Test
    fun packagedBengaliAndEnglishModelsArePresentAndMatchPinnedChecksums() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = mapOf(
            "ben.traineddata" to ("7e9054d1de309714009a85555f5276168d41af9c" to 855841L),
            "eng.traineddata" to ("bbef4675053b5b468cdb477053e28b1c698ba08e" to 4113088L)
        )

        expected.forEach { (fileName, metadata) ->
            val bytes = context.assets.open("tessdata/$fileName").use { it.readBytes() }
            assertEquals("Unexpected model size for $fileName", metadata.second, bytes.size.toLong())
            val sha1 = MessageDigest.getInstance("SHA-1")
                .digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            assertEquals("Checksum mismatch for $fileName", metadata.first, sha1)
        }
    }

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
