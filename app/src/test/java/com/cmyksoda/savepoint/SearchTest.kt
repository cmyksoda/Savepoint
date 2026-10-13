package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class SearchTest {
    @Test
    fun pokemonMatchesWithoutItsAccentAndAcrossCapitalization() {
        assertEquals(0, searchMatchScore("Pokémon Pokopia", "POKEMON"))
        assertEquals(0, searchMatchScore("Pokémon Platinum", "poke\u0301mon"))
        assertEquals(0, searchMatchScore("Metroid: Zero Mission", "metroid zero"))
    }

    @Test
    fun CommonTyposIncludeMissingLettersAndSwappedLetters() {
        assertNotNull(searchMatchScore("Pokémon Pokopia", "pokmeon"))
        assertNotNull(searchMatchScore("Metroid Fusion", "metriod fuson"))
        assertNotNull(searchMatchScore("Rhythm Heaven Groove", "rythm heven"))
        assertNotNull(searchMatchScore("Metroid: Zero Mission", "mision"))
    }

    @Test
    fun UnrelatedTitlesAndShortTyposDoNotMatch() {
        assertNull(searchMatchScore("Pokémon Platinum", "metroid"))
        assertNull(searchMatchScore("AM2R", "zz"))
        assertNull(searchMatchScore("Pokémon Mystery Dungeon", "pokemon y"))
        assertNull(searchMatchScore("Rhythm Heaven Groove", "rhythm metroid"))
    }

    @Test
    fun ExactResultsRankBeforeTypoMatchesAndEmptySearchShowsEverything() {
        assertTrue(
            searchMatchScore("Metroid Fusion", "metriod")!! >
                searchMatchScore("Metriod", "metriod")!!
        )
        assertEquals(0, searchMatchScore("Pokémon Platinum", " "))
        assertEquals(0, searchMatchScore("AM2R", "am2"))
    }
}
