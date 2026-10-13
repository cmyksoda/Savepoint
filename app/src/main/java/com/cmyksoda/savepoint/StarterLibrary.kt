package com.cmyksoda.savepoint

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
            "Nintendo Switch",
            "deltarune.jpg",
            "https://art.gametdb.com/switch/cover/US/BN5WA.jpg",
            "Cover via GameTDB",
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
            it.title.equals(game.title.trim(), ignoreCase = true) && it.system == game.system
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
                system = "Nintendo Switch",
                place = Place.ACTIVE,
                position = 1,
                source = "gametdb",
                sourceId = "BN5WA",
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
