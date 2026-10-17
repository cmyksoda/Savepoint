package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class NavigationTest {
    @Test
    fun backRetracesNestedPagesToTheOriginalLibraryTab() {
        val history = ScreenHistory()
        var route by history
        route = "backlog"
        route = "details"
        route = "form"
        route = "cover"
        route = history.previous
        assertEquals("form", route)
        route = history.previous
        assertEquals("details", route)
        route = history.previous
        assertEquals("backlog", route)
        assertEquals(listOf("backlog"), history.savedRoutes)
    }

    @Test
    fun tabSwitchesReplaceHistoryAndSavedHistoryRestoresTheReturnPath() {
        val history = ScreenHistory()
        history.open("paused")
        history.open("details")
        history.open("cover")
        val restored = ScreenHistory(history.savedRoutes)
        assertEquals("details", restored.previous)
        restored.open("log")
        assertEquals(listOf("log"), restored.savedRoutes)
        assertEquals("home", restored.previous)
    }

    @Test
    fun libraryActionsReturnToThePageThatOpenedTheGame() {
        val history = ScreenHistory()
        history.open("backlog")
        assertEquals("backlog", history.libraryReturn)
        history.open("details")
        history.open("cover")
        assertEquals("backlog", history.libraryReturn)
        history.open("home")
        history.open("find")
        history.open("details")
        assertEquals("find", history.libraryReturn)
    }

    @Test
    fun finishingAnAddReturnsToShelvesWithoutReopeningTheSearchOrForm() {
        for (origin in listOf("home", "backlog", "paused", "log")) {
            for (step in listOf("catalog", "form")) {
                for (destination in listOf("details", "journal")) {
                    val history = ScreenHistory(listOf(origin, "add", step))
                    history.finishAdding(destination)
                    assertEquals(listOf("home", destination), history.savedRoutes)
                    history.open(history.previous)
                    assertEquals(listOf("home"), history.savedRoutes)
                }
            }
        }
        val hunt = ScreenHistory(listOf("home", "add", "hunt-add", "form"))
        hunt.finishAdding("details")
        assertEquals("home", hunt.previous)
    }
}
