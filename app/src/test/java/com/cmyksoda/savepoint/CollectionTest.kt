package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class CollectionTest {
    @Test
    fun addingAndRemovingMembersPreservesTheirShelfAndPersonalData() {
        val a =
            Game(
                title = "A",
                place = Place.PAUSED,
                notes = "a note",
                collection = "series",
                collectionOrder = 1,
            )
        val b =
            Game(
                title = "B",
                place = Place.FINISHED,
                playthroughs = listOf(Playthrough(thoughts = "loved it")),
            )
        val result = setCollectionMembers(listOf(a, b), "series", setOf(b.id))
        assertEquals(
            a,
            result[0].copy(collection = a.collection, collectionOrder = a.collectionOrder),
        )
        assertEquals(
            b,
            result[1].copy(collection = b.collection, collectionOrder = b.collectionOrder),
        )
        assertEquals("", result[0].collection)
        assertEquals("series", result[1].collection)
    }

    @Test
    fun collectionReorderingIsIndependentOfShelfOrder() {
        val a = Game(title = "A", position = 8, collection = "series", collectionOrder = 1)
        val b = Game(title = "B", position = 3, collection = "series", collectionOrder = 2)
        val reordered = reorderCollection(listOf(a, b), "series", b.id, -1)
        assertEquals(listOf("B", "A"), reordered.sortedBy { it.collectionOrder }.map { it.title })
        assertEquals(listOf(8, 3), reordered.map { it.position })
        assertEquals(listOf(a, b), reorderCollection(listOf(a, b), "series", a.id, -1))
    }

    @Test
    fun emptyCollectionsAndCoverProvidersSurviveSerialization() {
        val settings = Settings(collectionNames = setOf("empty collection"))
        assertEquals(settings, JsonCodec.settings(JsonCodec.settings(settings)))
        val game = Game(title = "A", coverProvider = "LaunchBox")
        assertEquals(game, JsonCodec.game(JsonCodec.game(game)))
        val older = JsonCodec.settings(settings).also { it.remove("collectionNames") }
        assertTrue(JsonCodec.settings(older).collectionNames.isEmpty())
    }
}
