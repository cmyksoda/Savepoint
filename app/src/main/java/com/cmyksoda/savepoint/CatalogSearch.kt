package com.cmyksoda.savepoint

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CatalogPage(val games: List<Game>, val warning: String?)

internal fun mergeCatalogPages(sources: List<Flow<CatalogPage>>): Flow<CatalogPage> = channelFlow {
    val pages = MutableList(sources.size) { CatalogPage(emptyList(), null) }
    val mutex = Mutex()
    sources.forEachIndexed { index, source ->
        launch {
            source.collect { page ->
                mutex.withLock {
                    pages[index] = page
                    send(
                        CatalogPage(pages.flatMap { it.games }, pages.firstNotNullOfOrNull { it.warning })
                    )
                }
            }
        }
    }
}
