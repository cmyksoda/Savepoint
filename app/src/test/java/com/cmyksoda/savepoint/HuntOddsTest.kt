package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class HuntOddsTest {
    private fun hunt(
        version: String,
        method: String = "Random encounters",
        charm: Boolean = false,
        config: HuntConfig = HuntConfig(),
    ) =
        Game(
            title = "hunt",
            pokemon = "vulpix",
            shelf = Shelf.HUNTS,
            pokemonVersion = version,
            method = method,
            shinyCharm = charm,
            huntConfig = config,
        )

    private fun odds(expected: Double, game: Game, tolerance: Double = .02) =
        assertEquals(expected, calculatedOdds(game).first!!, tolerance)

    @Test
    fun baseOddsAndCharmMatchTheActualEdition() {
        listOf("gold", "ruby", "platinum", "soulsilver", "white").forEach { odds(8192.0, hunt(it)) }
        listOf("y", "sword", "scarlet", "legends-z-a").forEach { odds(4096.0, hunt(it)) }
        odds(8192.0, hunt("soulsilver", charm = true))
        odds(2731.0, hunt("black-2", charm = true))
        odds(1365.67, hunt("scarlet", charm = true))
        assertNull(calculatedOdds(hunt("other")).first)
    }

    @Test
    fun arceusResearchAndOutbreaksStackWithoutConfusingTheTwoOutbreakTypes() {
        odds(2048.25, hunt("legends-arceus", config = HuntConfig(research = 1)))
        odds(1024.38, hunt("legends-arceus", config = HuntConfig(research = 2)))
        odds(819.60, hunt("legends-arceus", charm = true))
        odds(585.57, hunt("legends-arceus", charm = true, config = HuntConfig(research = 2)))
        odds(158.02, hunt("legends-arceus", "Mass outbreaks"))
        odds(128.49, hunt("legends-arceus", "Mass outbreaks", true, HuntConfig(research = 2)))
        odds(315.54, hunt("legends-arceus", "Massive mass outbreaks"))
        odds(
            216.05,
            hunt("legends-arceus", "Massive mass outbreaks", true, HuntConfig(research = 2)),
        )
    }

    @Test
    fun scarletOutbreakProgressIsIndependentOfEncounterTotalAndMealDoesNotAffectEggs() {
        odds(4096.0, hunt("scarlet", "Mass outbreaks", config = HuntConfig(chain = 29)))
        odds(2048.25, hunt("scarlet", "Mass outbreaks", config = HuntConfig(chain = 30)))
        val full =
            hunt("violet", "Mass outbreaks", true, HuntConfig(chain = 60, sparklingPower = 3))
        odds(512.44, full.copy(encounters = 10000))
        odds(512.44, hunt("violet", "Masuda method", true, HuntConfig(sparklingPower = 3)))
        odds(4096.0, hunt("violet", "Soft resets", true, HuntConfig(sparklingPower = 3)))
    }

    @Test
    fun charmOnlyAffectsEggsInBdspAndPlainCharmEggsUseTwoRolls() {
        odds(4096.0, hunt("brilliant-diamond", charm = true))
        odds(2048.25, hunt("brilliant-diamond", "Breeding", true))
        odds(512.44, hunt("shining-pearl", "Masuda method", true))
        odds(2048.25, hunt("shining-pearl", "Grand Underground", true, HuntConfig(diglett = true)))
        odds(2048.25, hunt("sword", "Breeding", true))
    }

    @Test
    fun masudaUsesGenerationSpecificRollCounts() {
        odds(1638.80, hunt("platinum", "Masuda method"))
        odds(1365.75, hunt("white", "Masuda method"))
        odds(1024.44, hunt("white-2", "Masuda method", true))
        odds(683.08, hunt("y", "Masuda method"))
        assertNull(calculatedOdds(hunt("crystal", "Breeding")).first)
    }

    @Test
    fun radarUsesItsOwnPatchFormulaAndClampsAtForty() {
        odds(199.80, hunt("platinum", "Poké Radar", config = HuntConfig(chain = 40)))
        odds(100.0, hunt("x", "Poké Radar", true, HuntConfig(chain = 40)))
        odds(100.0, hunt("x", "Poké Radar", config = HuntConfig(chain = 2, radarMusic = true)))
        odds(99.90, hunt("brilliant-diamond", "Poké Radar", true, HuntConfig(chain = 40)))
        odds(99.90, hunt("brilliant-diamond", "Poké Radar", config = HuntConfig(chain = 100)))
        odds(399.61, hunt("brilliant-diamond", "Poké Radar", config = HuntConfig(chain = 38)))
        assertNull(calculatedOdds(hunt("platinum", "Poké Radar")).first)
    }

    @Test
    fun sosWrapsInSunMoonButNotUltra() {
        odds(273.54, hunt("sun", "SOS calls", true, HuntConfig(chain = 31)))
        odds(1365.67, hunt("sun", "SOS calls", true, HuntConfig(chain = 256)))
        odds(273.54, hunt("ultra-sun", "SOS calls", true, HuntConfig(chain = 256)))
    }

    @Test
    fun letsGoComboLureAndCharmStackOnlyForSpawns() {
        odds(
            273.54,
            hunt("lets-go-pikachu", "Catch combo", true, HuntConfig(chain = 31, lure = true)),
        )
        odds(1024.38, hunt("lets-go-eevee", charm = true, config = HuntConfig(lure = true)))
        odds(4096.0, hunt("lets-go-pikachu", "Gift Pokémon", true, HuntConfig(lure = true)))
    }

    @Test
    fun dynamaxAndBrilliantEncountersHaveDifferentModifiers() {
        odds(300.0, hunt("sword", "Dynamax Adventures"))
        odds(100.0, hunt("shield", "Dynamax Adventures", true))
        odds(455.56, hunt("sword", "Brilliant encounters", true, HuntConfig(battled = 500)))
        odds(4096.0, hunt("shield", "Max Raid Battles", true))
        odds(4096.0, hunt("sword", "Soft resets", true).copy(pokemon = "regidrago"))
        odds(4103.05, hunt("scarlet", "Tera Raid Battles", true))
        odds(4096.0, hunt("scarlet", "Fixed symbol encounters", true))
    }

    @Test
    fun zaHasThreeCharmRerollsAndFossilsIgnoreBonuses() {
        odds(1024.38, hunt("legends-z-a", charm = true))
        odds(
            585.57,
            hunt("legends-z-a", "Hyperspace encounters", true, HuntConfig(sparklingPower = 3)),
        )
        odds(4096.0, hunt("legends-z-a", "Fossil revival", true, HuntConfig(sparklingPower = 3)))
    }

    @Test
    fun dexNavIncludesTheRandomBoostAndMilestoneEncounter() {
        odds(4096.0, hunt("omega-ruby", "DexNav"))
        val regular = hunt("omega-ruby", "DexNav", true, HuntConfig(searchLevel = 999))
        odds(173.159145, regular, .00001)
        assertTrue(
            calculatedOdds(regular.copy(huntConfig = regular.huntConfig.copy(chain = 99))).first!! <
                calculatedOdds(regular).first!!
        )
    }

    @Test
    fun unsupportedMethodsNeedAnOverrideAndOldOverridesRemainExact() {
        val custom = hunt("soulsilver", "my custom hunt").copy(encounters = 12234, manualOdds = 512)
        assertEquals(512.0 to "manual odds", calculatedOdds(custom))
        assertNull(calculatedOdds(custom.copy(manualOdds = null)).first)
        assertFalse(hasShinyCharm("black"))
        assertTrue(hasShinyCharm("black-2"))
        assertFalse(charmApplies("brilliant-diamond", "Poké Radar"))
        assertFalse(charmApplies("scarlet", "Fixed symbol encounters"))
        assertFalse("Breeding" in huntMethods("legends-arceus"))
    }
}
