package com.cmyksoda.savepoint

import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun collectionNames(games: List<Game>, settings: Settings): List<String> =
    (settings.collectionNames + games.map { it.collection }.filter { it.isNotBlank() }).sortedBy {
        it.lowercase()
    }

fun setCollectionMembers(games: List<Game>, name: String, ids: Set<String>): List<Game> {
    var order = games.filter { it.collection == name }.maxOfOrNull { it.collectionOrder } ?: 0
    return games.map { game ->
        when {
            game.id in ids && game.collection != name ->
                game.copy(collection = name, collectionOrder = ++order)
            game.id !in ids && game.collection == name ->
                game.copy(collection = "", collectionOrder = 0)
            else -> game
        }
    }
}

fun reorderCollection(games: List<Game>, name: String, id: String, direction: Int): List<Game> {
    val row =
        games
            .filter { it.collection == name }
            .sortedWith(compareBy<Game> { it.collectionOrder }.thenBy { it.added })
            .toMutableList()
    val from = row.indexOfFirst { it.id == id }
    val to = from + direction
    if (from < 0 || to !in row.indices) return games
    val moving = row.removeAt(from)
    row.add(to, moving)
    val orders = row.mapIndexed { index, game -> game.id to index + 1 }.toMap()
    return games.map { game -> orders[game.id]?.let { game.copy(collectionOrder = it) } ?: game }
}

@Composable
fun CollectionsScreen(
    games: List<Game>,
    settings: Settings,
    artwork: Artwork,
    onCreate: (String) -> Unit,
    onMembers: (String, Set<String>) -> Unit,
    onReorder: (String, String, Int) -> Unit,
    onOpen: (String) -> Unit,
) {
    val names = collectionNames(games, settings)
    var selected by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val row = games.filter { it.collection == selected }.sortedBy { it.collectionOrder }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selected.isEmpty()) {
            PinkButton(
                onClick = {
                    name = ""
                    creating = true
                }
            ) {
                Text("new collection")
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                if (names.isEmpty())
                    item { Text("Keep a series or a group of games together here.") }
                items(names, key = { it }) { collection ->
                    Glass(
                        Modifier.fillMaxWidth().clickable { selected = collection }.animateItem()
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(collection, fontWeight = FontWeight.Medium)
                                Text(
                                    "${games.count { it.collection == collection }} games",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            AeroIcon(AeroSymbol.NEXT, Modifier.size(17.dp))
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(selected, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { selected = "" }) { Text("all collections") }
            }
            PinkButton(
                onClick = {
                    checked = row.map { it.id }.toSet()
                    query = ""
                    picking = true
                }
            ) {
                Text("choose games")
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                if (row.isEmpty())
                    item {
                        Text("Choose games from your shelves, backlog, paused games, or play log.")
                    }
                items(row, key = { it.id }) { game ->
                    Glass(
                        Modifier.fillMaxWidth()
                            .animateItem(placementSpec = spring(stiffness = 350f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpen(game.id) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Art(
                                    game.cover,
                                    artwork,
                                    game.title,
                                    system = game.system,
                                    coverBounds = CoverSize(54f, 72f),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(game.title, fontWeight = FontWeight.Medium)
                                    Text(game.system, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row {
                                TextButton(
                                    onClick = { onReorder(selected, game.id, -1) },
                                    enabled = row.first().id != game.id,
                                ) {
                                    Text("↑ earlier")
                                }
                                TextButton(
                                    onClick = { onReorder(selected, game.id, 1) },
                                    enabled = row.last().id != game.id,
                                ) {
                                    Text("↓ later")
                                }
                                TextButton(
                                    onClick = {
                                        onMembers(
                                            selected,
                                            row.filter { it.id != game.id }.map { it.id }.toSet(),
                                        )
                                    }
                                ) {
                                    Text("remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating)
        AeroDialog(
            onDismissRequest = { creating = false },
            title = { Text("new collection") },
            text = {
                AeroTextField(
                    name,
                    { name = it },
                    label = "collection name",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                PinkButton(
                    onClick = {
                        val entered = name.trim()
                        val existing = names.firstOrNull { it.equals(entered, true) }
                        selected = existing ?: entered
                        if (existing == null) onCreate(entered)
                        creating = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("create")
                }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("cancel") } },
        )
    if (picking)
        AeroDialog(
            onDismissRequest = { picking = false },
            title = { Text("games in $selected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AeroTextField(
                        query,
                        { query = it },
                        label = "find a game",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(
                            games
                                .filter { it.title.contains(query, true) }
                                .sortedBy { it.title.lowercase() },
                            key = { it.id },
                        ) { game ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(game.id in checked, role = Role.Checkbox) {
                                        checked =
                                            if (game.id in checked) checked - game.id
                                            else checked + game.id
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(game.id in checked, null, Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(game.title)
                                    if (game.collection.isNotBlank() && game.collection != selected)
                                        Text(
                                            "in ${game.collection}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                PinkButton(
                    onClick = {
                        onMembers(selected, checked)
                        picking = false
                    }
                ) {
                    Text("save collection")
                }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("cancel") } },
        )
}

@Composable
fun FindGames(
    games: List<Game>,
    artwork: Artwork,
    onOpen: (String) -> Unit,
    onCollections: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results =
        games
            .filter { it.title.contains(query, true) || it.pokemon.contains(query, true) }
            .sortedBy { it.title.lowercase() }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AeroTextField(
            query,
            { query = it },
            label = "search your games",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onCollections) { Text("manage collections") }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(results, key = { it.id }) { game ->
                Glass(Modifier.fillMaxWidth().clickable { onOpen(game.id) }.animateItem()) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Art(
                            if (game.shelf == Shelf.HUNTS) game.sprite else game.cover,
                            artwork,
                            game.title,
                            system = game.system,
                            sprite = game.shelf == Shelf.HUNTS,
                            coverBounds = CoverSize(54f, 72f),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(game.title, fontWeight = FontWeight.Medium)
                            Text(
                                when (game.place) {
                                    Place.ACTIVE -> game.shelf.label
                                    Place.FINISHED -> "play log"
                                    else -> game.place.name.lowercase()
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
