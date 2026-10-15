package com.cmyksoda.savepoint

import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

data class CollectionRename(val games: List<Game>, val settings: Settings)

fun collectionRenameSettings(settings: Settings, previous: String, name: String): Settings =
    settings.copy(
        collectionNames = (settings.collectionNames - previous) + name,
        collectionFilter =
            if (settings.collectionFilter == previous) name else settings.collectionFilter,
    )

fun renameCollection(
    games: List<Game>,
    settings: Settings,
    previous: String,
    entered: String,
): CollectionRename {
    val name = entered.trim()
    val names = collectionNames(games, settings)
    require(previous in names) { "This collection no longer exists" }
    require(name.isNotBlank()) { "Enter a collection name" }
    require(names.none { it != previous && it.equals(name, true) }) {
        "A collection already has that name"
    }
    return CollectionRename(
        games.map { if (it.collection == previous) it.copy(collection = name) else it },
        collectionRenameSettings(settings, previous, name),
    )
}

private data class CollectionViewport(val ids: List<String>, val index: Int, val offset: Int)

@Composable
private fun CollectionCollage(games: List<Game>, artwork: Artwork) {
    val covers = games.sortedBy { it.collectionOrder }.filter { it.cover.isNotBlank() }.take(4)
    Box(
        Modifier.size(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .glassMaterial(LocalAeroDark.current, 12, elevated = false),
        contentAlignment = Alignment.Center,
    ) {
        if (covers.isEmpty()) AeroIcon(AeroSymbol.GRID, Modifier.size(26.dp))
        else {
            val bands = if (covers.size <= 2) listOf(covers) else covers.chunked(2)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                bands.forEach { band ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        band.forEach { game ->
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                Art(
                                    game.cover,
                                    artwork,
                                    game.title,
                                    Modifier.fillMaxSize(),
                                    showPlaceholderTitle = false,
                                    imageScale = ContentScale.Crop,
                                    cornerRadius = 0.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionsScreen(
    games: List<Game>,
    settings: Settings,
    artwork: Artwork,
    selected: String,
    onCreate: (String) -> Unit,
    onRename: (String, String, (String?) -> Unit) -> Unit,
    onMembers: (String, Set<String>) -> Unit,
    onReorder: (String, String, Int, (Boolean) -> Unit) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val names = collectionNames(games, settings)
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var renameBusy by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val row = games.filter { it.collection == selected }.sortedBy { it.collectionOrder }
    val listState = rememberLazyListState()
    var pendingMove by remember { mutableStateOf<CollectionViewport?>(null) }
    fun move(id: String, direction: Int) {
        if (pendingMove != null) return
        pendingMove =
            CollectionViewport(
                row.map { it.id },
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        onReorder(selected, id, direction) { changed -> if (!changed) pendingMove = null }
    }
    val order = row.map { it.id }
    SideEffect {
        pendingMove?.let { anchor ->
            if (order != anchor.ids) {
                listState.requestScrollToItem(anchor.index, anchor.offset)
                pendingMove = null
            }
        }
    }
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
                        Modifier.fillMaxWidth()
                            .clickable { onOpenCollection(collection) }
                            .animateItem()
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CollectionCollage(games.filter { it.collection == collection }, artwork)
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
                AeroIconButton(
                    AeroSymbol.EDIT,
                    "rename collection",
                    {
                        name = selected
                        renameError = null
                        renaming = true
                    },
                    compact = true,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PinkButton(
                    onClick = {
                        checked = row.map { it.id }.toSet()
                        query = ""
                        picking = true
                    }
                ) {
                    Text("choose games")
                }
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                if (row.isEmpty())
                    item {
                        Text(
                            "Choose games from your shelves, backlog, paused games, or finished games."
                        )
                    }
                items(row, key = { it.id }) { game ->
                    Glass(
                        Modifier.fillMaxWidth()
                            .animateItem(placementSpec = spring(stiffness = 350f))
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpen(game.id) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                GameThumbnail(game, artwork)
                                Column(Modifier.weight(1f)) {
                                    Text(game.title, fontWeight = FontWeight.Medium)
                                    Text(
                                        game.system,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AeroButton(
                                    onClick = { move(game.id, -1) },
                                    modifier = Modifier.weight(1f),
                                    enabled = pendingMove == null && row.first().id != game.id,
                                ) {
                                    AeroIcon(AeroSymbol.UP, Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("move up", maxLines = 1)
                                }
                                AeroButton(
                                    onClick = { move(game.id, 1) },
                                    modifier = Modifier.weight(1f),
                                    enabled = pendingMove == null && row.last().id != game.id,
                                ) {
                                    AeroIcon(AeroSymbol.DOWN, Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("move down", maxLines = 1)
                                }
                                AeroIconButton(
                                    AeroSymbol.REMOVE,
                                    "remove ${game.title} from collection",
                                    {
                                        onMembers(
                                            selected,
                                            row.filter { it.id != game.id }.map { it.id }.toSet(),
                                        )
                                    },
                                )
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
                        if (existing == null) onCreate(entered)
                        creating = false
                        onOpenCollection(existing ?: entered)
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("create")
                }
            },
            dismissButton = { AeroButton(onClick = { creating = false }) { Text("cancel") } },
        )
    if (renaming)
        AeroDialog(
            onDismissRequest = { if (!renameBusy) renaming = false },
            title = { Text("rename collection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AeroTextField(
                        name,
                        {
                            name = it
                            renameError = null
                        },
                        label = "collection name",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    renameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                PinkButton(
                    onClick = {
                        val entered = name.trim()
                        renameBusy = true
                        onRename(selected, entered) { failure ->
                            renameBusy = false
                            if (failure == null) {
                                renaming = false
                            } else renameError = failure
                        }
                    },
                    enabled = name.isNotBlank() && !renameBusy,
                ) {
                    Text(if (renameBusy) "saving…" else "save")
                }
            },
            dismissButton = {
                AeroButton(onClick = { renaming = false }, enabled = !renameBusy) { Text("cancel") }
            },
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
                        onSubmit = {
                            focus.clearFocus()
                            keyboard?.hide()
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(
                            games
                                .filter { searchMatchScore(it.title, query) != null }
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
                                AeroCheckbox(game.id in checked, null, Modifier.size(20.dp))
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
            dismissButton = { AeroButton(onClick = { picking = false }) { Text("cancel") } },
        )
}

@Composable
fun FindGames(
    games: List<Game>,
    artwork: Artwork,
    onOpen: (String) -> Unit,
    onCollections: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    val results =
        games
            .mapNotNull { game ->
                val score =
                    listOfNotNull(
                            searchMatchScore(game.title, query),
                            searchMatchScore(game.pokemon, query),
                        )
                        .minOrNull()
                score?.let { it to game }
            }
            .sortedWith(
                compareBy<Pair<Int, Game>> { it.first }.thenBy { it.second.title.lowercase() }
            )
            .map { it.second }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AeroTextField(
            query,
            { query = it },
            label = "search your games",
            onSubmit = {
                focus.clearFocus()
                keyboard?.hide()
            },
            autoFocus = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AeroButton(
            onClick = {
                focus.clearFocus()
                keyboard?.hide()
                onCollections()
            }
        ) {
            Text("manage collections")
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(results, key = { it.id }) { game ->
                Glass(
                    Modifier.fillMaxWidth()
                        .clickable {
                            focus.clearFocus()
                            keyboard?.hide()
                            onOpen(game.id)
                        }
                        .animateItem()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameThumbnail(game, artwork)
                        Column(Modifier.weight(1f)) {
                            Text(game.title, fontWeight = FontWeight.Medium)
                            Text(
                                when (game.place) {
                                    Place.ACTIVE -> game.shelf.label
                                    Place.FINISHED -> "finished games"
                                    else -> game.place.name.lowercase()
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
