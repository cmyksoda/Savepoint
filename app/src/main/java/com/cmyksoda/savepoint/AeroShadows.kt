package com.cmyksoda.savepoint

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

private val glassShadows =
    object : android.util.LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) =
            value.asAndroidBitmap().allocationByteCount
    }

internal data class GlassShadowTexture(
    val image: ImageBitmap,
    val margin: Float,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val stretchable: Boolean,
)

internal fun glassShadowTexture(
    size: Size,
    radius: Float,
    blur: Float,
    offset: Float,
): GlassShadowTexture {
    val margin = blur * 3 + offset
    val corner = ceil(radius + blur * 3).toInt()
    val minimum = corner * 2 + 4
    val stretchable = size.width >= minimum && size.height >= minimum
    val width = if (stretchable) minimum.toFloat() else size.width
    val height = if (stretchable) minimum.toFloat() else size.height
    val key = "$width:$height:$radius:$blur:$offset"
    val bitmap =
        glassShadows.get(key)
            ?: ImageBitmap(
                    (width + margin * 2).toInt().coerceAtLeast(1),
                    (height + margin * 2).toInt().coerceAtLeast(1),
                )
                .also { image ->
                    val paint =
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(45, 38, 25, 50)
                            maskFilter =
                                android.graphics.BlurMaskFilter(
                                    blur,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                                )
                        }
                    android.graphics
                        .Canvas(image.asAndroidBitmap())
                        .drawRoundRect(
                            margin,
                            margin + offset,
                            margin + width,
                            margin + height + offset,
                            radius,
                            radius,
                            paint,
                        )
                    glassShadows.put(key, image)
                }
    return GlassShadowTexture(
        bitmap,
        margin,
        ceil(margin + radius + blur * 3).toInt(),
        ceil(margin + offset + radius + blur * 3).toInt(),
        ceil(margin + radius + blur * 3).toInt(),
        ceil(margin - offset + radius + blur * 3).toInt(),
        stretchable,
    )
}

internal fun DrawScope.drawGlassShadow(texture: GlassShadowTexture, contentSize: Size) {
    val image = texture.image
    if (!texture.stretchable) {
        drawImage(image, Offset(-texture.margin, -texture.margin))
        return
    }
    val width = (contentSize.width + texture.margin * 2).toInt()
    val height = (contentSize.height + texture.margin * 2).toInt()
    val sourceX = intArrayOf(0, texture.left, image.width - texture.right, image.width)
    val sourceY = intArrayOf(0, texture.top, image.height - texture.bottom, image.height)
    val destinationX = intArrayOf(0, texture.left, width - texture.right, width)
    val destinationY = intArrayOf(0, texture.top, height - texture.bottom, height)
    // Only stretch the uniform middle; the blurred corners and edges keep their original pixels.
    translate(-texture.margin, -texture.margin) {
        for (y in 0..2) for (x in 0..2) {
            drawImage(
                image,
                srcOffset = IntOffset(sourceX[x], sourceY[y]),
                srcSize = IntSize(sourceX[x + 1] - sourceX[x], sourceY[y + 1] - sourceY[y]),
                dstOffset = IntOffset(destinationX[x], destinationY[y]),
                dstSize =
                    IntSize(
                        destinationX[x + 1] - destinationX[x],
                        destinationY[y + 1] - destinationY[y],
                    ),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

fun Modifier.softGlassShadow(radius: Dp, elevation: Dp = 3.dp, visible: Boolean = true): Modifier =
    drawWithCache {
        if (!visible || size.width <= 0 || size.height <= 0) return@drawWithCache onDrawBehind {}
        val shadow =
            glassShadowTexture(
                size,
                radius.toPx(),
                maxOf(2.dp.toPx(), elevation.toPx() * .65f),
                elevation.toPx() * .5f,
            )
        onDrawBehind { drawGlassShadow(shadow, size) }
    }
