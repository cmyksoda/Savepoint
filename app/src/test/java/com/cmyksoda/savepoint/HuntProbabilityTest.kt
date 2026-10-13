package com.cmyksoda.savepoint

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HuntProbabilityTest {
    @Test
    fun quantilesAreTheFirstCountsToReachTheirProbabilities() {
        for (odds in listOf(4096, 8192, 512, 1, Int.MAX_VALUE)) {
            val stats = huntProbability(0, odds)
            assertEquals(0.0, stats.chance, 0.0)
            assertTrue(huntProbability(stats.encounters90, odds).chance >= .9)
            assertTrue(huntProbability(stats.encounters90 - 1, odds).chance < .9)
            assertTrue(huntProbability(stats.encounters50, odds).chance >= .5)
            assertTrue(huntProbability(stats.encounters50 - 1, odds).chance < .5)
        }
    }

    @Test
    fun longHuntsRemainFiniteAndProgressIsBounded() {
        val stats = huntProbability(Long.MAX_VALUE, 8192)
        assertEquals(1.0, stats.progress90, 0.0)
        assertTrue(stats.chance.isFinite())
        assertTrue(stats.oddsMultiples.isFinite())
        assertEquals(Long.MAX_VALUE, increasedEncounterCount(Long.MAX_VALUE - 2, 100))
        assertEquals(124L, increasedEncounterCount(24, 100))
    }

    @Test
    fun encounterAdjustmentsClampAtZeroAndSaturateWithoutOverflow() {
        assertEquals(1L, adjustedEncounterCount(0, 1))
        assertEquals(0L, adjustedEncounterCount(1, -1))
        assertEquals(0L, adjustedEncounterCount(0, -1))
        assertEquals(0L, adjustedEncounterCount(100, Long.MIN_VALUE))
        assertEquals(Long.MAX_VALUE, adjustedEncounterCount(Long.MAX_VALUE, 1))
        assertEquals(Long.MAX_VALUE - 1, adjustedEncounterCount(Long.MAX_VALUE, -1))
    }

    @Test
    fun speciesLookupUsesTheDeclaredDefaultAndExplicitFormsStayExact() {
        val requests = mutableListOf<String>()
        val pokemon = JSONObject("{\"name\":\"shaymin-land\"}")
        val resolved =
            resolvePokemon("shaymin") { url ->
                requests += url
                when {
                    url.endsWith("pokemon/shaymin") -> throw SourceUnavailable(404)
                    url.endsWith("pokemon-species/shaymin") ->
                        JSONObject(
                            "{\"varieties\":[{\"is_default\":false,\"pokemon\":{\"name\":\"shaymin-sky\"}},{\"is_default\":true,\"pokemon\":{\"name\":\"shaymin-land\"}}]}"
                        )
                    else -> pokemon
                }
            }
        assertSame(pokemon, resolved)
        assertTrue(requests.last().endsWith("/shaymin-land"))
        requests.clear()
        resolvePokemon("shaymin-sky") { url ->
            requests += url
            pokemon
        }
        assertEquals(listOf("https://pokeapi.co/api/v2/pokemon/shaymin-sky"), requests)
    }

    @Test
    fun providerErrorsAreNotTreatedAsMissingSpecies() {
        try {
            resolvePokemon("shaymin") { throw SourceUnavailable(503) }
            fail("Expected provider failure")
        } catch (e: SourceUnavailable) {
            assertEquals(503, e.status)
        }
    }
}
