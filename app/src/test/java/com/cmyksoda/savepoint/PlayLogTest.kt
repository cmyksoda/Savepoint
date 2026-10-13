package com.cmyksoda.savepoint

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayLogTest {
    private val game =
        Game(
            id = "rhythm",
            title = "Rhythm Heaven",
            place = Place.ACTIVE,
            notes = "general",
            pauseNote = "return here",
        )
    private val first =
        Playthrough(
            id = "first",
            finishedAt = 100,
            feeling = Feeling.LOVED,
            thoughts = "so much fun",
        )

    @Test
    fun finishingSavesThoughtsSeparatelyAndPromotesNextGame() {
        val next = Game(id = "delta", title = "Deltarune", place = Place.ACTIVE, position = 1)
        val games = LibraryRules.finish(listOf(game, next), game.id, first, null)
        val finished = games.first { it.id == game.id }
        assertEquals(Place.FINISHED, finished.place)
        assertEquals(listOf(first), finished.playthroughs)
        assertEquals("general", finished.notes)
        assertEquals("return here", finished.pauseNote)
        assertTrue(finished.inPlayLog)
        assertNull(finished.recordedHours)
        assertNull(finished.replayCount)
        assertEquals("delta", games.filter { it.place == Place.ACTIVE }.minBy { it.position }.id)
    }

    @Test
    fun replayStaysInLogAndSecondCompletionPreservesFirstEntry() {
        val completed = LibraryRules.finish(listOf(game), game.id, first, null)
        val active = LibraryRules.move(completed, game.id, Place.ACTIVE, true)
        assertTrue(active.single().inPlayLog)
        val second =
            Playthrough(
                id = "second",
                finishedAt = 200,
                feeling = Feeling.LIKED,
                thoughts = "noticed something new",
                hours = 3.5,
            )
        val replayed = LibraryRules.finish(active, game.id, second, 1).single()
        assertEquals(listOf(first, second), replayed.playthroughs)
        assertEquals(3.5, replayed.recordedHours!!, 0.00001)
        assertEquals(1, replayed.replayCount)
    }

    @Test
    fun editingEarlierThoughtsDoesNotChangeOtherEntriesOrDates() {
        val second = Playthrough(id = "second", finishedAt = 200, thoughts = "second")
        val initial = game.copy(playthroughs = listOf(first, second), hasPlayed = true)
        val edited =
            LibraryRules.editPlaythrough(
                    listOf(initial),
                    game.id,
                    first.copy(finishedAt = 999, thoughts = "edited", hours = 2.25),
                    null,
                )
                .single()
        assertEquals(100L, edited.playthroughs.first().finishedAt)
        assertEquals("edited", edited.playthroughs.first().thoughts)
        assertEquals(second, edited.playthroughs.last())
        assertEquals("general", edited.notes)
    }

    @Test
    fun duplicateSaveCannotCreateDuplicatePlaythrough() {
        val once = LibraryRules.finish(listOf(game), game.id, first, null)
        assertEquals(once, LibraryRules.finish(once, game.id, first, null))
    }

    @Test
    fun historyAndOptionalFieldsSurviveJsonRoundTrip() {
        val logged =
            game.copy(
                playthroughs =
                    listOf(first.copy(hours = 2.5), first.copy(id = "second", finishedAt = null)),
                hasPlayed = true,
                replayCount = 3,
            )
        assertEquals(logged, JsonCodec.game(JSONObject(JsonCodec.game(logged).toString())))
    }

    @Test
    fun oldFinishedSaveRemainsInLogEvenAfterStartingReplay() {
        val legacy = JsonCodec.game(game.copy(place = Place.FINISHED))
        legacy.remove("playthroughs")
        legacy.remove("hasPlayed")
        legacy.remove("replayCount")
        val restored = JsonCodec.game(legacy)
        assertTrue(restored.inPlayLog)
        assertTrue(restored.playthroughs.isEmpty())
        assertTrue(
            LibraryRules.move(listOf(restored), restored.id, Place.ACTIVE).single().inPlayLog
        )
    }

    @Test
    fun clearingMetricsMakesThemAbsentWithoutDeletingThoughts() {
        val original = game.copy(playthroughs = listOf(first.copy(hours = 4.0)), replayCount = 2)
        val cleared = LibraryRules.editPlaythrough(listOf(original), game.id, first, null).single()
        assertNull(cleared.recordedHours)
        assertNull(cleared.replayCount)
        assertEquals("so much fun", cleared.playthroughs.single().thoughts)
    }

    @Test
    fun invalidHoursAndCountsCannotBeSaved() {
        assertThrows(IllegalArgumentException::class.java) {
            LibraryRules.finish(listOf(game), game.id, first.copy(hours = Double.NaN), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LibraryRules.finish(listOf(game), game.id, first.copy(hours = -1.0), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LibraryRules.finish(listOf(game), game.id, first, -1)
        }
    }
}
