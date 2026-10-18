package com.cmyksoda.savepoint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

fun imageAvailable(url: String): Boolean {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "HEAD"
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    connection.setRequestProperty("User-Agent", "Savepoint/0.3 (personal game library)")
    return try {
        connection.responseCode == 200 && connection.contentType?.startsWith("image/") == true
    } finally {
        connection.disconnect()
    }
}

class SourceUnavailable(val status: Int) : IOException("Source unavailable ($status)")

fun fetchBytes(url: String, redirects: Int = 3): ByteArray {
    val address = URI(url)
    require(address.scheme == "https") { "An HTTPS address is required" }
    val connection = address.toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.instanceFollowRedirects = false
    connection.setRequestProperty("User-Agent", "Savepoint/0.1 (personal game library)")
    val moved =
        try {
            val status = connection.responseCode
            if (status == 200) return connection.inputStream.use { it.readBytes() }
            // LaunchBox moves bare and renamed game addresses to their current page; follow only within the same site.
            connection.getHeaderField("Location")
                ?.let { runCatching { address.resolve(it) }.getOrNull() }
                ?.takeIf { status in 300..399 && redirects > 0 && it.scheme == "https" && it.host == address.host }
                ?: throw SourceUnavailable(status)
        } finally {
            connection.disconnect()
        }
    return fetchBytes(moved.toString(), redirects - 1)
}

// Covers saved from GameTDB before 0.6 still load their sharper HQ variant.
fun preferredCoverUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    return if (
        uri.scheme == "https" &&
            uri.host == "art.gametdb.com" &&
            uri.path.matches(
                Regex("/(switch|wiiu|wii|3ds|ds)/cover/[A-Z]{2}/[A-Za-z0-9]+\\.(jpg|png)")
            )
    )
        url.replace("/cover/", "/coverHQ/")
    else url
}

class Artwork(private val context: Context) {
    val directory = File(context.filesDir, "artwork").apply { mkdirs() }
    // A quarter of the app's memory, as image libraries like Coil use, keeps a long backlog decoded.
    private val decoded =
        object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 4 / 1024).toInt()) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount + 1023) / 1024
        }

    // No screen shows a cover taller than 244 dp, so larger decodes only push other covers out.
    private val coverPixels = (256 * context.resources.displayMetrics.density).roundToInt()

    private val ratios = java.util.concurrent.ConcurrentHashMap<String, Float>()

    fun cachedAspectRatio(url: String): Float? = ratios[preferredCoverUrl(url)] ?: ratios[url]

    suspend fun prepareLibraryArtwork(games: List<Game>) =
        withContext(Dispatchers.IO) {
            for (game in games) {
                if (game.cover.isBlank()) continue
                for (url in listOf(preferredCoverUrl(game.cover), game.cover).distinct()) {
                    val file = fileFor(url)
                    if (!file.isFile) continue
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    if (bounds.outWidth > 0 && bounds.outHeight > 0)
                        ratios[url] = bounds.outWidth.toFloat() / bounds.outHeight
                }
            }
            for (game in games.filter { it.place == Place.ACTIVE }) {
                if (game.shelf == Shelf.HUNTS) {
                    if (game.sprite.isNotBlank() && fileFor(game.sprite).isFile)
                        spriteBitmap(game.sprite)
                } else if (game.cover.isNotBlank()) {
                    cachedCoverBitmap(game.cover)
                }
            }
        }

    suspend fun cachedCoverBitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) null
            else {
                memoryBitmap(url)
                    ?: listOf(preferredCoverUrl(url), url).distinct().firstNotNullOfOrNull {
                        candidate ->
                        if (fileFor(candidate).isFile) decode(candidate) else null
                    }
            }
        }

    fun memoryBitmap(url: String, sprite: Boolean = false): Bitmap? =
        if (sprite) decoded.get("sprite:$url")
        else decoded.get(preferredCoverUrl(url)) ?: decoded.get(url)

    suspend fun spriteBitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            memoryBitmap(url, true)
                ?: bitmap(url)?.let { trimmed(it) }?.also { decoded.put("sprite:$url", it) }
        }

    // Search lists show many sprites the player may never pick, so they use the expiring cache.
    suspend fun previewSprite(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            decoded.get("sprite:$url") ?: previewBitmap(url)?.let { trimmed(it) }?.also { decoded.put("sprite:$url", it) }
        }

    private fun trimmed(original: Bitmap): Bitmap {
        var left = original.width
        var top = original.height
        var right = -1
        var bottom = -1
        val pixels = IntArray(original.width * original.height)
        original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        for (y in 0 until original.height) for (x in 0 until original.width) {
            if ((pixels[y * original.width + x] ushr 24) != 0) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        return if (right < left) original
        else Bitmap.createBitmap(original, left, top, right - left + 1, bottom - top + 1)
    }

    private fun decode(url: String): Bitmap? {
        memoryBitmap(url)?.let {
            return it
        }
        val file = fileFor(url)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0)
            ratios[url] = bounds.outWidth.toFloat() / bounds.outHeight
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= coverPixels) sample *= 2
        val sampled =
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
        val scale = coverPixels.toFloat() / maxOf(sampled.width, sampled.height)
        val fitted =
            if (scale >= 1f) sampled
            else
                Bitmap.createScaledBitmap(
                    sampled,
                    (sampled.width * scale).roundToInt().coerceAtLeast(1),
                    (sampled.height * scale).roundToInt().coerceAtLeast(1),
                    true,
                )
        return fitted.also { decoded.put(url, it) }
    }

    fun filename(url: String) =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") {
            "%02x".format(it)
        } + ".image"

    private fun fileFor(url: String): File {
        val file =
            if (url.startsWith("local:")) File(directory, url.removePrefix("local:"))
            else File(directory, filename(url))
        require(file.canonicalFile.parentFile == directory.canonicalFile)
        return file
    }

    suspend fun cachedBitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) { if (url.isBlank()) null else decode(url) }

    suspend fun installBundledCovers() =
        withContext(Dispatchers.IO) {
            for (cover in bundledCovers) {
                for (url in listOf(cover.url, preferredCoverUrl(cover.url)).distinct()) {
                    val file = fileFor(url)
                    if (file.exists()) continue
                    val temporary = File.createTempFile("bundled-cover-", ".tmp", directory)
                    try {
                        context.assets.open("covers/${cover.asset}").use { input ->
                            temporary.outputStream().use { input.copyTo(it) }
                        }
                        require(temporary.renameTo(file)) { "Could not save bundled cover" }
                    } finally {
                        temporary.delete()
                    }
                }
            }
            for (cover in bundledCovers) decode(preferredCoverUrl(cover.url))
        }

    suspend fun bitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            val file = fileFor(url)
            if (!file.exists() && !url.startsWith("local:")) {
                val bytes = fetchBytes(url)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Artwork is not an image" }
                val temporary = File.createTempFile("artwork-", ".tmp", directory)
                try {
                    temporary.writeBytes(bytes)
                    require(temporary.renameTo(file)) { "Could not save artwork" }
                } finally {
                    temporary.delete()
                }
            }
            decode(url)
        }

    private val previews = File(context.cacheDir, "previews").apply { mkdirs() }

    // Covers seen while searching live in the cache and expire, so browsing doesn't fill the phone.
    suspend fun previewBitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            cachedCoverBitmap(url)?.let { return@withContext it }
            decoded.get("preview:$url")?.let { return@withContext it }
            val file = File(previews, filename(url))
            if (!file.isFile) {
                val temporary = File.createTempFile("preview-", ".tmp", previews)
                try {
                    temporary.writeBytes(fetchBytes(url))
                    temporary.renameTo(file)
                } finally {
                    temporary.delete()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            ratios[url] = bounds.outWidth.toFloat() / bounds.outHeight
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 240) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.also { decoded.put("preview:$url", it) }
        }

    suspend fun forgetOldPreviews() =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 2 * 86_400_000L
            previews.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }

    suspend fun select(uri: Uri): String =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose an image file" }
            val name = filename(java.util.UUID.randomUUID().toString())
            File(directory, name).writeBytes(bytes)
            "local:$name"
        }

    // Saved like a chosen picture, so the cover survives backups and the game being uninstalled.
    suspend fun saveAppIcon(packageName: String): String? =
        withContext(Dispatchers.IO) {
            val icon = appIconBitmap(context, packageName) ?: return@withContext null
            val name = filename("app:$packageName")
            File(directory, name).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            "local:$name"
        }
}

class Catalog(private val context: Context) {
    val pokemon = PokemonCatalog(context)
    // Result thumbnails and the cover screen read the same LaunchBox pages.
    private val coverPages = LruCache<String, List<CoverCandidate>>(200)

    private fun launchBoxSearch(query: String) = parseLaunchBoxResults(String(fetchBytes(launchBoxSearchUrl(query))))

    private fun steamSearch(query: String) = parseSteamResults(String(fetchBytes(steamSearchUrl(query))))

    private fun appStoreSearch(query: String, system: String) =
        parseAppStoreResults(String(fetchBytes(appStoreSearchUrl(query))), system)

    fun searchUpdates(
        query: String,
        system: String,
        families: Set<String> = defaultPlatforms,
    ): Flow<CatalogPage> {
        // Each source's own order breaks ties after match quality, so its best guesses stay on top.
        val rank = java.util.concurrent.ConcurrentHashMap<String, Int>()
        fun wanted(game: Game) =
            (system.isEmpty() || game.system == system) &&
                systemFamily(game.system).let { it == null || it in families }
        val sources = buildList<Pair<String, () -> List<Game>>> {
            add("LaunchBox" to { launchBoxSearch(query) })
            // Store searches pad their answers with loosely related titles, so keep only real matches.
            if ("pc" in families && (system.isEmpty() || system == "PC"))
                add("Steam" to { steamSearch(query).filter { searchMatchScore(it.title, query) != null } })
            if ("mobile" in families && (system.isEmpty() || system in mobileSystems))
                add("The App Store" to {
                    appStoreSearch(query, system.ifEmpty { "Android" }).filter { searchMatchScore(it.title, query) != null }
                })
        }
        val pages =
            sources.map { (name, search) ->
                flow {
                        val games = search().filter(::wanted)
                        games.forEachIndexed { index, game -> rank[game.source + game.sourceId] = index }
                        emit(CatalogPage(games, null))
                    }
                    .catch {
                        emit(CatalogPage(emptyList(), "$name is unavailable right now, so some games may be missing."))
                    }
                    .flowOn(Dispatchers.IO)
            }
        return mergeCatalogPages(pages)
            .map { page ->
                page.copy(
                    games =
                        page.games
                            .distinctBy { Triple(catalogTitleKey(it.title), it.system, it.source) }
                            .sortedWith(
                                compareBy<Game> { !it.title.equals(query, true) }
                                    .thenBy { searchMatchScore(it.title, query) ?: Int.MAX_VALUE }
                                    // Stray database entries rarely carry a release date; real games do.
                                    .thenBy { it.release == null }
                                    .thenBy { rank[it.source + it.sourceId] ?: Int.MAX_VALUE }
                            )
                )
            }
            .flowOn(Dispatchers.IO)
    }

    private suspend fun launchBoxCovers(slug: String, refresh: Boolean = false): List<CoverCandidate> =
        coverPages.get(slug)?.takeUnless { refresh }
            ?: withContext(Dispatchers.IO) {
                    parseLaunchBoxCovers(String(fetchBytes("$launchBoxRoot/games/images/$slug")))
                }
                .also { coverPages.put(slug, it) }

    suspend fun resultCover(game: Game): String? =
        game.cover.ifBlank { null }
            ?: if (game.source == "launchbox") launchBoxCovers(game.sourceId).firstOrNull()?.url else null

    suspend fun covers(game: Game, provider: String, refresh: Boolean = false): List<CoverCandidate> =
        withContext(Dispatchers.IO) {
            val key = catalogTitleKey(game.title)
            when (provider) {
                "LaunchBox" -> {
                    val slug =
                        if (game.source == "launchbox") game.sourceId
                        else {
                            val found =
                                launchBoxSearch(game.title).filter {
                                    it.system == catalogSystemName(game.system)
                                }
                            (found.firstOrNull { catalogTitleKey(it.title) == key }
                                    ?: found.firstOrNull { catalogTitleKey(it.title).startsWith(key) })
                                ?.sourceId ?: return@withContext emptyList()
                        }
                    launchBoxCovers(slug, refresh)
                }
                "Steam" -> {
                    val id =
                        if (game.source == "steam") game.sourceId
                        else
                            steamSearch(game.title).firstOrNull { catalogTitleKey(it.title) == key }?.sourceId
                                ?: return@withContext emptyList()
                    listOf("library_600x900_2x.jpg", "library_600x900.jpg")
                        .map { steamArt(id, it) }
                        .firstOrNull { runCatching { imageAvailable(it) }.getOrDefault(false) }
                        ?.let { listOf(CoverCandidate(it, "Steam", "Library art")) } ?: emptyList()
                }
                "App Store" ->
                    appStoreSearch(game.title, game.system)
                        .filter { catalogTitleKey(it.title) == key && it.cover.isNotBlank() }
                        .map { CoverCandidate(it.cover, "App Store", "App icon") }
                        .distinctBy { it.url }
                else -> emptyList()
            }
        }

    suspend fun shinySprite(pokemon: String, version: String): String =
        withContext(Dispatchers.IO) {
            val choice = this@Catalog.pokemon.find(pokemon.trim().lowercase().replace(' ', '-'))
            serebiiShinyCandidates(choice, version).firstOrNull { url ->
                runCatching { imageAvailable(url) }.getOrDefault(false)
            }
                ?: throw IllegalArgumentException(
                    "No Serebii shiny artwork was found for this form and game. You can choose an image."
                )
        }
}
