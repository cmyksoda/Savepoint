package com.cmyksoda.savepoint

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CatalogSourcesTest {
    private fun day(year: Int, month: Int, date: Int) =
        LocalDate.of(year, month, date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun card(slug: String, title: String, platform: String, released: String) =
        """
        <a class="list-item link-no-underline" href="/games/details/$slug">
          <div class="gameCard">
            <div class="cardImgPart"><img src="https://images.launchbox-app.com/shot.jpg" alt="$title" /></div>
            <div class="cardTitle"><h3>$title</h3><p>$platform</p></div>
            <div class="addToCollection"><a href="javascript:">Default Collection</a></div>
            <div class="releaseDate"><span>Released</span><h5>$released</h5></div>
          </div>
        </a>
        """

    @Test
    fun launchBoxCardsBecomeGamesOnKnownSystems() {
        val html =
            card("18848-kururin-paradise", "Kururin Paradise", "Nintendo Game Boy Advance", "December 6, 2002") +
                card("5-a-home-computer-game", "A Home Computer Game", "Commodore 64", "1985") +
                card("7-pokemon-crystal", "Pok&#233;mon Crystal &amp; Friends", "Nintendo Game Boy Color", "July 29, 2001")
        val games = parseLaunchBoxResults(html)
        assertEquals(listOf("Kururin Paradise", "Pokémon Crystal & Friends"), games.map { it.title })
        assertEquals(listOf("Game Boy Advance", "Game Boy Color"), games.map { it.system })
        assertEquals("18848-kururin-paradise", games.first().sourceId)
        assertEquals("launchbox", games.first().source)
        assertEquals(day(2002, 12, 6), games.first().release)
    }

    @Test
    fun launchBoxCoversPutOfficialFrontsFirstAndKeepFanArt() {
        fun image(name: String, label: String, host: String = "images.launchbox-app.com") =
            """<a href="https://$host/$name.jpg" data-title="$label">"""
        val html =
            image("fan", "Kururin Paradise - Fanart - Box - Front Image") +
                image("back", "Kururin Paradise - Box - Back Image (Japan)") +
                image("japan", "Kururin Paradise - Box - Front Image (Japan)") +
                image("fan-japan", "Kururin Paradise - Fanart - Box - Front Image (Japan)") +
                image("america", "Kururin Paradise - Box - Front Image (North America)") +
                image("rebuilt", "Kururin Paradise - Box - Front - Reconstructed Image") +
                image("elsewhere", "Kururin Paradise - Box - Front Image", host = "example.org")
        val covers = parseLaunchBoxCovers(html)
        assertEquals(
            listOf("North America", "Japan", "Reconstructed", "Fan art", "Fan art · Japan"),
            covers.map { it.label },
        )
        assertTrue(covers.all { it.provider == "LaunchBox" && "launchbox-app.com" in it.url })
    }

    @Test
    fun steamResultsSkipSoundtracksAndUseLibraryArt() {
        val json =
            """{"total":3,"items":[
                {"type":"app","name":"Hollow Knight","id":367520},
                {"type":"app","name":"Hollow Knight - Official Soundtrack","id":598190},
                {"type":"app","name":"Hollow Knight: Silksong","id":1030300}]}"""
        val games = parseSteamResults(json)
        assertEquals(listOf("Hollow Knight", "Hollow Knight: Silksong"), games.map { it.title })
        assertEquals(steamArt("367520"), games.first().cover)
        assertTrue(games.all { it.system == "PC" && it.source == "steam" })
    }

    @Test
    fun appStoreResultsKeepGamesOnTheChosenPhone() {
        val json =
            """{"results":[
                {"trackId":1,"trackName":"Pokémon GO","primaryGenreName":"Games",
                 "artworkUrl512":"https://is1-ssl.mzstatic.com/go.jpg","releaseDate":"2016-07-07T00:39:49Z"},
                {"trackId":2,"trackName":"A Photo Editor","primaryGenreName":"Photo & Video",
                 "artworkUrl512":"https://is1-ssl.mzstatic.com/photo.jpg","releaseDate":"2020-01-01T00:00:00Z"}]}"""
        val games = parseAppStoreResults(json, "Android")
        assertEquals(listOf("Pokémon GO"), games.map { it.title })
        assertEquals("Android", games.single().system)
        assertEquals(day(2016, 7, 7), games.single().release)
    }

    @Test
    fun eachSystemOffersTheCoverSourcesThatCarryIt() {
        assertEquals(listOf("LaunchBox"), coverProviders("Nintendo Switch"))
        assertEquals(listOf("LaunchBox", "Steam"), coverProviders("PC"))
        assertEquals(listOf("LaunchBox", "App Store"), coverProviders("Android"))
    }
}
