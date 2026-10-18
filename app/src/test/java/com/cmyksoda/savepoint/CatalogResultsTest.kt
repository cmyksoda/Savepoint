package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class CatalogResultsTest {
    @Test
    fun aGameFoundOnLaunchBoxAndSteamBecomesOneResultWithSteamArt() {
        val launchBox =
            Game(
                title = "Hollow Knight",
                system = "PC",
                source = "launchbox",
                sourceId = "1-hollow-knight",
                release = 50L,
            )
        val steam =
            Game(
                title = "Hollow Knight",
                system = "PC",
                source = "steam",
                sourceId = "367520",
                cover = steamArt("367520"),
            )
        val result = groupCatalogResults(listOf(steam, launchBox)).single()
        assertEquals(launchBox, result.game)
        assertEquals(listOf(CoverCandidate(steamArt("367520"), "Steam", "Library art")), result.covers)
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
        assertEquals("LaunchBox", providerAlternatives(listOf("LaunchBox")))
        assertEquals("LaunchBox or Steam", providerAlternatives(listOf("LaunchBox", "Steam")))
        assertEquals(
            "LaunchBox, Steam, or App Store",
            providerAlternatives(listOf("LaunchBox", "Steam", "App Store")),
        )
    }

}
