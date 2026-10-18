package com.cmyksoda.savepoint

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable

data class PhoneGame(val packageName: String, val title: String)

/** Installed apps their makers mark as games; Android tells only this phone, and the list never leaves it. */
fun installedGames(context: Context): List<PhoneGame> {
    val packages = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packages
        .queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.isGame() && it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { PhoneGame(it.packageName, packages.getApplicationLabel(it).toString()) }
        .sortedBy { it.title.lowercase() }
}

// Older games still mark themselves with the retired isGame flag instead of a category.
@Suppress("DEPRECATION")
private fun ApplicationInfo.isGame() =
    category == ApplicationInfo.CATEGORY_GAME || (flags and ApplicationInfo.FLAG_IS_GAME) != 0

/** Games on this phone that aren't in the library yet, narrowed as the search box is typed in. */
fun phoneGameMatches(games: List<PhoneGame>, library: List<Game>, query: String): List<PhoneGame> {
    val titles = library.map { catalogTitleKey(it.title) }.toSet()
    val packages = library.filter { it.source == "phone" }.map { it.sourceId }.toSet()
    return games.filter {
        it.packageName !in packages &&
            catalogTitleKey(it.title) !in titles &&
            (query.isBlank() || searchMatchScore(it.title, query) != null)
    }
}

/** The app's icon as a full square, the same shape as App Store artwork, so it can stand on a shelf. */
fun appIconBitmap(context: Context, packageName: String, size: Int = 512): Bitmap? {
    val icon = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return null
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (icon is AdaptiveIconDrawable) {
        // Adaptive layers are 108 dp with the visible icon in the middle 72 dp, so they're drawn half again as large.
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
