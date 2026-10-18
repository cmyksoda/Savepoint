package com.cmyksoda.savepoint

import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

const val launchBoxRoot = "https://gamesdb.launchbox-app.com"

private val launchBoxSystems =
    mapOf(
        "Nintendo Entertainment System" to "NES",
        "Nintendo Famicom Disk System" to "Famicom Disk System",
        "Super Nintendo Entertainment System" to "SNES",
        "Nintendo 64" to "Nintendo 64",
        "Nintendo GameCube" to "GameCube",
        "Nintendo Wii" to "Wii",
        "Nintendo Wii U" to "Wii U",
        "Nintendo Switch" to "Nintendo Switch",
        "Nintendo Switch 2" to "Nintendo Switch 2",
        "Nintendo Game Boy" to "Game Boy",
        "Nintendo Game Boy Color" to "Game Boy Color",
        "Nintendo Game Boy Advance" to "Game Boy Advance",
        "Nintendo DS" to "Nintendo DS",
        "Nintendo DSi" to "Nintendo DSi",
        "Nintendo 3DS" to "Nintendo 3DS",
        "Nintendo Virtual Boy" to "Virtual Boy",
        "Nintendo Pokemon Mini" to "Pokémon mini",
        "Windows" to "PC",
        "Linux" to "PC",
        "Mac OS" to "PC",
        "Sony Playstation" to "PlayStation",
        "Sony Playstation 2" to "PlayStation 2",
        "Sony Playstation 3" to "PlayStation 3",
        "Sony Playstation 4" to "PlayStation 4",
        "Sony Playstation 5" to "PlayStation 5",
        "Sony PSP" to "PSP",
        "Sony Playstation Vita" to "PlayStation Vita",
        "Microsoft Xbox" to "Xbox",
        "Microsoft Xbox 360" to "Xbox 360",
        "Microsoft Xbox One" to "Xbox One",
        "Microsoft Xbox Series X/S" to "Xbox Series X|S",
        "Sega Master System" to "Master System",
        "Sega Genesis" to "Genesis",
        "Sega CD" to "Sega CD",
        "Sega Saturn" to "Sega Saturn",
        "Sega Dreamcast" to "Dreamcast",
        "Sega Game Gear" to "Game Gear",
        "Apple iOS" to "iOS",
        "Android" to "Android",
        "Atari 2600" to "Atari 2600",
        "Atari 7800" to "Atari 7800",
        "Atari Lynx" to "Atari Lynx",
        "NEC TurboGrafx-16" to "TurboGrafx-16",
        "SNK Neo Geo AES" to "Neo Geo",
        "SNK Neo Geo Pocket Color" to "Neo Geo Pocket Color",
        "Bandai WonderSwan" to "WonderSwan",
        "Arcade" to "Arcade",
    )

val mobileSystems = setOf("iOS", "Android")

fun coverProviders(system: String): List<String> = buildList {
    add("LaunchBox")
    if (system == "PC") add("Steam")
    if (system in mobileSystems) add("App Store")
}

internal fun htmlText(html: String): String =
    html
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("&#([xX][0-9a-fA-F]+|\\d+);")) { match ->
            val code = match.groupValues[1]
            String(
                Character.toChars(
                    if (code[0] == 'x' || code[0] == 'X') code.drop(1).toInt(16) else code.toInt()
                )
            )
        }
        .replace(Regex("&(amp|quot|apos|lt|gt|nbsp);")) {
            when (it.groupValues[1]) {
                "amp" -> "&"
                "quot" -> "\""
                "apos" -> "'"
                "lt" -> "<"
                "gt" -> ">"
                else -> " "
            }
        }
        .trim()

internal fun pathSegment(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

private fun startOfDay(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** One game per card on LaunchBox's results page; [Game.sourceId] keeps its address. */
fun parseLaunchBoxResults(html: String): List<Game> {
    val links = Regex("href=\"/games/details/(\\d+-[^\"<>]*)\"").findAll(html).toList()
    val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    return links
        .mapIndexedNotNull { index, link ->
            val card = html.substring(link.range.first, links.getOrNull(index + 1)?.range?.first ?: html.length)
            val title = Regex("<h3[^>]*>([\\s\\S]*?)</h3>").find(card) ?: return@mapIndexedNotNull null
            val platform =
                Regex("<p[^>]*>([\\s\\S]*?)</p>").find(card, title.range.last)
                    ?: return@mapIndexedNotNull null
            val system = launchBoxSystems[htmlText(platform.groupValues[1])] ?: return@mapIndexedNotNull null
            val released =
                Regex("class=\"releaseDate\"[\\s\\S]*?<h5[^>]*>([\\s\\S]*?)</h5>").find(card)?.let {
                    runCatching { startOfDay(LocalDate.parse(htmlText(it.groupValues[1]), dateFormat)) }
                        .getOrNull()
                }
            Game(
                title = htmlText(title.groupValues[1]),
                system = system,
                source = "launchbox",
                sourceId = link.groupValues[1],
                release = released,
            )
        }
        .distinctBy { it.sourceId }
}

/** Front covers from a LaunchBox images page: official ones by region, then reconstructed, then fan art. */
fun parseLaunchBoxCovers(html: String): List<CoverCandidate> =
    Regex("<a\\s[^>]*href=\"(https://images\\.launchbox-app\\.com/[^\"<>]+)\"[^>]*data-title=\"([^\"]+)\"")
        .findAll(html)
        .mapNotNull { match ->
            val url = match.groupValues[1]
            if (runCatching { URI(url).host }.getOrNull() != "images.launchbox-app.com") return@mapNotNull null
            val label = htmlText(match.groupValues[2])
            val (rank, kind) =
                when {
                    " - Fanart - Box - Front Image" in label -> 3 to "fan art"
                    " - Box - Front - Reconstructed Image" in label -> 2 to "reconstructed"
                    " - Box - Front Image" in label -> 1 to null
                    else -> return@mapNotNull null
                }
            val region = Regex("\\(([^()]*)\\)$").find(label)?.groupValues?.get(1)
            val order = if (rank == 1 && region in setOf(null, "North America", "United States")) 0 else rank
            order to CoverCandidate(url, "LaunchBox", listOfNotNull(kind, region).joinToString(" · ").ifBlank { "front cover" })
        }
        .distinctBy { it.second.url }
        .sortedBy { it.first }
        .map { it.second }
        .toList()

private val steamExtras = Regex("(?i)\\b(soundtrack|ost|artbook|art book|wallpapers?)\\b")

fun steamSearchUrl(query: String) =
    "https://store.steampowered.com/api/storesearch/?term=${pathSegment(query)}&l=english&cc=US"

fun steamArt(id: String, file: String = "library_600x900_2x.jpg") =
    "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/$file"

/** Steam store matches with their library art; soundtracks and artbooks are not games. */
fun parseSteamResults(json: String): List<Game> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length())
        .map { items.getJSONObject(it) }
        .filter { it.optString("type") == "app" && !steamExtras.containsMatchIn(it.optString("name")) }
        .map {
            val id = it.get("id").toString()
            Game(title = it.getString("name"), system = "PC", source = "steam", sourceId = id, cover = steamArt(id))
        }
}

fun appStoreSearchUrl(query: String) =
    "https://itunes.apple.com/search?term=${pathSegment(query)}&entity=software&limit=25"

/** App Store games with their icons; [system] is the phone the player chose, Android unless they say iOS. */
fun parseAppStoreResults(json: String, system: String): List<Game> {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    return (0 until results.length())
        .map { results.getJSONObject(it) }
        .filter { it.optString("primaryGenreName") == "Games" }
        .map {
            Game(
                title = it.getString("trackName"),
                system = system,
                source = "appstore",
                sourceId = it.get("trackId").toString(),
                cover = it.optString("artworkUrl512"),
                release =
                    runCatching { startOfDay(OffsetDateTime.parse(it.getString("releaseDate")).toLocalDate()) }
                        .getOrNull(),
            )
        }
}
