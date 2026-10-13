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
    )

val systems =
    listOf(
        "PC",
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
        "Other",
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
    val manualOdds: Int? = null,
)

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
                it.id == id -> it.copy(place = place, position = positions.getValue(id))
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
}

val huntVersions =
    linkedMapOf(
        "gold" to "Gold",
        "silver" to "Silver",
        "crystal" to "Crystal",
        "ruby-sapphire" to "Ruby / Sapphire",
        "emerald" to "Emerald",
        "firered-leafgreen" to "FireRed / LeafGreen",
        "diamond-pearl" to "Diamond / Pearl",
        "platinum" to "Platinum",
        "heartgold-soulsilver" to "HeartGold / SoulSilver",
        "black-white" to "Black / White",
        "black-2-white-2" to "Black 2 / White 2",
        "x-y" to "X / Y",
        "omegaruby-alphasapphire" to "Omega Ruby / Alpha Sapphire",
        "sun-moon" to "Sun / Moon",
        "ultra-sun-ultra-moon" to "Ultra Sun / Ultra Moon",
        "sword-shield" to "Sword / Shield",
        "scarlet-violet" to "Scarlet / Violet",
        "lets-go" to "Let's Go Pikachu / Eevee",
        "brilliant-diamond-shining-pearl" to "Brilliant Diamond / Shining Pearl",
        "legends-arceus" to "Legends: Arceus",
        "legends-z-a" to "Legends: Z-A",
        "other" to "Other (choose artwork manually)",
    )

fun calculatedOdds(game: Game): Pair<Int?, String> {
    game.manualOdds?.let {
        return it to "manual odds"
    }
    val verifiedFullOdds = setOf("emerald", "firered-leafgreen", "platinum", "heartgold-soulsilver")
    if (
        game.pokemonVersion in verifiedFullOdds &&
            game.method == "Random encounters" &&
            !game.shinyCharm
    ) {
        return 8192 to "standard random encounter · no modifiers"
    }
    return null to "unknown — enter verified odds for this game and method"
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
