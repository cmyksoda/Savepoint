package com.cmyksoda.savepoint

const val deltarunePublisherCover =
    "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1671210/library_600x900.jpg"

private const val legacyDeltaruneCover = "https://art.gametdb.com/switch/cover/US/BN5WA.jpg"

fun preferPublisherCover(game: Game): Game =
    if (
        game.title.trim().equals("DELTARUNE", true) &&
            game.source == "gametdb" &&
            game.sourceId == "BN5WA" &&
            game.cover in
                setOf(legacyDeltaruneCover, legacyDeltaruneCover.replace("/cover/", "/coverHQ/"))
    )
        game.copy(cover = deltarunePublisherCover)
    else game

fun updateStarterArtwork(games: List<Game>): List<Game> =
    fillMissingStarterCovers(
        games.map { game ->
            val updated = preferPublisherCover(game)
            if (updated != game && game.system in listOf("Nintendo Switch", "Nintendo Switch 2"))
                updated.copy(
                    system = "Nintendo Switch 2",
                    source = "publisher",
                    sourceId = "1671210",
                )
            else game
        }
    )

data class BundledCover(
    val title: String,
    val system: String,
    val asset: String,
    val url: String,
    val attribution: String,
)

val bundledCovers =
    listOf(
        BundledCover(
            "Rhythm Heaven Groove",
            "Nintendo Switch",
            "rhythm-heaven.jpg",
            "https://art.gametdb.com/switch/cover/US/BFLTA.jpg",
            "Cover via GameTDB",
        ),
        BundledCover(
            "DELTARUNE",
            "Nintendo Switch 2",
            "deltarune.jpg",
            deltarunePublisherCover,
            "Publisher artwork · DELTARUNE",
        ),
        BundledCover(
            "Metroid: Zero Mission",
            "Game Boy Advance",
            "zero-mission.jpg",
            "https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Game_Boy_Advance/master/Named_Boxarts/Metroid%20-%20Zero%20Mission%20%28USA%29.png",
            "Cover via Libretro Thumbnails",
        ),
        BundledCover(
            "AM2R",
            "PC",
            "am2r.jpg",
            "https://cdn2.steamgriddb.com/file/sgdb-cdn/grid/1b39a10cc39ee53e5b2fdc1eda1eb5da.png",
            "Cover via SteamGridDB",
        ),
        BundledCover(
            "Pokémon Pokopia",
            "Nintendo Switch 2",
            "pokopia.jpg",
            "https://assets.nintendo.com/image/upload/c_crop,g_north_west,x_382,y_0,w_1236,h_2000/f_png/ncom/My%20Nintendo%20Store/EN-US/Nintendo%20Switch%202/Software/Pokemon%20Pokopia/125647-nintendo-pokemon-pokopia-2000x2000",
            "Cover via Nintendo",
        ),
    )

fun fillMissingStarterCovers(games: List<Game>): List<Game> = games.map { game ->
    if (game.cover.isNotBlank() || game.shelf == Shelf.HUNTS) game
    else {
        val cover = bundledCovers.firstOrNull {
            it.title.equals(game.title.trim(), ignoreCase = true) &&
                (it.system == game.system ||
                    (it.title == "DELTARUNE" && game.system == "Nintendo Switch"))
        }
        if (cover == null) game else game.copy(cover = cover.url)
    }
}

fun starterGames(): List<Game> =
    fillMissingStarterCovers(
        listOf(
            Game(
                title = "Rhythm Heaven Groove",
                system = "Nintendo Switch",
                place = Place.ACTIVE,
                source = "gametdb",
                sourceId = "BFLTA",
            ),
            Game(
                title = "DELTARUNE",
                system = "Nintendo Switch 2",
                place = Place.ACTIVE,
                position = 1,
                source = "publisher",
                sourceId = "1671210",
            ),
            Game(
                title = "Metroid: Zero Mission",
                system = "Game Boy Advance",
                place = Place.ACTIVE,
                position = 2,
                collection = "2d metroid",
                collectionOrder = 1,
            ),
            Game(
                title = "AM2R",
                system = "PC",
                place = Place.ACTIVE,
                position = 3,
                collection = "2d metroid",
                collectionOrder = 2,
            ),
            Game(
                title = "Pokémon Pokopia",
                system = "Nintendo Switch 2",
                shelf = Shelf.EASY,
                place = Place.ACTIVE,
            ),
        )
    )
