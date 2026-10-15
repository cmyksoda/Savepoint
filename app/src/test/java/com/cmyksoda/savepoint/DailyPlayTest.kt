package com.cmyksoda.savepoint

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DailyPlayTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val rhythm =
        Game(
            title = "Rhythm Heaven",
            place = Place.ACTIVE,
            notes = "Next: remix",
            playthroughs = listOf(Playthrough(thoughts = "Loved it")),
        )
    private val metroid = Game(title = "Metroid", place = Place.BACKLOG)

    @Test
    fun severalGamesMakeOnePlayedDayAndDoNotChangeShelvesOrThoughts() {
        val result =
            saveDailyPlays(
                listOf(rhythm, metroid),
                today,
                mapOf(rhythm.id to null, metroid.id to 75),
                today,
            )
        assertEquals(setOf(today), playedDates(result))
        assertEquals(rhythm.copy(dailyPlays = listOf(DailyPlay(today.toString()))), result[0])
        assertEquals(metroid.copy(dailyPlays = listOf(DailyPlay(today.toString(), 75))), result[1])
        assertEquals(1, currentPlayStreak(playedDates(result), today))
    }

    @Test
    fun editingDayReplacesDurationsAndDeselectionOnlyRemovesThatDate() {
        val before =
            listOf(
                rhythm.copy(dailyPlays = listOf(DailyPlay(today.minusDays(1).toString(), 20))),
                metroid,
            )
        val saved = saveDailyPlays(before, today, mapOf(rhythm.id to 45, metroid.id to null), today)
        val changed = saveDailyPlays(saved, today, mapOf(rhythm.id to null), today)
        assertEquals(
            listOf(DailyPlay(today.minusDays(1).toString(), 20), DailyPlay(today.toString())),
            changed[0].dailyPlays,
        )
        assertTrue(changed[1].dailyPlays.isEmpty())
        assertEquals(changed, saveDailyPlays(changed, today, mapOf(rhythm.id to null), today))
    }

    @Test
    fun backfillingRestoresStreakAndTodayDoesNotNeedToBeLoggedYet() {
        val dates = setOf(today.minusDays(1), today.minusDays(3))
        assertEquals(1, currentPlayStreak(dates, today))
        assertEquals(3, currentPlayStreak(dates + today.minusDays(2), today))
        assertEquals(4, currentPlayStreak(dates + today.minusDays(2) + today, today))
        assertEquals(0, currentPlayStreak(dates, today.plusDays(1)))
        assertEquals(3, longestPlayStreak(dates + today.minusDays(2)))
    }

    @Test
    fun milestonesSurviveGapsAndMultipleGamesDontInflateThem() {
        val dates = (1L..7L).map { today.minusDays(it) }.toSet()
        val milestones = reachedPlayMilestones(dates)
        assertTrue(milestones.any { it.key == "streak:7" })
        assertTrue(milestones.any { it.key == "days:7" })
        assertEquals(milestones, reachedPlayMilestones(dates))
        assertFalse(
            reachedPlayMilestones(dates + today.minusDays(20)).any { it.key == "streak:14" }
        )
    }

    @Test
    fun optionalTimeAndOldLibrariesRemainAbsentRatherThanZero() {
        assertNull(durationMinutes("", ""))
        assertEquals(75, durationMinutes("1", "15"))
        assertEquals(0, durationMinutes("", "0"))
        assertEquals("1 hr 15 min", playDuration(75))
        val legacy = JsonCodec.game(rhythm).also { it.remove("dailyPlays") }
        assertTrue(JsonCodec.game(legacy).dailyPlays.isEmpty())
        val saved =
            rhythm.copy(
                dailyPlays =
                    listOf(
                        DailyPlay(today.toString()),
                        DailyPlay(today.minusDays(1).toString(), 75),
                    )
            )
        assertEquals(saved, JsonCodec.game(JSONObject(JsonCodec.game(saved).toString())))
    }

    @Test
    fun malformedDatesDurationsAndDuplicateDaysRejectBeforeImport() {
        for (bad in
            listOf(
                DailyPlay("2026-02-30"),
                DailyPlay("not a date"),
                DailyPlay(today.toString(), -1),
            )) {
            assertThrows(RuntimeException::class.java) { validateDailyPlay(bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { durationMinutes("1", "60") }
        assertThrows(RuntimeException::class.java) { durationMinutes("-1", "") }
        assertThrows(IllegalArgumentException::class.java) {
            durationMinutes(Long.MAX_VALUE.toString(), "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            saveDailyPlays(listOf(rhythm), today.plusDays(1), mapOf(rhythm.id to null), today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            saveDailyPlays(listOf(rhythm), today, mapOf("deleted" to null), today)
        }
        val duplicate =
            rhythm.copy(
                dailyPlays = listOf(DailyPlay(today.toString()), DailyPlay(today.toString(), 30))
            )
        assertThrows(IllegalArgumentException::class.java) {
            JsonCodec.game(JsonCodec.game(duplicate))
        }
    }
}
