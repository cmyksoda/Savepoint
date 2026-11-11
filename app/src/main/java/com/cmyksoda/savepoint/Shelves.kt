package com.cmyksoda.savepoint

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ShelfStyle(val key: String, val label: String) {
    LEDGE("ledge", "Glass ledge"),
    DOUGH("dough", "Doughy plank"),
    MIRROR("mirror", "Mirror glass"),
    TRAY("tray", "Glass tray"),
}

fun shelfStyle(key: String): ShelfStyle =
    ShelfStyle.entries.firstOrNull { it.key == key } ?: ShelfStyle.LEDGE

enum class CoverGround {
    NONE,
    CONTACT,
    FOOT,
    REFLECTION,
}

private fun coverAreaHeight(shelf: Shelf): Dp =
    when (shelf) {
        Shelf.PLAY_THROUGH -> 264.dp
        Shelf.HUNTS -> 148.dp
        else -> 196.dp
    }

@Composable
fun ShelfSection(
    shelf: Shelf,
    row: List<Game>,
    style: ShelfStyle,
    artwork: Artwork,
    onOpen: (String) -> Unit,
    onEditShelf: (Shelf) -> Unit,
) {
    val dark = LocalAeroDark.current
    val hunt = shelf == Shelf.HUNTS
    val height = coverAreaHeight(shelf)
    val title: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shelf.label,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            AeroIconButton(
                AeroSymbol.NEXT,
                "Edit ${shelf.label.lowercase()} shelf",
                { onEditShelf(shelf) },
                compact = true,
            )
        }
    }

    if (style == ShelfStyle.TRAY) {
        Glass(Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            title(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp))
            ShelfGames(
                row,
                shelf,
                artwork,
                onOpen,
                Modifier.fillMaxWidth().height(height + 14.dp),
                PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
                if (hunt) CoverGround.NONE else CoverGround.CONTACT,
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        title(Modifier.padding(start = 22.dp, end = 10.dp))
        when (style) {
            ShelfStyle.LEDGE ->
                if (hunt)
                    ShelfGames(
                        row,
                        shelf,
                        artwork,
                        onOpen,
                        Modifier.fillMaxWidth().height(height).glassShelf(dark),
                        PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp),
                    )
                else
                    Column(Modifier.fillMaxWidth().height(height)) {
                        ShelfGames(
                            row,
                            shelf,
                            artwork,
                            onOpen,
                            Modifier.fillMaxWidth().weight(1f),
                            PaddingValues(horizontal = 20.dp),
                        )
                        Spacer(Modifier.fillMaxWidth().height(ShelfSurfaceInset).glassShelf(dark))
                    }
            ShelfStyle.DOUGH ->
                Box(Modifier.fillMaxWidth().height(height + if (hunt) 34.dp else 26.dp)) {
                    Spacer(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(30.dp)
                            .doughPlank(dark, top = 10.dp)
                    )
                    ShelfGames(
                        row,
                        shelf,
                        artwork,
                        onOpen,
                        Modifier.fillMaxSize(),
                        // Covers stand on the plank's top, a little behind its rounded front.
                        PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = if (hunt) 34.dp else 25.dp,
                        ),
                        if (hunt) CoverGround.NONE else CoverGround.FOOT,
                    )
                }

            ShelfStyle.MIRROR ->
                Box(
                    Modifier.fillMaxWidth()
                        .height(height + 30.dp)
                        .mirrorSlab(dark, height)
                ) {
                    ShelfGames(
                        row,
                        shelf,
                        artwork,
                        onOpen,
                        Modifier.fillMaxSize(),
                        PaddingValues(start = 20.dp, end = 20.dp, bottom = 30.dp),
                        if (hunt) CoverGround.NONE else CoverGround.REFLECTION,
                    )
                }

            ShelfStyle.TRAY -> Unit
        }
    }
}

@Composable
private fun ShelfGames(
    games: List<Game>,
    shelf: Shelf,
    artwork: Artwork,
    onOpen: (String) -> Unit,
    modifier: Modifier,
    padding: PaddingValues,
    ground: CoverGround = CoverGround.NONE,
) {
    LazyRow(
        modifier,
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(games, key = { it.id }) { game ->
            GameCover(
                game,
                artwork,
                shelf == Shelf.PLAY_THROUGH && games.first().id == game.id,
                Modifier.animateItem(placementSpec = spring(stiffness = 350f)),
                ground,
            ) {
                onOpen(game.id)
            }
        }
    }
}

fun Modifier.contactShadow(dark: Boolean): Modifier = drawBehind {
    val center = Offset(size.width / 2, size.height + 1.dp.toPx())
    fun shade(radius: Float, height: Float, alpha: Float) {
        val squash = height / radius
        withTransform({ scale(1f, squash, pivot = center) }) {
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF3A1838).copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius,
                center,
            )
        }
    }
    shade(size.width * .78f, 14.dp.toPx(), if (dark) .5f else .26f)
    shade(size.width * .56f, 4.5.dp.toPx(), if (dark) .75f else .46f)
}

// Lit from above, a standing case only darkens the shelf right at its foot.
fun Modifier.footShadow(dark: Boolean): Modifier = drawBehind {
    val center = Offset(size.width / 2, size.height)
    val radius = size.width * .54f
    val squash = 3.dp.toPx() / radius
    withTransform({ scale(1f, squash, pivot = center) }) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF3A1838).copy(alpha = if (dark) .5f else .26f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius,
            center,
        )
    }
}

/**
 * Runs off both edges, lit from above: covers stand on its top
 * while the doughy front rolls into shade.
 */
fun Modifier.doughPlank(dark: Boolean, top: Dp): Modifier = drawWithCache {
    val t = top.toPx()
    val h = size.height
    val face = h - t
    val surface =
        Brush.verticalGradient(
            if (dark) listOf(Color(0xFF5A6390).copy(alpha = .55f), Color(0xFF6E78A4))
            else listOf(Color(0xFFF6DCEA).copy(alpha = .6f), Color(0xFFFFF8FB)),
            startY = 0f,
            endY = t,
        )
    val front =
        Brush.verticalGradient(
            0f to (if (dark) Color(0xFF7C86AE) else Color(0xFFFFF3F8)),
            .35f to (if (dark) Color(0xFF5B648F) else Color(0xFFFBE0ED)),
            .8f to (if (dark) Color(0xFF3D4469) else Color(0xFFF0C2DA)),
            1f to (if (dark) Color(0xFF30365A) else Color(0xFFE6AFCD)),
            startY = t,
            endY = h,
        )
    val crest =
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0f),
                Color.White.copy(alpha = if (dark) .45f else .9f),
                Color.White.copy(alpha = 0f),
            ),
            startY = t - 2.dp.toPx(),
            endY = t + 5.dp.toPx(),
        )
    val bounce =
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color(0xFFFFEAF4).copy(alpha = if (dark) .08f else .35f),
                Color.Transparent,
            ),
            startY = t + face * .55f,
            endY = h - 2.dp.toPx(),
        )
    val underside =
        Brush.verticalGradient(
            listOf(Color.Transparent, Color(0xFF3A1838).copy(alpha = if (dark) .22f else .1f)),
            startY = h - 4.dp.toPx(),
            endY = h,
        )
    val shade =
        Brush.verticalGradient(
            listOf(
                Color(0xFF3A1838).copy(alpha = if (dark) .3f else .13f),
                Color(0xFF3A1838).copy(alpha = if (dark) .08f else .035f),
                Color.Transparent,
            ),
            startY = h,
            endY = h + 16.dp.toPx(),
        )
    onDrawBehind {
        drawRect(shade, Offset(0f, h), Size(size.width, 16.dp.toPx()))
        drawRect(surface, size = Size(size.width, t))
        drawRect(front, Offset(0f, t), Size(size.width, face))
        drawRect(crest, Offset(0f, t - 2.dp.toPx()), Size(size.width, 7.dp.toPx()))
        drawRect(bounce, Offset(0f, t + face * .55f), Size(size.width, face * .45f - 2.dp.toPx()))
        drawRect(underside, Offset(0f, h - 4.dp.toPx()), Size(size.width, 4.dp.toPx()))
    }
}

fun Modifier.coverReflection(depth: Dp, dark: Boolean): Modifier = drawWithCache {
    val layer = obtainGraphicsLayer()
    val gap = 2.dp.toPx()
    val top = size.height + gap
    val bounds = Rect(0f, top, size.width, top + depth.toPx())
    val fade =
        Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = if (dark) .26f else .38f), Color.Transparent),
            startY = bounds.top,
            endY = bounds.bottom,
        )
    onDrawWithContent {
        layer.record { this@onDrawWithContent.drawContent() }
        drawLayer(layer)
        drawContext.canvas.saveLayer(bounds, Paint())
        withTransform({ scale(1f, -1f, pivot = Offset(0f, size.height + gap / 2)) }) {
            drawLayer(layer)
        }
        drawRect(fade, bounds.topLeft, bounds.size, blendMode = BlendMode.DstIn)
        drawContext.canvas.restore()
    }
}

fun Modifier.mirrorSlab(dark: Boolean, surface: Dp): Modifier = drawWithCache {
    val y = surface.toPx()
    val glass =
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (dark) .14f else .62f),
                Color.White.copy(alpha = 0f),
            ),
            startY = y,
            endY = size.height,
        )
    val sheen =
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color(0xFFB5E5F0).copy(alpha = if (dark) .45f else .7f),
                Color(0xFFF6B3DE).copy(alpha = if (dark) .45f else .7f),
                Color.Transparent,
            )
        )
    onDrawBehind {
        drawRect(glass, Offset(0f, y), Size(size.width, size.height - y))
        drawLine(
            Color.White.copy(alpha = if (dark) .65f else 1f),
            Offset(0f, y),
            Offset(size.width, y),
            1.5.dp.toPx(),
        )
        drawLine(
            sheen,
            Offset(0f, y + 2.5.dp.toPx()),
            Offset(size.width, y + 2.5.dp.toPx()),
            1.dp.toPx(),
        )
    }
}
