package com.cmyksoda.savepoint

import java.text.Normalizer

data class CatalogResult(val game: Game, val covers: List<CoverCandidate>)

fun catalogTitleKey(title: String): String =
    Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

fun groupCatalogResults(games: List<Game>): List<CatalogResult> =
    games
        .groupBy { catalogTitleKey(it.title) to it.system }
        .values
        .map { versions ->
            CatalogResult(
                versions.firstOrNull { it.source == "launchbox" } ?: versions.first(),
                versions
                    .filter { it.cover.isNotBlank() }
                    .map {
                        val provider = coverProvider(it)
                        CoverCandidate(
                            it.cover,
                            provider,
                            when (provider) {
                                "Steam" -> "Library art"
                                "App Store" -> "App icon"
                                else -> "Front cover"
                            },
                        )
                    }
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
