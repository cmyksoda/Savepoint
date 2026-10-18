package com.cmyksoda.savepoint

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RestoredLibrary(val games: List<Game>, val settings: Settings, val artwork: File)

object Backup {
    suspend fun export(
        context: Context,
        uri: Uri,
        games: List<Game>,
        settings: Settings,
        artwork: Artwork,
    ) =
        withContext(Dispatchers.IO) {
            val manifest =
                JSONObject()
                    .put("version", 3)
                    .put("games", JSONArray(games.map { JsonCodec.game(it) }))
                    .put("settings", JsonCodec.settings(settings))
            ZipOutputStream(context.contentResolver.openOutputStream(uri)!!).use { zip ->
                zip.putNextEntry(ZipEntry("library.json"))
                zip.write(manifest.toString(2).toByteArray())
                zip.closeEntry()
                val names =
                    games
                        .flatMap { listOf(it.cover, preferredCoverUrl(it.cover), it.sprite) }
                        .filter { it.isNotBlank() }
                        .map {
                            if (it.startsWith("local:")) it.removePrefix("local:")
                            else artwork.filename(it)
                        }
                        .distinct()
                for (name in names) {
                    val file = File(artwork.directory, name)
                    require(file.canonicalFile.parentFile == artwork.directory.canonicalFile)
                    if (!file.isFile) continue
                    zip.putNextEntry(ZipEntry("artwork/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

    suspend fun inspect(context: Context, uri: Uri): RestoredLibrary =
        withContext(Dispatchers.IO) {
            val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                var manifest: JSONObject? = null
                var total = 0L
                val names = mutableSetOf<String>()
                ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(names.add(entry.name)) { "Duplicate backup entry" }
                        require(
                            entry.name == "library.json" ||
                                entry.name.matches(Regex("artwork/[a-f0-9]{64}\\.image"))
                        ) {
                            "Unexpected backup entry"
                        }
                        val file = File(staging, entry.name)
                        file.parentFile!!.mkdirs()
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= 512L * 1024 * 1024) {
                                    "Backup exceeds the 512 MB import safety limit"
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                        if (entry.name == "library.json") manifest = JSONObject(file.readText())
                        zip.closeEntry()
                    }
                }
                val data = requireNotNull(manifest) { "Missing library data" }
                require(data.getInt("version") in 1..3) { "Unsupported backup version" }
                val array = data.getJSONArray("games")
                val games = (0 until array.length()).map { JsonCodec.game(array.getJSONObject(it)) }
                require(games.map { it.id }.distinct().size == games.size) {
                    "Duplicate game identifiers"
                }
                for (game in games) for (url in listOf(game.cover, game.sprite)) {
                    require(
                        url.isBlank() ||
                            url.startsWith("https://") ||
                            url.matches(Regex("local:[a-f0-9]{64}\\.image"))
                    ) {
                        "Invalid artwork reference"
                    }
                    if (url.startsWith("local:"))
                        require(File(staging, "artwork/${url.removePrefix("local:")}").isFile) {
                            "Missing selected artwork"
                        }
                }
                RestoredLibrary(
                    games,
                    JsonCodec.settings(data.getJSONObject("settings"))
                        .copy(reminders = false, eveningCheckIn = false, laterAt = 0),
                    staging,
                )
            } catch (e: Exception) {
                staging.deleteRecursively()
                throw e
            }
        }
}
