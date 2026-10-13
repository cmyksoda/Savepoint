package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class PokemonCatalogTest {
    @Test
    fun formsKeepTheirSpeciesDexNumberInsteadOfTheirSeparateFormId() {
        val species = "id,identifier\n6,charizard\n699,aurorus"
        val pokemon =
            "id,identifier,species_id,height,weight,base_experience,order,is_default\n10034,charizard-mega-x,6,1,1,1,1,0\n699,aurorus,699,1,1,1,1,1"
        val forms =
            "id,identifier,form_identifier,pokemon_id,version,is_default\n10034,charizard-mega-x,mega-x,10034,15,1\n699,aurorus,,699,15,1"
        val result = parsePokemonChoices(pokemon, species, forms)
        assertEquals(6, result.first().number)
        assertFalse(result.first().default)
        assertTrue(result.last().default)
    }

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

    @Test
    fun onlyTheSwitchTwoEditionQualifierIsRemovedForCoverLookup() {
        assertEquals(
            "Pokémon Legends: Z-A",
            gameTdbCoverTitle("Pokémon Legends: Z-A - Nintendo Switch 2 Edition"),
        )
        assertEquals("Mario Kart 8 Deluxe", gameTdbCoverTitle("Mario Kart 8 Deluxe"))
        assertEquals("A game Remastered", gameTdbCoverTitle("A game Remastered"))
    }
}
