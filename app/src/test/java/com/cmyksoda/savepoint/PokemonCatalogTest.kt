package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class PokemonCatalogTest {
    @Test
    fun pokemonAndFormsAcceptTyposAndSeparateWordQueries() {
        val choices =
            listOf(
                PokemonChoice("aurorus", "aurorus", 699, ""),
                PokemonChoice("shaymin-sky", "shaymin", 492, "sky"),
                PokemonChoice("shaymin-land", "shaymin", 492, "land"),
            )
        assertEquals("aurorus", searchPokemon(choices, "aurous").single().slug)
        assertEquals("shaymin-sky", searchPokemon(choices, "shamin skye").single().slug)
        assertEquals("aurorus", searchPokemon(choices, "699").single().slug)
    }

    @Test
    fun aurorusUsesTheLegendsZaRenderAndPlatinumShayminKeepsItsForm() {
        assertEquals(
            "https://www.serebii.net/Shiny/ZA/699.png",
            serebiiShinyCandidates(PokemonChoice("aurorus", "aurorus", 699, ""), "legends-z-a")
                .first(),
        )
        assertEquals(
            "https://www.serebii.net/Shiny/Pl/492-l.png",
            serebiiShinyCandidates(
                    PokemonChoice("shaymin-land", "shaymin", 492, "land"),
                    "platinum",
                )
                .first(),
        )
        assertEquals(
            "https://www.serebii.net/Shiny/Pl/492.png",
            serebiiShinyCandidates(PokemonChoice("shaymin-sky", "shaymin", 492, "sky"), "platinum")
                .first(),
        )
        assertTrue(
            serebiiShinyCandidates(PokemonChoice("aurorus", "aurorus", 699, ""), "other").isEmpty()
        )
    }

}
