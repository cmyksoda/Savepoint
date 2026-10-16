package com.cmyksoda.savepoint

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JsonCodecTest {
    @Test
    fun huntModifiersRoundTripAndOlderHuntsKeepTheirCountersAndOverrides() {
        val original =
            Game(
                title = "Pokémon Platinum",
                pokemon = "shaymin",
                shelf = Shelf.HUNTS,
                pokemonVersion = "platinum",
                method = "Runaways",
                encounters = 18003,
                manualOdds = 8192,
                huntConfig =
                    HuntConfig(
                        research = 2,
                        chain = 60,
                        sparklingPower = 3,
                        lure = true,
                        searchLevel = 999,
                        battled = 500,
                        diglett = true,
                        radarMusic = true,
                    ),
            )
        val encoded = JsonCodec.game(original)
        assertEquals(original, JsonCodec.game(JSONObject(encoded.toString())))
        encoded.remove("huntConfig")
        assertEquals(original.copy(huntConfig = HuntConfig()), JsonCodec.game(encoded))
        encoded.put("huntConfig", JSONObject().put("chain", -1))
        assertThrows(IllegalArgumentException::class.java) { JsonCodec.game(encoded) }
    }

    @Test
    fun pauseAndGeneralNotesSurviveSerializationIndependently() {
        val original =
            Game(
                title = "Rhythm Heaven",
                place = Place.PAUSED,
                notes = "general thoughts 💕",
                pauseNote = "next: stage 3",
                collection = "2d metroid",
                collectionOrder = 2,
                release = 12345678,
                encounters = 400,
                manualOdds = 8192,
            )
        assertEquals(original, JsonCodec.game(JSONObject(JsonCodec.game(original).toString())))
    }

    @Test
    fun unknownDateAndOddsRemainUnknownInBackup() {
        val original = Game(title = "AM2R")
        val restored = JsonCodec.game(JSONObject(JsonCodec.game(original).toString()))
        assertNull(restored.release)
        assertNull(restored.manualOdds)
    }

    @Test
    fun invalidCountersAndEnumsRejectBeforeImport() {
        val invalid = JsonCodec.game(Game(title = "Hunt")).put("encounters", -1)
        assertThrows(IllegalArgumentException::class.java) { JsonCodec.game(invalid) }
        val wrongShelf = JsonCodec.game(Game(title = "Hunt")).put("shelf", "missing")
        assertThrows(IllegalArgumentException::class.java) { JsonCodec.game(wrongShelf) }
    }

    @Test
    fun settingsRoundTripAndBadWindowsReject() {
        val settings =
            Settings(
                theme = "dark",
                gridViews = setOf("backlog", "log"),
                reminders = true,
                days = setOf(2, 5),
                sort = LibrarySort.SYSTEM,
            )
        assertEquals(
            settings,
            JsonCodec.settings(JSONObject(JsonCodec.settings(settings).toString())),
        )
        val invalid = JsonCodec.settings(settings).put("endMinute", settings.startMinute)
        assertThrows(IllegalArgumentException::class.java) { JsonCodec.settings(invalid) }
    }

    @Test
    fun oldSettingsDefaultToListAndConcurrentViewChangesPreserveOtherTabs() {
        val previous = Settings()
        assertTrue(
            JsonCodec.settings(JsonCodec.settings(previous).apply { remove("gridViews") })
                .gridViews
                .isEmpty()
        )
        val current = previous.copy(gridViews = setOf("paused"), reminders = true)
        val updated = mergeSettings(current, previous, previous.copy(gridViews = setOf("backlog")))
        assertEquals(setOf("backlog", "paused"), updated.gridViews)
        assertTrue(updated.reminders)
    }
}
