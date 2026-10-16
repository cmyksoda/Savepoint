package com.cmyksoda.savepoint

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class HuntCatalogTest {
    companion object {
        val index by lazy {
            HuntCatalogIndex.parse(File("src/main/assets/hunt-catalog.json").readText())
        }
    }

    @Test
    fun gameCatalogTitlesInferAnEditionWithoutCreatingNewPairedHunts() {
        assertEquals("soulsilver", inferHuntVersion("Pokémon SoulSilver Version"))
        assertEquals("alpha-sapphire", inferHuntVersion("Pokémon Alpha Sapphire"))
        assertEquals("brilliant-diamond", inferHuntVersion("Pokémon Brilliant Diamond"))
        assertEquals("lets-go-eevee", inferHuntVersion("Pokémon: Let's Go, Eevee!"))
        assertNull(inferHuntVersion("Pokémon Ruby / Sapphire"))
        assertNull(inferHuntVersion("Pokémon Let's Go"))
    }

    @Test
    fun impossibleGenerationsVersionExclusivesAndShinyLocksAreExcluded() {
        val games = index.games("dialga")
        assertTrue("diamond" in games)
        assertFalse("pearl" in games)
        assertFalse("gold" in games)
        assertFalse("x" in games)
        assertFalse("legends-arceus" in games)
        assertFalse("scarlet" in games)
        assertTrue(index.games("koraidon").isEmpty())
        assertTrue(index.games("vulpix").contains("soulsilver"))
    }

    @Test
    fun formsHaveTheirOwnAvailabilityAndLegacySpeciesSlugsStillResolve() {
        assertTrue("platinum" in index.games("shaymin"))
        assertTrue("platinum" in index.games("shaymin-sky"))
        assertFalse("diamond" in index.games("shaymin-land"))
        assertFalse("soulsilver" in index.games("vulpix-alola"))
        assertTrue("sun" in index.games("vulpix-alola"))
        assertFalse("legends-arceus" in index.games("vulpix-alola"))
        assertFalse("gold" in index.games("unown-a"))
        assertTrue("gold" in index.games("unown-i"))
        assertEquals(setOf("sword", "shield"), index.games("charizard-gmax").toSet())
        assertTrue("black" in index.games("frillish-female"))
    }

    @Test
    fun methodsDependOnTheSpeciesAndGameInsteadOfJustTheGeneration() {
        assertEquals(listOf("Soft resets", "Runaways"), index.methods("dialga", "diamond"))
        assertFalse("Poké Radar" in index.methods("shaymin", "platinum"))
        assertTrue("Poké Radar" in index.methods("shinx", "platinum"))
        assertFalse("Breeding" in index.methods("pikachu", "legends-arceus"))
        assertTrue("Massive mass outbreaks" in index.methods("rowlet", "legends-arceus"))
        assertFalse("Mass outbreaks" in index.methods("rowlet", "legends-arceus"))
        assertFalse("Mass outbreaks" in index.methods("great-tusk", "scarlet"))
        assertTrue("Mass outbreaks" in index.methods("pikachu", "scarlet"))
        assertEquals(listOf("Dynamax Adventures"), index.methods("dialga", "sword"))
    }

    @Test
    fun evolutionaryTargetsExplainWhichPokemonIsActuallyHunted() {
        assertTrue(index.targets("pikachu", "scarlet", "Random encounters").isEmpty())
        assertEquals(listOf("Amaura"), index.targets("aurorus", "legends-z-a", "Fossil revival"))
        assertEquals(listOf("Charmander"), index.targets("charizard", "x", "Masuda method"))
    }

    @Test
    fun bundledDataContainsValidSupportedEditionsAndPreservesFormDexNumbers() {
        assertTrue(index.choices.size > 1000)
        assertEquals(6, index.choices.single { it.slug == "charizard-mega-x" }.number)
        assertFalse(index.choices.single { it.slug == "charizard-mega-x" }.default)
        assertTrue(index.choices.single { it.slug == "aurorus" }.default)
        for (choice in index.choices) {
            for (game in index.games(choice.slug)) {
                assertTrue(game in huntGameEditions)
                assertTrue("${choice.slug}: $game", index.methods(choice.slug, game).isNotEmpty())
            }
        }
    }
}
