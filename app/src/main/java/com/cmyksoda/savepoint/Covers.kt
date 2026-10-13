package com.cmyksoda.savepoint

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val coverProviders = listOf("LaunchBox", "IGDB", "GameTDB")

data class CoverCandidate(val url: String, val provider: String, val label: String)

fun coverProvider(game: Game): String =
    game.coverProvider.ifBlank {
        when {
            game.cover.startsWith("local:") -> "Selected image"
            "launchbox-app.com" in game.cover -> "LaunchBox"
            "images.igdb.com" in game.cover -> "IGDB"
            "art.gametdb.com" in game.cover -> "GameTDB"
            "steamstatic.com" in game.cover -> "Publisher"
            "libretro-thumbnails" in game.cover -> "Libretro Thumbnails"
            "steamgriddb.com" in game.cover -> "SteamGridDB"
            "assets.nintendo.com" in game.cover -> "Nintendo"
            else -> "Selected image"
        }
    }

@Composable
fun CoverSelection(
    game: Game,
    initial: List<CoverCandidate>,
    artwork: Artwork,
    catalog: Catalog,
    endpoint: String,
    onPick: (CoverCandidate?) -> Unit,
    existing: Boolean = false,
) {
    var choices by
        remember(game.id) {
            mutableStateOf(
                if (existing) emptyList()
                else
                    initial.ifEmpty {
                        if (game.cover.isBlank()) emptyList()
                        else
                            listOf(
                                CoverCandidate(game.cover, coverProvider(game), coverProvider(game))
                            )
                    }
            )
        }
    var loading by remember(game.id) { mutableStateOf(emptySet<String>()) }
    var unavailable by remember(game.id) { mutableStateOf(emptySet<String>()) }
    var failed by remember(game.id) { mutableStateOf(emptySet<String>()) }
    var attempt by remember(game.id) { mutableStateOf(0) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    runCatching { artwork.select(uri) }
                        .onSuccess {
                            onPick(CoverCandidate(it, "Selected image", "Selected image"))
                        }
                        .onFailure { imageError = it.message ?: "Could not open this image" }
                }
        }
    LaunchedEffect(game.id, game.system, endpoint, attempt) {
        unavailable = emptySet()
        failed = emptySet()
        kotlinx.coroutines.coroutineScope {
            coverProviders
                .filter { provider ->
                    attempt > 0 || existing || choices.none { it.provider == provider }
                }
                .filter { endpoint.isNotBlank() || it == "GameTDB" }
                .forEach { provider ->
                    launch {
                        loading = loading + provider
                        try {
                            val found = catalog.covers(game.title, game.system, provider, endpoint)
                            choices = (choices + found).distinctBy { it.url }
                            if (found.isEmpty()) unavailable = unavailable + provider
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed = failed + provider
                        } finally {
                            loading = loading - provider
                        }
                    }
                }
        }
    }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            Text(game.title, style = MaterialTheme.typography.titleLarge)
            Text(game.system, style = MaterialTheme.typography.bodySmall)
            Text(
                "choose a cover",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (existing && game.cover.isNotBlank())
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "current cover · ${coverProvider(game)}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Art(
                            game.cover,
                            artwork,
                            "current cover",
                            system = game.system,
                            coverBounds = CoverSize(116f, 146f),
                        )
                    }
                }
            }
        (coverProviders + choices.map { it.provider }).distinct().forEach { provider ->
            val options = choices.filter { it.provider == provider }
            if (options.isNotEmpty() || provider in loading)
                item(key = provider) {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(provider, style = MaterialTheme.typography.labelLarge)
                            if (provider in loading)
                                Box(
                                    Modifier.height(166.dp).fillMaxWidth(),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            else
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(options, key = { it.url }) { cover ->
                                        Column(
                                            Modifier.width(116.dp).clickable { onPick(cover) },
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Art(
                                                cover.url,
                                                artwork,
                                                "choose $provider cover: ${cover.label}",
                                                system = game.system,
                                                coverBounds = CoverSize(116f, 146f),
                                            )
                                            Text(
                                                if (provider == "LaunchBox")
                                                    Regex("\\(([^()]*)\\)$")
                                                        .find(cover.label)
                                                        ?.groupValues
                                                        ?.get(1) ?: "front cover"
                                                else "use this cover",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                        }
                    }
                }
        }
        item {
            if (unavailable.isNotEmpty() && loading.isEmpty())
                Text(
                    "No matching cover from ${providerAlternatives(coverProviders.filter { it in unavailable })}. You can choose another cover or your own image.",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (failed.isNotEmpty() && loading.isEmpty())
                Text(
                    "Couldn't reach ${providerAlternatives(coverProviders.filter { it in failed })}. Retry, or choose an image from your phone.",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (endpoint.isBlank())
                Text(
                    "Set a catalog service address in settings to search LaunchBox or IGDB.",
                    style = MaterialTheme.typography.bodySmall,
                )
            imageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AeroButton(
                    onClick = { attempt++ },
                    enabled = loading.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loading.isEmpty()) "retry cover search" else "finding covers…")
                }
                PinkButton(
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("choose image from phone")
                }
                AeroButton(
                    onClick = {
                        onPick(
                            if (existing && game.cover.isNotBlank())
                                CoverCandidate(game.cover, coverProvider(game), "current cover")
                            else null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (existing) "keep current cover" else "add without a cover")
                }
            }
        }
    }
}
