package com.cmyksoda.savepoint

import java.text.Normalizer

data class CatalogResult(val game: Game, val covers: List<CoverCandidate>)

fun catalogTitleKey(title: String): String =
    Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

fun isVirtualConsoleId(system: String, id: String): Boolean {
    if (id.length != 4) return false
    return when (system) {
        "Wii" -> id.first() in "FJNLMPQEC"
        "Wii U" -> id.first() in "FJNPD"
        "Nintendo 3DS" -> id.first() in "PQRTU"
        else -> false
    }
}

fun groupCatalogResults(games: List<Game>): List<CatalogResult> =
    games
        .filterNot { it.source == "gametdb" && isVirtualConsoleId(it.system, it.sourceId) }
        .groupBy { catalogTitleKey(it.title) to it.system }
        .values
        .map { versions ->
            val metadata = versions.firstOrNull { it.source == "igdb" } ?: versions.first()
            CatalogResult(
                metadata,
                versions
                    .filter { it.cover.isNotBlank() }
                    .map { CoverCandidate(it.cover, coverProvider(it), coverProvider(it)) }
                    .distinctBy { it.url },
            )
        }
