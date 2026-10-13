package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class CatalogResultsTest {
    @Test
    fun providerDuplicatesBecomeOneGameWithBothCoverOptions() {
        val igdb =
            Game(
                title = "Pokémon: A Game",
                system = "Nintendo Switch",
                source = "igdb",
                cover = "https://images.igdb.com/a.jpg",
                release = 50L,
            )
        val tdb =
            Game(
                title = "Pokemon - A Game",
                system = "Nintendo Switch",
                source = "gametdb",
                cover = "https://art.gametdb.com/a.jpg",
            )
        val result = groupCatalogResults(listOf(tdb, igdb)).single()
        assertEquals(igdb, result.game)
        assertEquals(setOf("IGDB", "GameTDB"), result.covers.map { it.provider }.toSet())
    }

    @Test
    fun superMetroidVirtualConsoleEntriesAreRemovedAcrossAllThreeSources() {
        val original = Game(title = "Super Metroid", system = "SNES", source = "igdb")
        val reissues =
            listOf("Wii" to "JAVE", "Wii U" to "JAJE", "Nintendo 3DS" to "UACE").map { (system, id)
                ->
                Game(title = "Super Metroid", system = system, source = "gametdb", sourceId = id)
            }
        assertEquals(listOf(original), groupCatalogResults(reissues + original).map { it.game })
    }

    @Test
    fun nativeGamesAndEnhancedEditionsKeepTheirPlatforms() {
        val native =
            Game(
                title = "Mario vs. Donkey Kong",
                system = "Nintendo 3DS",
                source = "gametdb",
                sourceId = "JYLJ",
            )
        val deluxe =
            Game(title = "Mario Kart 8 Deluxe", system = "Nintendo Switch", source = "igdb")
        val persona = Game(title = "Persona 5 Royal", system = "Nintendo Switch", source = "igdb")
        assertEquals(3, groupCatalogResults(listOf(native, deluxe, persona)).size)
        assertFalse(isVirtualConsoleId("Wii", "RMCE01"))
        assertFalse(isVirtualConsoleId("Wii", "WAAE"))
    }

    @Test
    fun twoActualPlatformsAndDifferentEnhancedTitlesRemainSeparate() {
        val original = Game(title = "A game", system = "PC")
        assertEquals(
            3,
            groupCatalogResults(
                    listOf(
                        original,
                        original.copy(system = "Nintendo Switch"),
                        original.copy(title = "A game Deluxe", system = "Nintendo Switch"),
                    )
                )
                .size,
        )
    }

    @Test
    fun savedPlatformNamesResolveToTheSameCatalogPlatformAsNewSearchResults() {
        assertEquals("SNES", catalogSystemName("Super Nintendo Entertainment System"))
        assertEquals("NES", catalogSystemName("Nintendo Entertainment System"))
        assertEquals("GameCube", catalogSystemName("Nintendo GameCube"))
        assertEquals("SNES", catalogSystemName("SNES"))
        assertEquals("Nintendo Switch", catalogSystemName("Nintendo Switch"))
        assertEquals("Unknown platform", catalogSystemName("Unknown platform"))
    }

    @Test
    fun providerMessagesUseOrForTwoAndThreeAlternatives() {
        assertEquals("IGDB", providerAlternatives(listOf("IGDB")))
        assertEquals("IGDB or LaunchBox", providerAlternatives(listOf("IGDB", "LaunchBox")))
        assertEquals(
            "GameTDB, IGDB, or LaunchBox",
            providerAlternatives(listOf("GameTDB", "IGDB", "LaunchBox")),
        )
    }
}
