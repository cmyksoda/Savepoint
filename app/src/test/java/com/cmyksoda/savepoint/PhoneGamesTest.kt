package com.cmyksoda.savepoint

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneGamesTest {
    private val phone =
        listOf(
            PhoneGame("com.example.go", "Pokémon GO"),
            PhoneGame("com.example.sleep", "Pokémon Sleep"),
            PhoneGame("com.example.knight", "Hollow Knight"),
        )

    @Test
    fun gamesAlreadyInTheLibraryStayHidden() {
        val library =
            listOf(
                Game(title = "Pokémon GO", system = "Android"),
                Game(title = "Knight", system = "Android", source = "phone", sourceId = "com.example.knight"),
            )
        assertEquals(listOf("Pokémon Sleep"), phoneGameMatches(phone, library, "").map { it.title })
    }

    @Test
    fun theListNarrowsAsATitleIsTyped() {
        assertEquals(
            listOf("Pokémon GO", "Pokémon Sleep"),
            phoneGameMatches(phone, emptyList(), "pokemon").map { it.title },
        )
        assertEquals(listOf("Hollow Knight"), phoneGameMatches(phone, emptyList(), "hollow").map { it.title })
        assertEquals(emptyList<String>(), phoneGameMatches(phone, emptyList(), "zelda").map { it.title })
    }
}
