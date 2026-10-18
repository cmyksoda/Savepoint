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
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

fun fetchBytes(url: String): ByteArray {
    require(URI(url).scheme == "https") { "An HTTPS address is required" }
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.instanceFollowRedirects = false
    connection.setRequestProperty("User-Agent", "Savepoint/0.1 (personal game library)")
    return try {
        if (connection.responseCode != 200) throw SourceUnavailable(connection.responseCode)
        connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

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
    private val decoded =
        object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount + 1023) / 1024
        }

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
                ?: bitmap(url)?.let { original ->
                    var left = original.width
                    var top = original.height
                    var right = -1
                    var bottom = -1
                    val pixels = IntArray(original.width * original.height)
                    original.getPixels(
                        pixels,
                        0,
                        original.width,
                        0,
                        0,
                        original.width,
                        original.height,
                    )
                    for (y in 0 until original.height) for (x in 0 until original.width) {
                        if ((pixels[y * original.width + x] ushr 24) != 0) {
                            left = minOf(left, x)
                            top = minOf(top, y)
                            right = maxOf(right, x)
                            bottom = maxOf(bottom, y)
                        }
                    }
                    val trimmed =
                        if (right < left) original
                        else
                            Bitmap.createBitmap(
                                original,
                                left,
                                top,
                                right - left + 1,
                                bottom - top + 1,
                            )
                    decoded.put("sprite:$url", trimmed)
                    trimmed
                }
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
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 720) sample *= 2
        return BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
            ?.also { decoded.put(url, it) }
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
}

class Catalog(private val context: Context) {
    val pokemon = PokemonCatalog(context)
    private val sources =
        linkedMapOf(
            "Nintendo Switch" to "switch",
            "Wii U" to "wiiu",
            "Wii" to "wii",
            "Nintendo 3DS" to "3ds",
            "Nintendo DS" to "ds",
            "GameCube" to "wii",
        )

    suspend fun search(query: String, system: String, endpoint: String): Pair<List<Game>, String?> =
        searchPage(query, system, endpoint).let { it.games to it.warning }

    suspend fun searchPage(
        query: String,
        system: String,
        endpoint: String,
        offset: Int = 0,
        refreshGameTdb: Boolean = false,
    ): CatalogPage = searchUpdates(query, system, endpoint, offset, refreshGameTdb).last()

    fun searchUpdates(
        query: String,
        system: String,
        endpoint: String,
        offset: Int = 0,
        refreshGameTdb: Boolean = false,
        families: Set<String> = defaultPlatforms,
    ): Flow<CatalogPage> {
        val requested =
            if (offset > 0 || "nintendo" !in families) emptyMap()
            else if (system.isNotEmpty()) sources.filterKeys { it == system } else sources
        val pages = mutableListOf<Flow<CatalogPage>>()
        pages +=
            flow {
                    if (endpoint.isBlank()) {
                        emit(
                            CatalogPage(
                                emptyList(),
                                "GameTDB covers recent Nintendo consoles. Add a catalog service address in settings to search everything else.",
                                null,
                            )
                        )
                    } else {
                        val encoded = URLEncoder.encode(query, "UTF-8")
                        val data =
                            JSONObject(
                                String(
                                    fetchBytes(
                                        "${endpoint.trimEnd('/')}/search?q=$encoded&offset=$offset" +
                                            "&families=${families.sorted().joinToString(",")}"
                                    )
                                )
                            )
                        val nextOffset = data.optInt("nextOffset", -1).takeIf { it > offset }
                        val results = data.getJSONArray("results")
                        val games = buildList {
                            for (index in 0 until results.length()) {
                                val item = results.getJSONObject(index)
                                if (system.isNotEmpty() && item.getString("system") != system)
                                    continue
                                add(
                                    Game(
                                        title = item.getString("title"),
                                        system = item.getString("system"),
                                        cover = item.optString("cover"),
                                        source = "igdb",
                                        sourceId = item.getString("sourceId"),
                                        release =
                                            if (item.isNull("release")) null
                                            else item.getLong("release"),
                                    )
                                )
                            }
                        }
                        emit(CatalogPage(games, null, nextOffset))
                    }
                }
                .catch { error ->
                    if (offset > 0) throw error
                    emit(
                        CatalogPage(
                            emptyList(),
                            "Broad catalog unavailable. GameTDB and manual entry are still available.",
                            null,
                        )
                    )
                }
                .flowOn(Dispatchers.IO)
        for ((slug, platforms) in requested.entries.groupBy { it.value }) {
            var cachedGames = emptyList<Game>()
            pages +=
                flow {
                        val cache = File(context.cacheDir, "$slug-titles.txt")
                        val valid =
                            cache.isFile &&
                                cache.bufferedReader().use {
                                    it.readLine()?.startsWith("TITLES =") == true
                                }
                        fun matches(): List<Game> =
                            cache.useLines { lines ->
                                buildList {
                                    lines
                                        .filter { it.contains(" = ") && !it.startsWith("TITLES") }
                                        .forEach { line ->
                                            val (id, title) = line.split(" = ", limit = 2)
                                            val platform =
                                                if (slug == "wii") {
                                                    val name =
                                                        if (id.startsWith("G")) "GameCube"
                                                        else "Wii"
                                                    platforms.firstOrNull { it.key == name }?.key
                                                } else platforms.first().key
                                            if (
                                                platform == null || isVirtualConsoleId(platform, id)
                                            )
                                                return@forEach
                                            if (searchMatchScore(title, query) == null)
                                                return@forEach
                                            val extension = if (slug == "wii") "png" else "jpg"
                                            add(
                                                Game(
                                                    title = title,
                                                    system = platform,
                                                    source = "gametdb",
                                                    sourceId = id,
                                                    cover =
                                                        "https://art.gametdb.com/$slug/cover/US/$id.$extension",
                                                )
                                            )
                                        }
                                }
                            }
                        if (valid) {
                            cachedGames = matches()
                            emit(CatalogPage(cachedGames, null, null))
                        }
                        if (
                            !valid ||
                                refreshGameTdb ||
                                System.currentTimeMillis() - cache.lastModified() > 86400000L
                        ) {
                            val bytes = fetchBytes("https://www.gametdb.com/${slug}tdb.txt?LANG=EN")
                            require(String(bytes.take(100).toByteArray()).startsWith("TITLES =")) {
                                "GameTDB returned an invalid title list"
                            }
                            val temporary = File.createTempFile("titles-", ".tmp", context.cacheDir)
                            try {
                                temporary.writeBytes(bytes)
                                require(temporary.renameTo(cache))
                            } finally {
                                temporary.delete()
                            }
                            emit(CatalogPage(matches(), null, null))
                        }
                    }
                    .catch {
                        emit(
                            CatalogPage(
                                cachedGames,
                                "Some catalog sources are unavailable. You can still add a game manually.",
                                null,
                            )
                        )
                    }
                    .flowOn(Dispatchers.IO)
        }
        return mergeCatalogPages(pages)
            .map { page ->
                page.copy(
                    games =
                        page.games
                            .map(::preferPublisherCover)
                            .distinctBy { Triple(it.title.lowercase(), it.system, it.source) }
                            .sortedWith(
                                compareBy<Game> { !it.title.equals(query, true) }
                                    .thenBy { it.title.length }
                            )
                )
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun covers(
        title: String,
        system: String,
        provider: String,
        endpoint: String,
        refresh: Boolean = false,
    ): List<CoverCandidate> =
        withContext(Dispatchers.IO) {
            if (provider == "GameTDB") {
                val requestedSystem = catalogSystemName(system)
                val lookupSystem =
                    if (requestedSystem == "Nintendo Switch 2") "Nintendo Switch"
                    else requestedSystem
                val lookupTitle = gameTdbCoverTitle(title)
                val matches =
                    searchPage(lookupTitle, lookupSystem, "", refreshGameTdb = refresh)
                        .games
                        .filter {
                            it.source == "gametdb" &&
                                catalogTitleKey(it.title) == catalogTitleKey(lookupTitle)
                        }
                return@withContext kotlinx.coroutines.coroutineScope {
                    matches
                        .flatMap { game ->
                            listOf("US", "EN", "JA").map { region ->
                                async {
                                    val slug = sources[lookupSystem] ?: return@async null
                                    val extension = if (slug == "wii") "png" else "jpg"
                                    val base = "https://art.gametdb.com/$slug"
                                    val url =
                                        listOf(
                                                "$base/coverHQ/$region/${game.sourceId}.$extension",
                                                "$base/cover/$region/${game.sourceId}.$extension",
                                            )
                                            .firstOrNull {
                                                runCatching { imageAvailable(it) }
                                                    .getOrDefault(false)
                                            } ?: return@async null
                                    val label =
                                        when (region) {
                                            "EN" -> "Europe"
                                            "JA" -> "Japan"
                                            else -> "US"
                                        }
                                    CoverCandidate(
                                        url,
                                        "GameTDB",
                                        if (requestedSystem == "Nintendo Switch 2")
                                            "$label · Switch cover"
                                        else label,
                                    )
                                }
                            }
                        }
                        .map { it.await() }
                        .filterNotNull()
                        .distinctBy { it.url }
                }
            }
            require(endpoint.isNotBlank()) {
                "Set your catalog service address in settings to search $provider covers."
            }
            fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")
            val data =
                JSONObject(
                        String(
                            fetchBytes(
                                "${endpoint.trimEnd('/')}/covers?q=${encoded(title)}&system=${encoded(catalogSystemName(system))}&provider=${encoded(provider)}"
                            )
                        )
                    )
                    .getJSONArray("results")
            (0 until data.length())
                .map { index ->
                    val item = data.getJSONObject(index)
                    CoverCandidate(
                        item.getString("url"),
                        item.getString("provider"),
                        item.getString("label"),
                    )
                }
                .filter { candidate ->
                    provider != "IGDB" || catalogTitleKey(candidate.label) == catalogTitleKey(title)
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
