package com.example.engine.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineOcrInstrumentedTest {
    private fun textBitmap(text: String, textSize: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(2200, 500, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                this.textSize = textSize
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            drawText(text, 70f, 300f, paint)
        }
        return bitmap
    }

    @Test
    fun recognizesEnglishTextOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = textBitmap("POWER HOUSE 1800 RPM", 170f)
        try {
            OfflineTesseractOcrEngine.openSession(context, OcrLanguage.ENGLISH).use { session ->
                val result = session.recognize(bitmap).uppercase()
                assertTrue("Expected POWER/HOUSE/RPM in OCR result, got: $result",
                    listOf("POWER", "HOUSE", "RPM").count { result.contains(it) } >= 2)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun recognizesBengaliTextOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = textBitmap("বাংলা ভাষা", 190f)
        try {
            OfflineTesseractOcrEngine.openSession(context, OcrLanguage.BENGALI).use { session ->
                val result = session.recognize(bitmap).replace("\\s+".toRegex(), "")
                assertTrue("Expected Bengali text in OCR result, got: $result", result.contains("বাংলা"))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
