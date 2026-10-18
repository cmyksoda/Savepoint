package com.cmyksoda.savepoint

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassShadowInstrumentedTest {
    private fun original(size: Size, radius: Float, blur: Float, offset: Float): Bitmap {
        val margin = blur * 3 + offset
        val bitmap =
            Bitmap.createBitmap(
                (size.width + margin * 2).toInt(),
                (size.height + margin * 2).toInt(),
                Bitmap.Config.ARGB_8888,
            )
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glassShadowColor
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
        android.graphics
            .Canvas(bitmap)
            .drawRoundRect(
                margin,
                margin + offset,
                margin + size.width,
                margin + size.height + offset,
                radius,
                radius,
                paint,
            )
        return bitmap
    }

    @Test
    fun stretchedShadowMatchesTheOriginalBlurAtPhoneDensities() {
        for (density in listOf(1f, 2.625f, 3f)) {
            val radius = 18 * density
            val blur = 4.55f * density
            val offset = 3.5f * density
            for (size in
                listOf(
                    Size((350 * density).toInt().toFloat(), (280 * density).toInt().toFloat()),
                    Size(100 * density, 40 * density),
                )) {
                val reference = original(size, radius, blur, offset)
                val texture = glassShadowTexture(size, radius, blur, offset)
                val rendered =
                    Bitmap.createBitmap(reference.width, reference.height, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(
                    Density(1f),
                    LayoutDirection.Ltr,
                    Canvas(rendered.asImageBitmap()),
                    Size(reference.width.toFloat(), reference.height.toFloat()),
                ) {
                    translate(texture.margin, texture.margin) { drawGlassShadow(texture, size) }
                }
                val expected = IntArray(reference.width * reference.height)
                val actual = IntArray(expected.size)
                reference.getPixels(
                    expected,
                    0,
                    reference.width,
                    0,
                    0,
                    reference.width,
                    reference.height,
                )
                rendered.getPixels(
                    actual,
                    0,
                    reference.width,
                    0,
                    0,
                    reference.width,
                    reference.height,
                )
                val difference =
                    expected.indices.maxOf {
                        kotlin.math.abs((expected[it] ushr 24) - (actual[it] ushr 24))
                    }
                assertTrue(
                    "Shadow changed at density $density / $size: $difference alpha levels",
                    difference <= 2,
                )
            }
        }
    }

    @Test
    fun dockAndCalendarReuseOneSmallTextureRatherThanFullCardBitmaps() {
        val density = 2.625f
        val radius = 18 * density
        val blur = 4.55f * density
        val offset = 3.5f * density
        val dock = glassShadowTexture(Size(360 * density, 68 * density), radius, blur, offset)
        val calendar = glassShadowTexture(Size(360 * density, 320 * density), radius, blur, offset)
        assertTrue(dock.stretchable)
        assertTrue(calendar.stretchable)
        assertSame(dock.image, calendar.image)
        assertTrue(
            calendar.image.asAndroidBitmap().allocationByteCount <
                360 * density * 320 * density * 4 / 4
        )
    }
}
