package com.cmyksoda.savepoint

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneGamesTest {
    private val phone =
        listOf(
            PhoneGame("com.example.go", "Pokémon GO"),
            PhoneGame("com.example.sleep", "Pokémon Sleep", game = false),
            PhoneGame("com.example.knight", "Hollow Knight"),
            PhoneGame("com.example.maps", "Maps", game = false),
        )

    @Test
    fun gamesAlreadyInTheLibraryStayHidden() {
        val library =
            listOf(
                Game(title = "Pokémon GO", system = "Android"),
                Game(
                    title = "Knight",
                    system = "Android",
                    source = "phone",
                    sourceId = "com.example.knight",
                ),
            )
        assertEquals(emptyList<String>(), phoneGameMatches(phone, library, "").map { it.title })
    }

    @Test
    fun typingFindsAppsThatArentFiledAsGames() {
        assertEquals(
            listOf("Pokémon GO", "Hollow Knight"),
            phoneGameMatches(phone, emptyList(), "").map { it.title },
        )
        assertEquals(
            listOf("Pokémon GO", "Pokémon Sleep"),
            phoneGameMatches(phone, emptyList(), "pokemon").map { it.title },
        )
        assertEquals(
            listOf("Pokémon Sleep"),
            phoneGameMatches(phone, emptyList(), "sleep").map { it.title },
        )
        assertEquals(
            emptyList<String>(),
            phoneGameMatches(phone, emptyList(), "zelda").map { it.title },
        )
    }

    @Test
    fun aLibraryGameFindsItsInstalledApp() {
        val fromStore =
            Game(title = "Pokémon Sleep", system = "Android", source = "appstore", sourceId = "1")
        assertEquals("com.example.sleep", phoneAppFor(fromStore, phone)?.packageName)
        val fromPhone =
            Game(
                title = "Renamed",
                system = "Android",
                source = "phone",
                sourceId = "com.example.knight",
            )
        assertEquals("com.example.knight", phoneAppFor(fromPhone, phone)?.packageName)
        assertEquals(null, phoneAppFor(Game(title = "Metroid Dread", system = "Android"), phone))
    }
}
