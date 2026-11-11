package com.cmyksoda.savepoint

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
}
