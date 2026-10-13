package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class StarterLibraryTest {
    @Test
    fun everyStarterHasACoverWhileTheQueueAndCollectionsStayOrdered() {
        val games = starterGames()
        assertEquals(5, games.size)
        assertTrue(games.all { it.cover.startsWith("https://") })
        assertEquals(
            listOf("Rhythm Heaven Groove", "DELTARUNE", "Metroid: Zero Mission", "AM2R"),
            games
                .filter { it.shelf == Shelf.PLAY_THROUGH }
                .sortedBy { it.position }
                .map { it.title },
        )
        assertEquals(
            listOf(1, 2),
            games.filter { it.collection == "2d metroid" }.map { it.collectionOrder },
        )
        assertEquals(Shelf.EASY, games.last().shelf)
    }

    @Test
    fun aMissingCoverIsFilledWithoutChangingAnyPersonalData() {
        val game =
            Game(
                title = "Metroid: Zero Mission",
                system = "Game Boy Advance",
                place = Place.PAUSED,
                notes = "general note",
                pauseNote = "where to go next",
                playthroughs = listOf(Playthrough(thoughts = "first time")),
                replayCount = 2,
            )
        val updated = fillMissingStarterCovers(listOf(game)).single()
        assertTrue(updated.cover.isNotBlank())
        assertEquals(game, updated.copy(cover = ""))
    }

    @Test
    fun selectedAndCustomRemoteCoversArePreserved() {
        for (cover in listOf("local:chosen.image", "https://example.com/my-cover.png")) {
            val game = Game(title = "AM2R", system = "PC", cover = cover)
            assertEquals(listOf(game), fillMissingStarterCovers(listOf(game)))
        }
    }

    @Test
    fun differentSystemsUnknownTitlesAndShinyHuntsAreNotGuessed() {
        val games =
            listOf(
                Game(title = "Metroid: Zero Mission", system = "Wii U"),
                Game(title = "Unknown game", system = "Game Boy Advance"),
                Game(title = "Pokémon Pokopia", system = "Nintendo Switch 2", shelf = Shelf.HUNTS),
            )
        assertEquals(games, fillMissingStarterCovers(games))
    }

    @Test
    fun upgradesAreIdempotentAndNeverAddGamesToAnEmptyLibrary() {
        assertTrue(fillMissingStarterCovers(emptyList()).isEmpty())
        val game = Game(title = "am2r", system = "PC", place = Place.FINISHED)
        val updated = fillMissingStarterCovers(listOf(game))
        assertEquals(updated, fillMissingStarterCovers(updated))
        assertEquals(game.id, updated.single().id)
    }

    @Test
    fun deltaruneStarterUsesBlackPublisherArtworkAndSwitch2() {
        val game = starterGames().first { it.title == "DELTARUNE" }
        assertEquals("Nintendo Switch 2", game.system)
        assertEquals(deltarunePublisherCover, game.cover)
    }

    @Test
    fun legacyDeltaruneUpgradePreservesEveryPersonalField() {
        val old =
            Game(
                title = "DELTARUNE",
                system = "Nintendo Switch",
                source = "gametdb",
                sourceId = "BN5WA",
                cover = "https://art.gametdb.com/switch/cover/US/BN5WA.jpg",
                place = Place.PAUSED,
                position = 3,
                notes = "next room",
                pauseNote = "take your time",
                replayCount = 2,
                playthroughs =
                    listOf(
                        Playthrough(feeling = Feeling.LOVED, thoughts = "wonderful", hours = 7.5)
                    ),
            )
        val updated = updateStarterArtwork(listOf(old)).single()
        assertEquals("Nintendo Switch 2", updated.system)
        assertEquals(deltarunePublisherCover, updated.cover)
        assertEquals(
            old,
            updated.copy(
                system = old.system,
                cover = old.cover,
                source = old.source,
                sourceId = old.sourceId,
            ),
        )
        assertEquals(listOf(updated), updateStarterArtwork(listOf(updated)))
    }

    @Test
    fun deltaruneCustomArtworkAndExplicitCatalogPlatformArePreserved() {
        val legacy =
            Game(
                title = "DELTARUNE",
                system = "Nintendo Switch",
                source = "gametdb",
                sourceId = "BN5WA",
            )
        for (cover in listOf("local:chosen.image", "https://example.com/cover.jpg")) {
            val game = legacy.copy(cover = cover)
            assertEquals(listOf(game), updateStarterArtwork(listOf(game)))
        }
        val catalog =
            preferPublisherCover(
                legacy.copy(cover = "https://art.gametdb.com/switch/cover/US/BN5WA.jpg")
            )
        assertEquals("Nintendo Switch", catalog.system)
        assertEquals(deltarunePublisherCover, catalog.cover)
        assertEquals(listOf(catalog), updateStarterArtwork(listOf(catalog)))
    }
}
