package com.cmyksoda.savepoint

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CatalogSearchTest {
    @Test
    fun aFastProviderAppearsWhileAnotherIsStillLoading() = runBlocking {
        val blocked = CompletableDeferred<Unit>()
        val game = Game(id = "metroid", title = "Metroid")
        val slow = flow {
            blocked.await()
            emit(CatalogPage(emptyList(), null))
        }
        val first =
            withTimeout(5000) {
                mergeCatalogPages(listOf(slow, flowOf(CatalogPage(listOf(game), null)))).first()
            }
        assertEquals(listOf(game), first.games)
        assertFalse(blocked.isCompleted)
    }

    @Test
    fun refreshingOneProviderReplacesItsCachedEntriesAndKeepsTheOtherProvider() =
        runBlocking {
            val cached = Game(id = "cached", title = "Cached title")
            val fresh = Game(id = "fresh", title = "Updated title")
            val broad = Game(id = "broad", title = "Broad catalog title")
            val cacheSent = CompletableDeferred<Unit>()
            val broadSent = CompletableDeferred<Unit>()
            val local = flow {
                emit(CatalogPage(listOf(cached), null))
                cacheSent.complete(Unit)
                broadSent.await()
                emit(CatalogPage(listOf(fresh), null))
            }
            val online = flow {
                cacheSent.await()
                emit(CatalogPage(listOf(broad), null))
                broadSent.complete(Unit)
            }
            val updates = withTimeout(5000) { mergeCatalogPages(listOf(local, online)).toList() }
            assertEquals(listOf(cached), updates.first().games)
            assertEquals(setOf(fresh, broad), updates.last().games.toSet())
            assertEquals(3, updates.size)
        }

    @Test
    fun anUnavailableProviderDoesNotRemoveWorkingResults() = runBlocking {
        val game = Game(id = "ultra-moon", title = "Pokémon Ultra Moon")
        val last =
            mergeCatalogPages(
                    listOf(
                        flowOf(CatalogPage(listOf(game), null)),
                        flowOf(CatalogPage(emptyList(), "A source is unavailable")),
                    )
                )
                .toList()
                .last()
        assertEquals(listOf(game), last.games)
        assertEquals("A source is unavailable", last.warning)
    }

    @Test
    fun leavingSearchCancelsTheProvidersStillWaiting() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val pending =
            flow<CatalogPage> {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        val ready = flow {
            started.await()
            emit(CatalogPage(listOf(Game(title = "Metroid")), null))
        }
        withTimeout(5000) {
            mergeCatalogPages(listOf(pending, ready)).first()
            cancelled.await()
        }
    }
}
