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
    fun finishingAnAddDiscardsCoverAndFormStepsButKeepsTheAddMenu() {
        for (step in listOf("catalog", "form")) {
            val history = ScreenHistory(listOf("backlog", "add", step))
            history.finishAdding("details")
            assertEquals(listOf("backlog", "add", "details"), history.savedRoutes)
            history.open(history.previous)
            assertEquals("backlog", history.previous)
        }
        val hunt = ScreenHistory(listOf("home", "add", "hunt-add", "form"))
        hunt.finishAdding("details")
        assertEquals("hunt-add", hunt.previous)
    }
}
