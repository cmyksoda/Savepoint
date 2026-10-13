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
        assertEquals(4096, calculatedOdds(hunt.copy(pokemonVersion = "scarlet-violet")).first)
    }

    @Test
    fun manualOddsAreExplicitAndOverrideAutomaticValue() {
        assertEquals(512 to "manual odds", calculatedOdds(hunt.copy(manualOdds = 512)))
    }

    @Test
    fun everyListedGenerationUsesItsStandardBaseRate() {
        for (version in huntVersions.keys.filter { it != "other" }) {
            val expected =
                if (
                    version in
                        listOf(
                            "gold",
                            "silver",
                            "crystal",
                            "ruby-sapphire",
                            "emerald",
                            "firered-leafgreen",
                            "diamond-pearl",
                            "platinum",
                            "heartgold-soulsilver",
                            "black-white",
                            "black-2-white-2",
                        )
                )
                    8192
                else 4096
            assertEquals(
                version,
                expected,
                calculatedOdds(hunt.copy(pokemonVersion = version)).first,
            )
        }
        assertNull(calculatedOdds(hunt.copy(pokemonVersion = "other")).first)
    }
}
