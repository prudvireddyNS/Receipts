package com.prudvi.trackbudget.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontVariationTest {
    @Test
    fun variableFontsRenderDistinctDeclaredWeights() {
        val resolver = createFontFamilyResolver(InstrumentationRegistry.getInstrumentation().targetContext)

        val bodyNormal = inkCoverage(resolve(resolver, ReceiptsFonts.instrumentSans, FontWeight.Normal), "Receipts 8888")
        val bodyStrong = inkCoverage(resolve(resolver, ReceiptsFonts.instrumentSans, FontWeight.SemiBold), "Receipts 8888")
        assertTrue("Instrument Sans 600 must render heavier than 400", bodyStrong > bodyNormal)

        val monoMedium = inkCoverage(resolve(resolver, ReceiptsFonts.splineSansMono, FontWeight.Medium), "₹8,888 LABEL")
        val monoSemiBold = inkCoverage(resolve(resolver, ReceiptsFonts.splineSansMono, FontWeight.SemiBold), "₹8,888 LABEL")
        val monoBold = inkCoverage(resolve(resolver, ReceiptsFonts.splineSansMono, FontWeight.Bold), "₹8,888 LABEL")
        assertTrue("Spline Sans Mono 600 must render heavier than 500", monoSemiBold > monoMedium)
        assertTrue("Spline Sans Mono 700 must render heavier than 600", monoBold > monoSemiBold)
    }

    private fun resolve(resolver: FontFamily.Resolver, family: FontFamily, weight: FontWeight): Typeface =
        resolver.resolve(family, weight, FontStyle.Normal, FontSynthesis.None).value as Typeface

    private fun inkCoverage(typeface: Typeface, text: String): Long {
        val bitmap = Bitmap.createBitmap(1_200, 200, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 128f
            color = Color.BLACK
        }
        android.graphics.Canvas(bitmap).drawText(text, 8f, 150f, paint)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        return pixels.sumOf { Color.alpha(it).toLong() }
    }
}
