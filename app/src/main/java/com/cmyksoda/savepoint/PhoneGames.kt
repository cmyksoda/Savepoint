package com.cmyksoda.savepoint

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhoneGame(val packageName: String, val title: String, val game: Boolean = true)

/**
 * Apps on this phone that open from the launcher;
 * Android tells only this phone, and the list never leaves it.
 */
fun installedApps(context: Context): List<PhoneGame> {
    val packages = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packages
        .queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { PhoneGame(it.packageName, packages.getApplicationLabel(it).toString(), it.isGame()) }
        .sortedBy { it.title.lowercase() }
}

// Older games still mark themselves with the retired isGame flag instead of a category.
@Suppress("DEPRECATION")
private fun ApplicationInfo.isGame() =
    category == ApplicationInfo.CATEGORY_GAME || (flags and ApplicationInfo.FLAG_IS_GAME) != 0

/**
 * Apps on this phone that aren't in the library yet: games when nothing is typed, and any app whose
 * name matches once a title is typed, since some games, like Pokémon Sleep, aren't filed as games.
 */
fun phoneGameMatches(apps: List<PhoneGame>, library: List<Game>, query: String): List<PhoneGame> {
    val titles = library.map { catalogTitleKey(it.title) }.toSet()
    val packages = library.filter { it.source == "phone" }.map { it.sourceId }.toSet()
    return apps.filter {
        it.packageName !in packages &&
            catalogTitleKey(it.title) !in titles &&
            if (query.isBlank()) it.game else searchMatchScore(it.title, query) != null
    }
}

/** The installed app a library game was added from, or else one with the same name. */
fun phoneAppFor(game: Game, apps: List<PhoneGame>): PhoneGame? {
    if (game.source == "phone")
        apps.firstOrNull { it.packageName == game.sourceId }?.let { return it }
    val title = catalogTitleKey(game.title)
    return apps.firstOrNull { catalogTitleKey(it.title) == title }
}

/** The game's own app icon as a cover choice, when that app is installed here. */
suspend fun phoneCovers(context: Context, artwork: Artwork, game: Game): List<CoverCandidate> =
    withContext(Dispatchers.IO) {
        val app = phoneAppFor(game, installedApps(context)) ?: return@withContext emptyList()
        listOfNotNull(
            artwork.saveAppIcon(app.packageName)?.let {
                CoverCandidate(it, "This phone", "App icon")
            }
        )
    }

/**
 * The app's icon as a full square, the same shape as App Store artwork,
 * so it can stand on a shelf.
 */
fun appIconBitmap(context: Context, packageName: String, size: Int = 512): Bitmap? {
    val icon =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return null
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    if (icon is AdaptiveIconDrawable) {
        // Adaptive layers are 108 dp with the visible icon in the middle 72 dp,
        // so they're drawn half again as large.
        val bleed = size / 4
        listOfNotNull(icon.background, icon.foreground).forEach {
            it.setBounds(-bleed, -bleed, size + bleed, size + bleed)
            it.draw(canvas)
        }
    } else {
        canvas.drawColor(Color.WHITE)
        icon.setBounds(0, 0, size, size)
        icon.draw(canvas)
    }
    return bitmap
}
