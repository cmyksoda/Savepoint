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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ShelfStyle(val key: String, val label: String) {
    LEDGE("ledge", "glass ledge"),
    DOUGH("dough", "doughy plank"),
    LIP("lip", "glass lip"),
    MIRROR("mirror", "mirror glass"),
    TRAY("tray", "glass tray"),
    GLOW("glow", "light pool"),
}

fun shelfStyle(key: String): ShelfStyle =
    ShelfStyle.entries.firstOrNull { it.key == key } ?: ShelfStyle.LEDGE

enum class CoverGround {
    NONE,
    CONTACT,
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
                "edit ${shelf.label} shelf",
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
                Box(Modifier.fillMaxWidth().height(height + if (hunt) 30.dp else 16.dp)) {
                    Spacer(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                            .height(22.dp)
                            .doughPlank(dark)
                    )
                    ShelfGames(
                        row,
                        shelf,
                        artwork,
                        onOpen,
                        Modifier.fillMaxSize(),
                        // Covers settle a little way into the cushion of the plank.
                        PaddingValues(start = 24.dp, end = 24.dp, bottom = if (hunt) 26.dp else 15.dp),
                        if (hunt) CoverGround.NONE else CoverGround.CONTACT,
                    )
                }
            ShelfStyle.LIP ->
                Box(Modifier.fillMaxWidth().height(height + if (hunt) 18.dp else 8.dp)) {
                    ShelfGames(
                        row,
                        shelf,
                        artwork,
                        onOpen,
                        Modifier.fillMaxSize(),
                        PaddingValues(start = 20.dp, end = 20.dp, bottom = if (hunt) 18.dp else 8.dp),
                    )
                    // The lip stands in front of the covers' feet, so they sit inside the shelf.
                    Spacer(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(if (hunt) 14.dp else 20.dp)
                            .glassLip(dark)
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
            ShelfStyle.GLOW ->
                Box(Modifier.fillMaxWidth().height(height + 18.dp).lightPool(dark, height)) {
                    ShelfGames(
                        row,
                        shelf,
                        artwork,
                        onOpen,
                        Modifier.fillMaxSize(),
                        PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp),
                        if (hunt) CoverGround.NONE else CoverGround.CONTACT,
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

/** Shade where a cover meets the surface it rests on: a tight dark core inside a wide soft spread. */
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

/** A long, puffy plank in the same dough as the buttons, tinted pink so it reads on the pale sky. */
fun Modifier.doughPlank(dark: Boolean): Modifier =
    softGlassShadow(11.dp, 12.dp).drawWithCache {
        val radius = size.height / 2
        val body =
            Brush.verticalGradient(
                if (dark) listOf(Color(0xFF6A7293), Color(0xFF474E6D), Color(0xFF30364F), Color(0xFF262B42))
                else listOf(Color.White, Color(0xFFFDE8F3), Color(0xFFF2C6DE), Color(0xFFE6B0D0))
            )
        val gloss =
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (dark) .32f else .9f), Color.White.copy(alpha = 0f)),
                startY = 1.dp.toPx(),
                endY = size.height * .5f,
            )
        val bounce =
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = if (dark) .12f else .45f)),
                startY = size.height * .68f,
                endY = size.height,
            )
        val rim =
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) .6f else 1f),
                    Color.White.copy(alpha = .1f),
                    (if (dark) Color(0xFF8E9AC0) else Color(0xFFD08FB7)).copy(alpha = .6f),
                )
            )
        onDrawBehind {
            drawRoundRect(body, cornerRadius = CornerRadius(radius))
            drawRoundRect(
                gloss,
                topLeft = Offset(radius * .5f, 1.dp.toPx()),
                size = Size(size.width - radius, size.height * .48f),
                cornerRadius = CornerRadius(radius * .7f),
            )
            drawRoundRect(bounce, cornerRadius = CornerRadius(radius))
            drawRoundRect(
                rim,
                topLeft = Offset(.5.dp.toPx(), .5.dp.toPx()),
                size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                cornerRadius = CornerRadius(radius),
                style = Stroke(1.dp.toPx()),
            )
        }
    }

/** Draws the content again below itself, mirrored and fading, as if standing on polished glass. */
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

fun Modifier.glassLip(dark: Boolean): Modifier = drawWithCache {
    val body =
        Brush.verticalGradient(
            if (dark) listOf(Color(0xCC5A6280), Color(0xB8394059), Color(0xD02B3149))
            else listOf(Color(0xEBFFFFFF), Color(0xCCFCE8F2), Color(0xDDF1D3E5))
        )
    val gloss =
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) .2f else .6f), Color.Transparent),
            endY = size.height * .55f,
        )
    val shade =
        Brush.verticalGradient(
            listOf(Color(0xFF21152F).copy(alpha = if (dark) .3f else .15f), Color.Transparent),
            startY = size.height,
            endY = size.height + 14.dp.toPx(),
        )
    val sheen =
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color(0xFFB5E5F0).copy(alpha = if (dark) .4f else .75f),
                Color(0xFFF6B3DE).copy(alpha = if (dark) .4f else .75f),
                Color.Transparent,
            )
        )
    onDrawBehind {
        drawRect(shade, Offset(0f, size.height), Size(size.width, 14.dp.toPx()))
        drawRect(body)
        drawRect(gloss, size = Size(size.width, size.height * .55f))
        drawLine(sheen, Offset(0f, size.height * .58f), Offset(size.width, size.height * .58f), 1.dp.toPx())
        drawLine(
            Color.White.copy(alpha = if (dark) .6f else 1f),
            Offset(0f, .5.dp.toPx()),
            Offset(size.width, .5.dp.toPx()),
            1.dp.toPx(),
        )
        drawLine(
            (if (dark) Color(0xFF9AA4C4) else Color(0xFFD3A4C2)).copy(alpha = .6f),
            Offset(0f, size.height - .5.dp.toPx()),
            Offset(size.width, size.height - .5.dp.toPx()),
            1.dp.toPx(),
        )
    }
}

fun Modifier.mirrorSlab(dark: Boolean, surface: Dp): Modifier = drawWithCache {
    val y = surface.toPx()
    val glass =
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) .14f else .62f), Color.White.copy(alpha = 0f)),
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
        drawLine(sheen, Offset(0f, y + 2.5.dp.toPx()), Offset(size.width, y + 2.5.dp.toPx()), 1.dp.toPx())
    }
}

fun Modifier.lightPool(dark: Boolean, baseline: Dp): Modifier = drawWithCache {
    val center = Offset(size.width / 2, baseline.toPx())
    val radius = size.width * .64f
    val pool =
        Brush.radialGradient(
            listOf(
                (if (dark) Color(0xFFFF7CC2) else Color.White).copy(alpha = if (dark) .42f else 1f),
                (if (dark) Color(0xFF7FD3E8) else Color(0xFFF7B9DA)).copy(alpha = if (dark) .14f else .55f),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        )
    val squash = 36.dp.toPx() / radius
    onDrawBehind {
        withTransform({ scale(1f, squash, pivot = center) }) { drawCircle(pool, radius, center) }
    }
}
