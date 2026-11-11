package com.cmyksoda.savepoint

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

fun playingNow(games: List<Game>): Game? =
    games
        .filter { it.place == Place.ACTIVE && it.shelf == Shelf.PLAY_THROUGH }
        .minByOrNull { it.position }

private val plum = ColorProvider(day = Color(0xFF3A2341), night = Color(0xFFF7EAF6))
private val dusk = ColorProvider(day = Color(0xFF7A5B78), night = Color(0xFFCDB7D3))
private val berry = ColorProvider(day = Color(0xFFA93468), night = Color(0xFFF6A8CF))

class SavepointWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val game = Library(context).use { library ->
            library.load()
            playingNow(library.games.value)
        }
        val cover =
            game?.cover?.takeIf { it.isNotBlank() && game.shelf != Shelf.HUNTS }?.let { url ->
                val artwork = Artwork(context)
                runCatching {
                        artwork.cachedCoverBitmap(url)
                            ?: artwork.bitmap(preferredCoverUrl(url))
                            ?: artwork.bitmap(url)
                    }
                    .getOrNull()
                    ?.let(::widgetSized)
            }
        provideContent { PlayingNow(game, cover) }
    }
}

// Widgets share a small bitmap budget with the launcher, so covers travel at thumbnail size.
private fun widgetSized(cover: Bitmap): Bitmap {
    val scale = minOf(1f, 360f / cover.height)
    return if (scale >= 1f) cover
    else Bitmap.createScaledBitmap(cover, (cover.width * scale).toInt(), 360, true)
}

class SavepointWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SavepointWidget()
}

private fun openSavepoint(context: Context, logPlay: Boolean) =
    actionStartActivity(
        Intent(context, SavepointActivity::class.java)
            .setAction(if (logPlay) "log-play" else "open")
            .putExtra("log-play", logPlay)
            // A fresh task, so the play log opens
            // even when Savepoint was already on another screen.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )

@Composable
private fun PlayingNow(game: Game?, cover: Bitmap?) {
    val context = LocalContext.current
    val size = LocalSize.current
    Row(
        GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_glass))
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(openSavepoint(context, logPlay = false)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover != null) {
            val height = (size.height - 24.dp).coerceAtLeast(48.dp)
            Image(
                ImageProvider(cover),
                contentDescription = null,
                modifier =
                    GlanceModifier.size(height * (cover.width.toFloat() / cover.height), height)
                        .cornerRadius(10.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(GlanceModifier.width(14.dp))
        }
        Column(
            GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (game == null) "Nothing playing yet" else "Playing now",
                style = TextStyle(color = berry, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                game?.title ?: "Choose a game to play through, and it lives here.",
                style =
                    TextStyle(
                        color = plum,
                        fontSize = if (game == null) 13.sp else 16.sp,
                        fontWeight = if (game == null) FontWeight.Normal else FontWeight.Bold,
                    ),
                maxLines = 2,
            )
            if (game != null)
                Text(game.system, style = TextStyle(color = dusk, fontSize = 12.sp), maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))
            Box(
                GlanceModifier.background(ImageProvider(R.drawable.widget_pearl))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .clickable(openSavepoint(context, logPlay = game != null)),
            ) {
                Text(
                    if (game == null) "Open Savepoint" else "Log play",
                    style =
                        TextStyle(
                            color = ColorProvider(day = Color.White, night = Color.White),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
            }
        }
    }
}
