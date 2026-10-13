package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class HuntOddsTest {
    private val hunt = Game(title = "SoulSilver", pokemon = "vulpix", shelf = Shelf.HUNTS)

    @Test
    fun supportedRandomEncounterUsesVerifiedOdds() {
        assertEquals(8192, calculatedOdds(hunt).first)
    }

    @Test
    fun methodOrModifierCannotSilentlyKeepStandardOdds() {
        assertNull(calculatedOdds(hunt.copy(method = "Breeding")).first)
        assertNull(calculatedOdds(hunt.copy(shinyCharm = true)).first)
        assertNull(calculatedOdds(hunt.copy(pokemonVersion = "scarlet-violet")).first)
    }

    @Test
    fun manualOddsAreExplicitAndOverrideAutomaticValue() {
        assertEquals(512 to "manual odds", calculatedOdds(hunt.copy(manualOdds = 512)))
    }
}
