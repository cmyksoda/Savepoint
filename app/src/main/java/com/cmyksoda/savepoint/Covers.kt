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
fun CoverPicker(
    game: Game,
    artwork: Artwork,
    catalog: Catalog,
    endpoint: String,
    onPick: (CoverCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var provider by remember {
        mutableStateOf(coverProvider(game).takeIf { it in coverProviders } ?: "LaunchBox")
    }
    var results by remember { mutableStateOf<List<CoverCandidate>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        onPick(
                            CoverCandidate(artwork.select(uri), "Selected image", "Selected image")
                        )
                    } catch (e: Exception) {
                        message = e.message
                    }
                }
        }
    AeroDialog(
        onDismissRequest = onDismiss,
        title = { Text("cover art") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${game.title} · ${game.system}", style = MaterialTheme.typography.bodySmall)
                Choice("provider", provider, coverProviders) {
                    provider = it
                    results = emptyList()
                    message = null
                }
                PinkButton(
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                results =
                                    catalog.covers(game.title, game.system, provider, endpoint)
                                if (results.isEmpty())
                                    message =
                                        "No matching front covers found for this game and system."
                            } catch (e: Exception) {
                                message = e.message ?: "Cover search unavailable."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && game.title.isNotBlank(),
                ) {
                    Text(if (busy) "finding covers…" else "find covers")
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (results.isNotEmpty())
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(results, key = { it.url }) { cover ->
                            Column(
                                Modifier.width(116.dp).clickable { onPick(cover) },
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Art(
                                    cover.url,
                                    artwork,
                                    cover.label,
                                    system = game.system,
                                    coverBounds = CoverSize(116f, 156f),
                                )
                                Text(
                                    if (cover.provider == "LaunchBox")
                                        Regex("\\(([^()]*)\\)$")
                                            .find(cover.label)
                                            ?.groupValues
                                            ?.get(1) ?: "front cover"
                                    else cover.provider,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                TextButton(onClick = { picker.launch("image/*") }) {
                    Text("choose image from phone")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("done") } },
    )
}
