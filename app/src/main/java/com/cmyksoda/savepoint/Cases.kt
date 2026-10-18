package com.cmyksoda.savepoint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalGameCases = staticCompositionLocalOf { false }

enum class CaseKind {
    PLASTIC,
    CARDBOARD,
    DIGITAL,
}

fun caseKind(system: String): CaseKind =
    when (catalogSystemName(system)) {
        "NES",
        "Famicom Disk System",
        "SNES",
        "Nintendo 64",
        "Game Boy",
        "Game Boy Color",
        "Game Boy Advance",
        "Virtual Boy",
        "Pokémon mini",
        "Atari 2600",
        "Atari 7800",
        "Atari Lynx",
        "Neo Geo Pocket Color",
        "WonderSwan" -> CaseKind.CARDBOARD
        "PC",
        "iOS",
        "Android",
        "Arcade",
        "Other" -> CaseKind.DIGITAL
        else -> CaseKind.PLASTIC
    }

/** Plastic cases round their corners a little less than flat art; cardboard boxes stay crisp. */
@Composable
fun coverRadius(system: String, flat: Dp): Dp =
    if (!LocalGameCases.current) flat
    else
        when (caseKind(system)) {
            CaseKind.PLASTIC -> flat * .8f
            CaseKind.CARDBOARD -> maxOf(flat * .25f, 1.5.dp)
            CaseKind.DIGITAL -> flat
        }

/** Plastic and digital art keep a pale rim of light; a carton's edge is its own printed border. */
@Composable
fun coverRim(system: String): Color =
    if (LocalGameCases.current && caseKind(system) == CaseKind.CARDBOARD) Color.Black.copy(alpha = .14f)
    else Color.White.copy(alpha = .65f)

/** Light and shade of the physical box drawn over the art: a sleeve's sheen or a carton's folds. */
fun Modifier.caseFinish(kind: CaseKind): Modifier = drawWithContent {
    drawContent()
    val w = size.width
    val h = size.height
    val unit = (w / 144.dp.toPx()).coerceIn(.35f, 1.6f)
    fun dp(value: Float) = value.dp.toPx() * unit
    when (kind) {
        CaseKind.PLASTIC -> {
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    .18f to Color.White.copy(alpha = .07f),
                    .38f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(w, h),
                )
            )
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .14f)),
                    startX = w - dp(5f),
                    endX = w,
                ),
                Offset(w - dp(5f), 0f),
                Size(dp(5f), h),
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = .2f), Color.Transparent), endY = dp(3f)),
                size = Size(w, dp(3f)),
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .12f)),
                    startY = h - dp(4f),
                    endY = h,
                ),
                Offset(0f, h - dp(4f)),
                Size(w, dp(4f)),
            )
        }
        CaseKind.CARDBOARD -> {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .08f), Color.Transparent, Color.Black.copy(alpha = .1f))
                )
            )
            // Folded edges catch the light along the top and left and fall into shade on the others.
            val line = maxOf(1f, dp(1f))
            drawLine(Color.White.copy(alpha = .38f), Offset(0f, line / 2), Offset(w, line / 2), line)
            drawLine(Color.White.copy(alpha = .24f), Offset(line / 2, 0f), Offset(line / 2, h), line)
            drawLine(Color.Black.copy(alpha = .26f), Offset(w - line / 2, 0f), Offset(w - line / 2, h), line)
            drawLine(Color.Black.copy(alpha = .3f), Offset(0f, h - line / 2), Offset(w, h - line / 2), line)
        }
        CaseKind.DIGITAL -> Unit
    }
}
