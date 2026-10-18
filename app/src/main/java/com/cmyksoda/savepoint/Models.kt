package com.cmyksoda.savepoint

import java.util.UUID

enum class Shelf(val label: String) {
    PLAY_THROUGH("play through"),
    KEEP_PLAYING("keep playing"),
    EASY("take it easy"),
    FRIENDS("with friends"),
    HUNTS("shiny hunts"),
}

enum class Place {
    BACKLOG,
    ACTIVE,
    PAUSED,
    FINISHED,
}

fun mergeSettings(current: Settings, previous: Settings, changed: Settings) =
    current.copy(
        theme = if (changed.theme != previous.theme) changed.theme else current.theme,
        sort = if (changed.sort != previous.sort) changed.sort else current.sort,
        systemFilter =
            if (changed.systemFilter != previous.systemFilter) changed.systemFilter
            else current.systemFilter,
        collectionFilter =
            if (changed.collectionFilter != previous.collectionFilter) changed.collectionFilter
            else current.collectionFilter,
        collectionNames =
            if (changed.collectionNames != previous.collectionNames) changed.collectionNames
            else current.collectionNames,
        gridViews =
            (current.gridViews - (previous.gridViews - changed.gridViews)) +
                (changed.gridViews - previous.gridViews),
        catalogUrl =
            if (changed.catalogUrl != previous.catalogUrl) changed.catalogUrl
            else current.catalogUrl,
        reminders =
            if (changed.reminders != previous.reminders) changed.reminders else current.reminders,
        days = if (changed.days != previous.days) changed.days else current.days,
        startMinute =
            if (changed.startMinute != previous.startMinute) changed.startMinute
            else current.startMinute,
        endMinute =
            if (changed.endMinute != previous.endMinute) changed.endMinute else current.endMinute,
        skipDate =
            if (changed.skipDate != previous.skipDate) changed.skipDate else current.skipDate,
        laterAt = if (changed.laterAt != previous.laterAt) changed.laterAt else current.laterAt,
        logReminder =
            if (changed.logReminder != previous.logReminder) changed.logReminder
            else current.logReminder,
        shelfStyle =
            if (changed.shelfStyle != previous.shelfStyle) changed.shelfStyle
            else current.shelfStyle,
        gameCases =
            if (changed.gameCases != previous.gameCases) changed.gameCases else current.gameCases,
        platforms =
            if (changed.platforms != previous.platforms) changed.platforms else current.platforms,
        onboarded =
            if (changed.onboarded != previous.onboarded) changed.onboarded else current.onboarded,
    )

data class SystemFamily(val key: String, val label: String, val systems: List<String>)

val systemFamilies =
    listOf(
        SystemFamily("pc", "PC", listOf("PC")),
        SystemFamily(
            "nintendo",
            "Nintendo",
            listOf(
                "NES",
                "Famicom Disk System",
                "SNES",
                "Nintendo 64",
                "GameCube",
                "Wii",
                "Wii U",
                "Nintendo Switch",
                "Nintendo Switch 2",
                "Game Boy",
                "Game Boy Color",
                "Game Boy Advance",
                "Nintendo DS",
                "Nintendo DSi",
                "Nintendo 3DS",
                "Virtual Boy",
                "Pokémon mini",
            ),
        ),
        SystemFamily(
            "playstation",
            "PlayStation",
            listOf(
                "PlayStation",
                "PlayStation 2",
                "PlayStation 3",
                "PlayStation 4",
                "PlayStation 5",
                "PSP",
                "PlayStation Vita",
            ),
        ),
        SystemFamily("xbox", "Xbox", listOf("Xbox", "Xbox 360", "Xbox One", "Xbox Series X|S")),
        SystemFamily(
            "sega",
            "Sega",
            listOf("Master System", "Genesis", "Sega CD", "Sega Saturn", "Dreamcast", "Game Gear"),
        ),
        SystemFamily("mobile", "phone & tablet", listOf("iOS", "Android")),
        SystemFamily(
            "retro",
            "other retro",
            listOf(
                "Atari 2600",
                "Atari 7800",
                "Atari Lynx",
                "TurboGrafx-16",
                "Neo Geo",
                "Neo Geo Pocket Color",
                "WonderSwan",
                "Arcade",
            ),
        ),
    )

val defaultPlatforms = setOf("pc", "nintendo")

val systems = systemFamilies.flatMap { it.systems } + "Other"

fun systemFamily(system: String): String? =
    systemFamilies.firstOrNull { system in it.systems }?.key

/** The systems someone plays on, plus any they already used, so an existing game never loses its system. */
fun playableSystems(platforms: Set<String>, keep: Collection<String> = emptyList()): List<String> =
    systems.filter { it == "Other" || systemFamily(it) in platforms || it in keep }

enum class Feeling(val label: String) {
    DIDNT_LIKE("didn't like"),
    LIKED("liked"),
    LOVED("loved"),
}

data class Playthrough(
    val id: String = UUID.randomUUID().toString(),
    val finishedAt: Long? = System.currentTimeMillis(),
    val feeling: Feeling? = null,
    val thoughts: String = "",
    val hours: Double? = null,
)

data class Game(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val system: String = "PC",
    val shelf: Shelf = Shelf.PLAY_THROUGH,
    val place: Place = Place.BACKLOG,
    val position: Int = 0,
    val added: Long = System.currentTimeMillis(),
    val release: Long? = null,
    val cover: String = "",
    val coverProvider: String = "",
    val source: String = "manual",
    val sourceId: String = "",
    val notes: String = "",
    val pauseNote: String = "",
    val collection: String = "",
    val collectionOrder: Int = 0,
    val pokemon: String = "",
    val pokemonVersion: String = "heartgold-soulsilver",
    val sprite: String = "",
    val method: String = "Random encounters",
    val encounters: Long = 0,
    val shinyCharm: Boolean = false,
    val huntConfig: HuntConfig = HuntConfig(),
    val manualOdds: Int? = null,
    val playthroughs: List<Playthrough> = emptyList(),
    val hasPlayed: Boolean = false,
    val replayCount: Int? = null,
    val dailyPlays: List<DailyPlay> = emptyList(),
)

val Game.inPlayLog: Boolean
    get() = hasPlayed || playthroughs.isNotEmpty() || place == Place.FINISHED

val Game.recordedHours: Double?
    get() = playthroughs.mapNotNull { it.hours }.takeIf { it.isNotEmpty() }?.sum()

object LibraryRules {
    fun move(games: List<Game>, id: String, place: Place, first: Boolean = false): List<Game> {
        val moving = games.first { it.id == id }
        val peers =
            games
                .filter { it.shelf == moving.shelf && it.place == place && it.id != id }
                .sortedBy { it.position }
        val ordered = if (first) listOf(moving) + peers else peers + moving
        val positions = ordered.mapIndexed { index, game -> game.id to index }.toMap()
        return games.map {
            when {
                it.id == id ->
                    it.copy(
                        place = place,
                        position = positions.getValue(id),
                        hasPlayed =
                            it.hasPlayed || it.place == Place.FINISHED || place == Place.FINISHED,
                    )
                it.id in positions -> it.copy(position = positions.getValue(it.id))
                else -> it
            }
        }
    }

    fun reorder(games: List<Game>, id: String, direction: Int): List<Game> {
        val game = games.first { it.id == id }
        val peers =
            games
                .filter { it.shelf == game.shelf && it.place == game.place }
                .sortedBy { it.position }
                .toMutableList()
        val from = peers.indexOfFirst { it.id == id }
        val to = (from + direction).coerceIn(0, peers.lastIndex)
        peers.add(to, peers.removeAt(from))
        val positions = peers.mapIndexed { index, peer -> peer.id to index }.toMap()
        return games.map {
            if (it.id in positions) it.copy(position = positions.getValue(it.id)) else it
        }
    }

    fun pause(games: List<Game>, id: String, note: String): List<Game> =
        move(games.map { if (it.id == id) it.copy(pauseNote = note) else it }, id, Place.PAUSED)

    fun finish(games: List<Game>, id: String, entry: Playthrough, replayCount: Int?): List<Game> {
        require(entry.hours == null || entry.hours.isFinite() && entry.hours >= 0) {
            "Playtime must be a finite, nonnegative number"
        }
        require(replayCount == null || replayCount >= 0) { "Replays cannot be negative" }
        val game = games.first { it.id == id }
        if (game.playthroughs.any { it.id == entry.id }) return games
        val updated =
            games.map {
                if (it.id == id)
                    it.copy(
                        playthroughs = it.playthroughs + entry,
                        hasPlayed = true,
                        replayCount = replayCount,
                    )
                else it
            }
        return move(updated, id, Place.FINISHED)
    }

    fun editPlaythrough(
        games: List<Game>,
        id: String,
        entry: Playthrough,
        replayCount: Int?,
    ): List<Game> {
        require(entry.hours == null || entry.hours.isFinite() && entry.hours >= 0) {
            "Playtime must be a finite, nonnegative number"
        }
        require(replayCount == null || replayCount >= 0) { "Replays cannot be negative" }
        return games.map { game ->
            if (game.id != id) game
            else
                game.copy(
                    playthroughs =
                        if (game.playthroughs.any { it.id == entry.id })
                            game.playthroughs.map {
                                if (it.id == entry.id) entry.copy(finishedAt = it.finishedAt)
                                else it
                            }
                        else game.playthroughs + entry,
                    replayCount = replayCount,
                    hasPlayed = true,
                )
        }
    }
}

fun adjustedEncounterCount(current: Long, delta: Long): Long {
    require(current >= 0) { "Encounters cannot be negative" }
    return if (delta >= 0) increasedEncounterCount(current, delta)
    else if (delta < -current) 0 else current + delta
}

fun increasedEncounterCount(current: Long, amount: Long): Long {
    require(current >= 0 && amount >= 0) { "Encounters cannot be negative" }
    return if (amount > Long.MAX_VALUE - current) Long.MAX_VALUE else current + amount
}

enum class LibrarySort(val label: String) {
    ADDED_NEW("date added · newest"),
    ADDED_OLD("date added · oldest"),
    RELEASE_NEW("release date · newest"),
    RELEASE_OLD("release date · oldest"),
    AZ("title · A–Z"),
    ZA("title · Z–A"),
    SYSTEM("system"),
    COLLECTION("collection order"),
}

fun sortedLibrary(games: List<Game>, sort: LibrarySort): List<Game> =
    when (sort) {
        LibrarySort.ADDED_NEW -> games.sortedByDescending { it.added }
        LibrarySort.ADDED_OLD -> games.sortedBy { it.added }
        LibrarySort.RELEASE_NEW ->
            games.sortedWith(
                compareBy<Game> { it.release == null }
                    .thenByDescending { it.release }
                    .thenBy { it.title.lowercase() }
            )
        LibrarySort.RELEASE_OLD ->
            games.sortedWith(
                compareBy<Game> { it.release == null }
                    .thenBy { it.release }
                    .thenBy { it.title.lowercase() }
            )
        LibrarySort.AZ -> games.sortedBy { it.title.lowercase() }
        LibrarySort.ZA -> games.sortedByDescending { it.title.lowercase() }
        LibrarySort.SYSTEM ->
            games.sortedWith(compareBy<Game> { it.system }.thenBy { it.title.lowercase() })
        LibrarySort.COLLECTION ->
            games.sortedWith(
                compareBy<Game> { it.collection }
                    .thenBy { it.collectionOrder }
                    .thenBy { it.title.lowercase() }
            )
    }
