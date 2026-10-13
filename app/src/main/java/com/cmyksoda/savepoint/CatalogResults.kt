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

fun catalogSystemName(system: String): String =
    when (system.trim().lowercase()) {
        "nintendo entertainment system",
        "famicom",
        "nes" -> "NES"
        "super nintendo entertainment system",
        "super nintendo",
        "super famicom",
        "snes" -> "SNES"
        "nintendo gamecube",
        "gamecube" -> "GameCube"
        "nintendo wii",
        "wii" -> "Wii"
        "nintendo wii u",
        "wii u" -> "Wii U"
        "nintendo game boy",
        "game boy" -> "Game Boy"
        "nintendo game boy color",
        "game boy color" -> "Game Boy Color"
        "nintendo game boy advance",
        "game boy advance" -> "Game Boy Advance"
        "nintendo virtual boy",
        "virtual boy" -> "Virtual Boy"
        "windows",
        "linux",
        "mac os",
        "pc (microsoft windows)",
        "pc" -> "PC"
        else -> system.trim()
    }

fun providerAlternatives(providers: Collection<String>): String =
    when (providers.size) {
        0 -> ""
        1 -> providers.first()
        2 -> providers.joinToString(" or ")
        else -> providers.toList().dropLast(1).joinToString(", ") + ", or " + providers.last()
    }

fun mergeCatalogResults(
    current: List<CatalogResult>,
    page: List<CatalogResult>,
): List<CatalogResult> {
    val merged =
        current.associateBy { catalogTitleKey(it.game.title) to it.game.system }.toMutableMap()
    for (incoming in page) {
        val key = catalogTitleKey(incoming.game.title) to incoming.game.system
        val previous = merged[key]
        merged[key] =
            if (previous == null) incoming
            else
                CatalogResult(
                    if (previous.game.source == "igdb" || incoming.game.source != "igdb")
                        previous.game
                    else incoming.game,
                    (previous.covers + incoming.covers).distinctBy { it.url },
                )
    }
    return merged.values.toList()
}

fun gameTdbCoverTitle(title: String): String =
    title
        .replace(
            Regex("\\s*[-–—:]?\\s*\\(?Nintendo Switch 2 Edition\\)?$", RegexOption.IGNORE_CASE),
            "",
        )
        .trim()
