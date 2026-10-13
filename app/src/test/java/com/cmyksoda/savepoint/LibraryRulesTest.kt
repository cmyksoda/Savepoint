package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class LibraryRulesTest {
    private val rhythm =
        Game(
            id = "rhythm",
            title = "Rhythm Heaven",
            place = Place.ACTIVE,
            notes = "general",
            position = 0,
        )
    private val delta = Game(id = "delta", title = "Deltarune", place = Place.ACTIVE, position = 1)
    private val metroid =
        Game(id = "metroid", title = "Metroid", place = Place.ACTIVE, position = 2)

    @Test
    fun pausePreservesGeneralNotesAndPromotesNextGame() {
        val games = LibraryRules.pause(listOf(rhythm, delta, metroid), rhythm.id, "stage 3")
        val paused = games.first { it.id == rhythm.id }
        assertEquals(Place.PAUSED, paused.place)
        assertEquals("general", paused.notes)
        assertEquals("stage 3", paused.pauseNote)
        assertEquals(delta.id, games.filter { it.place == Place.ACTIVE }.minBy { it.position }.id)
    }

    @Test
    fun resumeCanAppendOrBecomeFocusWithoutLosingPauseNote() {
        val games =
            LibraryRules.pause(listOf(rhythm, delta, metroid), rhythm.id, "remember controls")
        val queued = LibraryRules.move(games, rhythm.id, Place.ACTIVE)
        assertEquals(
            listOf("delta", "metroid", "rhythm"),
            queued.sortedBy { it.position }.map { it.id },
        )
        val focused = LibraryRules.move(queued, rhythm.id, Place.ACTIVE, true)
        assertEquals(
            listOf("rhythm", "delta", "metroid"),
            focused.sortedBy { it.position }.map { it.id },
        )
        assertEquals("remember controls", focused.first { it.id == rhythm.id }.pauseNote)
    }

    @Test
    fun queueReorderDoesNotTouchBacklogOrOtherShelf() {
        val backlog = Game(id = "backlog", title = "Someday", position = 15)
        val cozy =
            Game(
                id = "cozy",
                title = "Cozy",
                shelf = Shelf.EASY,
                place = Place.ACTIVE,
                position = 9,
            )
        val games = LibraryRules.reorder(listOf(rhythm, delta, backlog, cozy), delta.id, -1)
        assertEquals(0, games.first { it.id == delta.id }.position)
        assertEquals(backlog, games.first { it.id == backlog.id })
        assertEquals(cozy, games.first { it.id == cozy.id })
    }

    @Test
    fun unknownReleaseDatesSortLastInBothDirections() {
        val unknown = rhythm.copy(release = null)
        val old = delta.copy(release = 100)
        val recent = metroid.copy(release = 200)
        assertEquals(
            listOf(old, recent, unknown),
            sortedLibrary(listOf(unknown, recent, old), LibrarySort.RELEASE_OLD),
        )
        assertEquals(
            listOf(recent, old, unknown),
            sortedLibrary(listOf(unknown, old, recent), LibrarySort.RELEASE_NEW),
        )
    }

    @Test
    fun completionDoesNotChangeOtherGamesNotesOrShelf() {
        val games = LibraryRules.move(listOf(rhythm, delta), rhythm.id, Place.FINISHED)
        assertEquals("general", games.first { it.id == rhythm.id }.notes)
        assertEquals(delta, games.first { it.id == delta.id })
    }
}
