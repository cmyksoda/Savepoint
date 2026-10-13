package com.cmyksoda.savepoint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun fetchBytes(url: String): ByteArray {
    require(URI(url).scheme == "https") { "An HTTPS address is required" }
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.instanceFollowRedirects = false
    connection.setRequestProperty("User-Agent", "Savepoint/0.1 (personal game library)")
    return try {
        require(connection.responseCode == 200) {
            "Source unavailable (${connection.responseCode})"
        }
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
        withContext(Dispatchers.IO) {
            if (url.isBlank()) null else BitmapFactory.decodeFile(fileFor(url).path)
        }

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
        }

    suspend fun bitmap(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            val file = fileFor(url)
            if (!file.exists() && !url.startsWith("local:")) {
                val bytes = fetchBytes(url)
                require(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                    "Artwork is not an image"
                }
                val temporary = File.createTempFile("artwork-", ".tmp", directory)
                try {
                    temporary.writeBytes(bytes)
                    require(temporary.renameTo(file)) { "Could not save artwork" }
                } finally {
                    temporary.delete()
                }
            }
            BitmapFactory.decodeFile(file.path)
        }

    suspend fun select(uri: Uri): String =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            require(BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                "Choose an image file"
            }
            val name = filename(java.util.UUID.randomUUID().toString())
            File(directory, name).writeBytes(bytes)
            "local:$name"
        }
}

class Catalog(private val context: Context) {
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
        withContext(Dispatchers.IO) {
            val found = mutableListOf<Game>()
            var warning: String? = null
            if (endpoint.isNotBlank()) {
                try {
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    val data =
                        JSONObject(String(fetchBytes("${endpoint.trimEnd('/')}/search?q=$encoded")))
                    val results = data.getJSONArray("results")
                    for (index in 0 until results.length()) {
                        val item = results.getJSONObject(index)
                        if (system.isNotEmpty() && item.getString("system") != system) continue
                        found +=
                            Game(
                                title = item.getString("title"),
                                system = item.getString("system"),
                                cover = item.optString("cover"),
                                source = "igdb",
                                sourceId = item.getString("sourceId"),
                                release =
                                    if (item.isNull("release")) null else item.getLong("release"),
                            )
                    }
                } catch (e: Exception) {
                    warning =
                        "Broad catalog unavailable. GameTDB and manual entry are still available."
                }
            } else
                warning =
                    "GameTDB search is available. Configure the catalog service for PC and other Nintendo systems."
            val requested =
                if (system.isNotEmpty()) sources.filterKeys { it == system } else sources
            for ((platform, slug) in requested) {
                try {
                    val cache = File(context.cacheDir, "$slug-titles.txt")
                    if (!cache.exists())
                        cache.writeBytes(
                            fetchBytes("https://www.gametdb.com/${slug}tdb.txt?LANG=EN")
                        )
                    cache.useLines { lines ->
                        lines
                            .filter { it.contains(" = ") && !it.startsWith("TITLES") }
                            .forEach { line ->
                                val (id, title) = line.split(" = ", limit = 2)
                                val gameCube = slug == "wii" && id.startsWith("G")
                                if (
                                    platform == "GameCube" && !gameCube ||
                                        platform == "Wii" && gameCube
                                )
                                    return@forEach
                                if (!title.contains(query, ignoreCase = true)) return@forEach
                                val artworkSlug = if (slug == "3ds") "3ds" else slug
                                val extension = if (slug == "wii") "png" else "jpg"
                                found +=
                                    Game(
                                        title = title,
                                        system = platform,
                                        source = "gametdb",
                                        sourceId = id,
                                        cover =
                                            "https://art.gametdb.com/$artworkSlug/cover/US/$id.$extension",
                                    )
                            }
                    }
                } catch (e: Exception) {
                    warning =
                        "Some catalog sources are unavailable. You can still add a game manually."
                }
            }
            found
                .map(::preferPublisherCover)
                .distinctBy { Triple(it.title.lowercase(), it.system, it.source) }
                .sortedWith(
                    compareBy<Game> { !it.title.equals(query, true) }.thenBy { it.title.length }
                ) to warning
        }

    suspend fun shinySprite(pokemon: String, version: String): String =
        withContext(Dispatchers.IO) {
            val generation =
                when (version) {
                    "gold",
                    "silver",
                    "crystal" -> "generation-ii"
                    "ruby-sapphire",
                    "emerald",
                    "firered-leafgreen" -> "generation-iii"
                    "diamond-pearl",
                    "platinum",
                    "heartgold-soulsilver" -> "generation-iv"
                    "black-white" -> "generation-v"
                    "x-y",
                    "omegaruby-alphasapphire" -> "generation-vi"
                    "sun-moon",
                    "ultra-sun-ultra-moon",
                    "lets-go" -> "generation-vii"
                    else ->
                        throw IllegalArgumentException(
                            "Choose game-specific artwork manually for this game."
                        )
                }
            val slug = pokemon.trim().lowercase().replace(' ', '-')
            require(slug.matches(Regex("[a-z0-9-]+"))) { "Enter a Pokémon name or Pokédex number" }
            val data = JSONObject(String(fetchBytes("https://pokeapi.co/api/v2/pokemon/$slug")))
            val versionKey =
                when (version) {
                    "omegaruby-alphasapphire" -> "omega-ruby-alpha-sapphire"
                    "lets-go" -> "lets-go-pikachu-lets-go-eevee"
                    else -> version
                }
            val sprites =
                data
                    .getJSONObject("sprites")
                    .getJSONObject("versions")
                    .getJSONObject(generation)
                    .optJSONObject(versionKey)
                    ?: throw IllegalArgumentException(
                        "No matching game artwork is available. Choose a game-specific image manually."
                    )
            val url = sprites.optString("front_shiny")
            require(url.isNotBlank() && url != "null") {
                "No shiny artwork exists for this Pokémon in the selected game."
            }
            url
        }
}
