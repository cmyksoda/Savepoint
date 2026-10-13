package com.cmyksoda.savepoint

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Savepoint(intent.getBooleanExtra("later", false)) }
    }
}

@Composable
fun Art(
    url: String,
    artwork: Artwork,
    title: String,
    modifier: Modifier = Modifier,
    sprite: Boolean = false,
    showPlaceholderTitle: Boolean = true,
    system: String = "",
    coverBounds: CoverSize? = null,
    transparent: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var bitmap by remember(url) { mutableStateOf(artwork.memoryBitmap(url)) }
    LaunchedEffect(url) {
        bitmap = runCatching { artwork.cachedBitmap(url) }.getOrNull()
        val preferred = preferredCoverUrl(url)
        bitmap =
            runCatching { artwork.bitmap(preferred) }.getOrNull()
                ?: bitmap
                ?: runCatching { artwork.bitmap(url) }.getOrNull()
    }
    val frame =
        coverBounds?.let {
            if (sprite) it
            else fitCover(coverAspectRatio(system, bitmap?.width, bitmap?.height), it)
        }
    val size = frame?.let { Modifier.size(it.width.dp, it.height.dp) } ?: Modifier
    Box(
        size
            .then(modifier)
            .then(
                if (transparent) Modifier
                else
                    Modifier.background(
                        Brush.linearGradient(listOf(Color(0xFFC677A4), Color(0xFF59496F)))
                    )
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap!!.asImageBitmap(),
                title,
                Modifier.fillMaxSize().then(if (sprite) Modifier.padding(12.dp) else Modifier),
                contentScale = ContentScale.Fit,
                filterQuality =
                    if (sprite) androidx.compose.ui.graphics.FilterQuality.None
                    else androidx.compose.ui.graphics.FilterQuality.Medium,
            )
        } else
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.icon_handheld), null, Modifier.size(48.dp))
                if (showPlaceholderTitle)
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
        overlay()
    }
}

@Composable
fun GameCover(
    game: Game,
    artwork: Artwork,
    focus: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    val hunt = game.shelf == Shelf.HUNTS
    if (hunt) {
        Column(
            modifier.width(142.dp).clickable(onClick = onOpen).semantics {
                contentDescription = game.title
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Art(
                game.sprite,
                artwork,
                displayPokemonName(game.pokemon),
                Modifier.size(112.dp),
                sprite = true,
                transparent = true,
                showPlaceholderTitle = false,
            )
            Text(
                displayPokemonName(game.pokemon).ifBlank { "shiny hunt" },
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                huntVersions[game.pokemonVersion] ?: game.title,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Art(
        if (hunt) game.sprite else game.cover,
        artwork,
        if (hunt) game.pokemon.ifBlank { game.title } else game.title,
        modifier
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = .65f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .semantics {
                contentDescription = "${game.title}${if (focus) ", playing now" else ""}"
            },
        sprite = hunt,
        system = game.system,
        coverBounds =
            when {
                hunt -> CoverSize(142f, 154f)
                focus -> CoverSize(216f, 244f)
                else -> CoverSize(144f, 176f)
            },
    ) {
        if (focus)
            Surface(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                color = Color(0xE0212A40),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "playing now",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        if (hunt)
            Surface(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color(0xE0212A40),
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        game.pokemon.ifBlank { "shiny hunt" },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        game.title,
                        color = Color(0xFFFFD9EB),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
    }
}

@Composable
fun Home(
    games: List<Game>,
    artwork: Artwork,
    onOpen: (String) -> Unit,
    onEditShelf: (Shelf) -> Unit,
    onAdd: () -> Unit,
    onSeed: () -> Unit,
) {
    val active = games.filter { it.place == Place.ACTIVE }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (active.isEmpty())
            item {
                Glass(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            "a place for your games",
                            fontSize = 22.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Bring a few games onto your shelves. The rest can stay tucked away.")
                        PinkButton(onClick = onAdd) { Text("add a game") }
                        if (games.isEmpty())
                            TextButton(onClick = onSeed) {
                                Text("start with the games we discussed")
                            }
                    }
                }
            }
        Shelf.entries.forEach { shelf ->
            val row = active.filter { it.shelf == shelf }.sortedBy { it.position }
            if (row.isNotEmpty())
                item(key = shelf.name) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                shelf.label,
                                fontSize = 19.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            AeroIconButton(
                                AeroSymbol.NEXT,
                                "edit ${shelf.label} shelf",
                                { onEditShelf(shelf) },
                                compact = true,
                            )
                        }
                        LazyRow(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(
                                        (if (shelf == Shelf.PLAY_THROUGH) 254
                                            else if (shelf == Shelf.HUNTS) 164 else 186)
                                            .dp
                                    )
                                    .glassShelf(LocalAeroDark.current),
                            contentPadding =
                                PaddingValues(start = 20.dp, end = 20.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            items(row, key = { it.id }) { game ->
                                GameCover(
                                    game,
                                    artwork,
                                    shelf == Shelf.PLAY_THROUGH && row.first().id == game.id,
                                    Modifier.animateItem(placementSpec = spring(stiffness = 350f))
                                        .animateContentSize(spring(stiffness = 350f)),
                                ) {
                                    onOpen(game.id)
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
fun LibraryList(
    games: List<Game>,
    place: Place,
    settings: Settings,
    artwork: Artwork,
    onSettings: (Settings) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCollections: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val collections = games.map { it.collection }.filter { it.isNotBlank() }.distinct().sorted()
    val filtered =
        sortedLibrary(
            games.filter {
                it.place == place &&
                    (query.isBlank() ||
                        it.title.contains(query, true) ||
                        it.pokemon.contains(query, true)) &&
                    (settings.systemFilter.isEmpty() || it.system == settings.systemFilter) &&
                    (settings.collectionFilter.isEmpty() ||
                        it.collection == settings.collectionFilter)
            },
            settings.sort,
        )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Choice("sort", settings.sort.label, LibrarySort.entries.map { it.label }) { value ->
            onSettings(settings.copy(sort = LibrarySort.entries.first { it.label == value }))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Choice(
                    "system",
                    settings.systemFilter.ifBlank { "all systems" },
                    listOf("all systems") + systems,
                ) {
                    onSettings(settings.copy(systemFilter = if (it == "all systems") "" else it))
                }
            }
            Column(Modifier.weight(1f)) {
                Choice(
                    "collection",
                    settings.collectionFilter.ifBlank { "all collections" },
                    listOf("all collections") + collections,
                ) {
                    onSettings(
                        settings.copy(collectionFilter = if (it == "all collections") "" else it)
                    )
                }
            }
        }
        TextButton(onClick = onCollections) { Text("manage collections") }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (filtered.isEmpty())
                item {
                    Text(
                        if (place == Place.PAUSED) "Games you pause will keep their place here."
                        else "No games here yet.",
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            items(filtered, key = { it.id }) { game ->
                Glass(
                    Modifier.fillMaxWidth()
                        .animateItem(placementSpec = spring(stiffness = 350f))
                        .clickable { onOpen(game.id) }
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Art(
                            if (game.shelf == Shelf.HUNTS) game.sprite else game.cover,
                            artwork,
                            game.title,
                            Modifier.clip(RoundedCornerShape(8.dp)),
                            game.shelf == Shelf.HUNTS,
                            system = game.system,
                            coverBounds = CoverSize(54f, 72f),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(game.title, fontWeight = FontWeight.SemiBold)
                            Text(game.system, style = MaterialTheme.typography.bodySmall)
                            if (game.collection.isNotBlank())
                                Text(game.collection, style = MaterialTheme.typography.bodySmall)
                            if (place == Place.PAUSED && game.pauseNote.isNotBlank())
                                Text(
                                    game.pauseNote,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                        }
                        if (place == Place.BACKLOG)
                            AeroIconButton(
                                AeroSymbol.DELETE,
                                "delete ${game.title}",
                                { onDelete(game.id) },
                                compact = true,
                            )
                        AeroIcon(
                            AeroSymbol.NEXT,
                            Modifier.size(16.dp),
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfEditor(
    shelf: Shelf,
    games: List<Game>,
    onMove: (String, Int) -> Unit,
    onOpen: (String) -> Unit,
    onBacklog: (String) -> Unit,
    onAdd: () -> Unit,
    onChooseBacklog: () -> Unit,
) {
    val row =
        games.filter { it.place == Place.ACTIVE && it.shelf == shelf }.sortedBy { it.position }
    LazyColumn(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                if (shelf == Shelf.PLAY_THROUGH)
                    "First is playing now. The rest are your near-future queue."
                else "Arrange this shelf however you like."
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PinkButton(onClick = onAdd) { Text("add a game") }
                TextButton(onClick = onChooseBacklog) { Text("from backlog") }
            }
        }
        items(row, key = { it.id }) { game ->
            Glass(Modifier.fillMaxWidth().animateItem(placementSpec = spring(stiffness = 350f))) {
                Column(Modifier.padding(14.dp)) {
                    TextButton(onClick = { onOpen(game.id) }) {
                        Text(game.title, fontWeight = FontWeight.SemiBold)
                    }
                    FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { onMove(game.id, -1) },
                            enabled = row.first().id != game.id,
                        ) {
                            Text("↑ earlier")
                        }
                        TextButton(
                            onClick = { onMove(game.id, 1) },
                            enabled = row.last().id != game.id,
                        ) {
                            Text("↓ later")
                        }
                        TextButton(onClick = { onBacklog(game.id) }) { Text("to backlog") }
                    }
                }
            }
        }
    }
}

@Composable
fun GameDetails(
    game: Game,
    artwork: Artwork,
    onNotes: () -> Unit,
    onPause: () -> Unit,
    onEdit: () -> Unit,
    onFinish: () -> Unit,
    onPlayLog: () -> Unit,
    onMove: (Place, Boolean) -> Unit,
    onEncounter: (Long) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    onEditCount: () -> Unit,
    onRelocate: () -> Unit,
    onDelete: () -> Unit,
    onCover: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameIdentity(game, artwork, showShelf = true, prominent = true)
        if (game.shelf != Shelf.HUNTS) TextButton(onClick = onCover) { Text("change cover art") }
        if (game.shelf != Shelf.HUNTS && game.pauseNote.isNotBlank())
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("for when you return", fontWeight = FontWeight.SemiBold)
                    Text(game.pauseNote)
                }
            }
        if (game.shelf == Shelf.HUNTS)
            AeroButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("edit hunt") }
        else {
            AeroActionRow(
                first = {
                    AeroButton(onClick = onNotes) {
                        AeroIcon(AeroSymbol.LOG, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("notes")
                    }
                },
                second = {
                    AeroButton(onClick = onEdit) {
                        AeroIcon(AeroSymbol.EDIT, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("edit game")
                    }
                },
            )
        }
        if (game.place == Place.ACTIVE || game.place == Place.BACKLOG) {
            AeroActionRow(
                first = { AeroButton(onClick = onRelocate) { Text("move game") } },
                second = { AeroButton(onClick = onPause) { Text("pause game") } },
            )
        } else
            AeroButton(onClick = onRelocate, modifier = Modifier.fillMaxWidth()) {
                Text("move game")
            }
        if (game.inPlayLog)
            AeroButton(onClick = onPlayLog, modifier = Modifier.fillMaxWidth()) {
                Text("view play log")
            }
        if (game.shelf == Shelf.HUNTS) {
            val odds = calculatedOdds(game)
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(onClick = onEditCount)
                            .semantics { contentDescription = "change encounter count" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${game.encounters} encounters",
                            fontSize = 24.sp,
                            modifier = Modifier.weight(1f),
                        )
                        AeroIcon(
                            AeroSymbol.EDIT,
                            Modifier.size(17.dp),
                            MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(game.method)
                    Text(odds.first?.let { "1 in $it · ${odds.second}" } ?: odds.second)
                    Text(huntVersions[game.pokemonVersion] ?: game.pokemonVersion)
                    if (game.shinyCharm) Text("shiny charm enabled")
                    PinkButton(
                        onClick = {
                            if (game.encounters < Long.MAX_VALUE) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onEncounter(1)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 64.dp,
                    ) {
                        Text("+1 encounter", fontSize = 20.sp)
                    }
                    TextButton(onClick = onUndo, enabled = canUndo) { Text("undo last count") }
                    HuntStatistics(game)
                }
            }
        }
        if (game.place == Place.ACTIVE) {
            if (game.shelf == Shelf.PLAY_THROUGH)
                PinkButton(
                    onClick = { onMove(Place.ACTIVE, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("play now")
                }
            AeroButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(if (game.shelf == Shelf.HUNTS) "complete hunt" else "mark finished")
            }
        } else {
            if (game.shelf == Shelf.PLAY_THROUGH)
                PinkButton(
                    onClick = { onMove(Place.ACTIVE, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (game.inPlayLog) "replay now" else "play now")
                }
            AeroButton(
                onClick = { onMove(Place.ACTIVE, false) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (game.shelf == Shelf.PLAY_THROUGH && game.inPlayLog) "queue a replay"
                    else if (game.shelf == Shelf.PLAY_THROUGH) "add to queue"
                    else "send to ${game.shelf.label}"
                )
            }
        }
        TextButton(onClick = onDelete) {
            Text("delete game", color = MaterialTheme.colorScheme.error)
        }
        if (game.source == "gametdb")
            Text("Title source: GameTDB", style = MaterialTheme.typography.bodySmall)
        if (game.source == "igdb")
            Text("Game data provided by IGDB", style = MaterialTheme.typography.bodySmall)
        if (game.cover.isNotBlank() && game.shelf != Shelf.HUNTS)
            Text("Cover via ${coverProvider(game)}", style = MaterialTheme.typography.bodySmall)
        if (game.sprite.startsWith("https://raw.githubusercontent.com/PokeAPI"))
            Text(
                "Pokémon artwork via PokéAPI · respective owners retain artwork rights",
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

@Composable
fun GameForm(
    initial: Game,
    artwork: Artwork,
    catalog: Catalog,
    existing: Boolean,
    onSave: (Game) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var system by rememberSaveable(initial.id) { mutableStateOf(initial.system) }
    var shelf by rememberSaveable(initial.id, initial.shelf) { mutableStateOf(initial.shelf) }
    var collection by rememberSaveable(initial.id) { mutableStateOf(initial.collection) }
    var collectionOrder by
        rememberSaveable(initial.id) { mutableStateOf(initial.collectionOrder.toString()) }
    var release by
        rememberSaveable(initial.id) {
            mutableStateOf(
                initial.release?.let {
                    java.time.Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString()
                } ?: ""
            )
        }
    var cover by rememberSaveable(initial.id) { mutableStateOf(initial.cover) }
    var selectedCoverProvider by
        rememberSaveable(initial.id) { mutableStateOf(initial.coverProvider) }
    var pokemon by rememberSaveable(initial.id) { mutableStateOf(initial.pokemon) }
    var version by
        rememberSaveable(initial.id, initial.pokemonVersion) {
            mutableStateOf(initial.pokemonVersion)
        }
    var sprite by rememberSaveable(initial.id) { mutableStateOf(initial.sprite) }
    var method by rememberSaveable(initial.id) { mutableStateOf(initial.method) }
    var count by rememberSaveable(initial.id) { mutableStateOf(initial.encounters.toString()) }
    var odds by
        rememberSaveable(initial.id) { mutableStateOf(initial.manualOdds?.toString() ?: "") }
    var charm by rememberSaveable(initial.id) { mutableStateOf(initial.shinyCharm) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectingSprite by remember { mutableStateOf(false) }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        val selected = artwork.select(uri)
                        if (selectingSprite) sprite = selected
                        else {
                            cover = selected
                            selectedCoverProvider = "Selected image"
                        }
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        AeroTextField(
            title,
            { title = it },
            label = if (shelf == Shelf.HUNTS) "game title" else "title",
            modifier = Modifier.fillMaxWidth(),
        )
        Choice("system", system, systems) { system = it }
        if (existing)
            Choice("shelf", shelf.label, Shelf.entries.map { it.label }) { label ->
                shelf = Shelf.entries.first { it.label == label }
            }
        AeroTextField(
            collection,
            { collection = it },
            label = "collection (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        if (collection.isNotBlank())
            AeroTextField(
                collectionOrder,
                { collectionOrder = it },
                label = "position in collection",
                modifier = Modifier.fillMaxWidth(),
            )
        AeroTextField(
            release,
            { release = it },
            label = "release date · YYYY-MM-DD (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                selectingSprite = false
                imagePicker.launch("image/*")
            }
        ) {
            Text("choose cover image")
        }
        if (cover.isNotBlank())
            Art(
                cover,
                artwork,
                title,
                Modifier.clip(RoundedCornerShape(12.dp)),
                system = system,
                coverBounds = CoverSize(90f, 120f),
            )
        if (shelf == Shelf.HUNTS) {
            AeroTextField(
                pokemon,
                {
                    pokemon = it
                    sprite = ""
                },
                label = "Pokémon name or form slug",
                modifier = Modifier.fillMaxWidth(),
            )
            Choice(
                "Pokémon game",
                huntVersions[version] ?: version,
                huntVersions.values.toList(),
            ) { value ->
                version = huntVersions.entries.first { it.value == value }.key
                sprite = ""
                odds = ""
            }
            Row {
                TextButton(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                sprite = catalog.shinySprite(pokemon, version)
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && pokemon.isNotBlank(),
                ) {
                    Text(if (busy) "finding artwork…" else "find shiny artwork")
                }
                TextButton(
                    onClick = {
                        selectingSprite = true
                        imagePicker.launch("image/*")
                    }
                ) {
                    Text("choose image")
                }
            }
            if (sprite.isNotBlank()) Art(sprite, artwork, pokemon, Modifier.size(100.dp), true)
            val methods = huntMethods(version)
            Choice(
                "hunt method",
                if (method in methods) method else "Other / custom",
                methods + "Other / custom",
            ) {
                method = if (it == "Other / custom") "" else it
                odds = ""
            }
            if (method !in methods)
                AeroTextField(
                    method,
                    {
                        method = it
                        odds = ""
                    },
                    label = "custom method",
                    modifier = Modifier.fillMaxWidth(),
                )
            AeroTextField(
                count,
                { count = it },
                label = "encounters",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    charm,
                    {
                        charm = it
                        odds = ""
                    },
                )
                Text("shiny charm")
            }
            AeroTextField(
                odds,
                { odds = it },
                label = "verified odds · 1 in… (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Standard base odds follow the game generation. Methods and modifiers without a verified calculation need your odds value. Changing method or charm clears the old value.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PinkButton(
            onClick = {
                try {
                    require(title.isNotBlank()) { "Give this game a title" }
                    require(shelf != Shelf.HUNTS || pokemon.isNotBlank()) {
                        "Enter the Pokémon you are hunting"
                    }
                    val encounters = count.toLongOrNull() ?: error("Enter a whole encounter count")
                    require(encounters >= 0) { "Encounters cannot be negative" }
                    val denominator =
                        if (odds.isBlank()) null
                        else odds.toIntOrNull() ?: error("Enter whole-number odds")
                    require(denominator == null || denominator > 0) { "Odds must be positive" }
                    val date =
                        if (release.isBlank()) null
                        else
                            LocalDate.parse(release)
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                    val order =
                        collectionOrder.toIntOrNull() ?: error("Enter a whole collection position")
                    require(order >= 0) { "Collection position cannot be negative" }
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            system = system,
                            shelf = shelf,
                            collection = collection.trim(),
                            collectionOrder = order,
                            release = date,
                            cover = cover,
                            coverProvider = selectedCoverProvider,
                            pokemon = pokemon.trim(),
                            pokemonVersion = version,
                            sprite = sprite,
                            method = method.trim(),
                            encounters = encounters,
                            shinyCharm = charm,
                            manualOdds = denominator,
                        )
                    )
                } catch (e: Exception) {
                    error = e.message ?: "Please check these values"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            Text("save game")
        }
        TextButton(onClick = onCancel) { Text("cancel") }
    }
}

@Composable
fun AddScreen(catalog: Catalog, endpoint: String, onChoose: (Game) -> Unit, onManual: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Game>>(emptyList()) }
    var warning by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AeroTextField(
            query,
            { query = it },
            label = "game title",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Choice("system", system.ifBlank { "all systems" }, listOf("all systems") + systems) {
            system = if (it == "all systems") "" else it
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PinkButton(
                onClick = {
                    busy = true
                    warning = null
                    scope.launch {
                        try {
                            val answer = catalog.search(query.trim(), system, endpoint)
                            results = answer.first
                            warning = answer.second
                        } catch (e: Exception) {
                            warning = "Search unavailable. You can add a game manually."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && query.isNotBlank(),
            ) {
                Text(if (busy) "searching…" else "search")
            }
            TextButton(onClick = onManual) { Text("add manually") }
        }
        warning?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(results) { game ->
                Glass(Modifier.fillMaxWidth().clickable { onChoose(game) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(game.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${game.system} · ${if (game.source == "gametdb") "GameTDB" else "IGDB"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onEnable: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    var endpoint by remember(settings.catalogUrl) { mutableStateOf(settings.catalogUrl) }
    var endpointError by remember { mutableStateOf<String?>(null) }
    fun pickTime(start: Boolean) {
        val minute = if (start) settings.startMinute else settings.endMinute % 1440
        TimePickerDialog(
                context,
                { _, hour, minutes ->
                    val selected = hour * 60 + minutes
                    val s =
                        if (start) settings.copy(startMinute = selected)
                        else settings.copy(endMinute = if (selected == 0) 1440 else selected)
                    if (s.endMinute > s.startMinute) onChange(s)
                    else endpointError = "Choose an end time after the start time."
                },
                minute / 60,
                minute % 60,
                false,
            )
            .show()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Choice("appearance", settings.theme, listOf("system", "light", "dark")) {
            onChange(settings.copy(theme = it))
        }
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "time for yourself",
                    fontSize = 19.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "A general invitation to enjoy your games. Ignoring it never creates another reminder."
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("rest invitations", Modifier.weight(1f))
                    Switch(
                        settings.reminders,
                        { enabled ->
                            if (enabled) onEnable()
                            else onChange(settings.copy(reminders = false, laterAt = 0))
                        },
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").forEachIndexed {
                        index,
                        day ->
                        val number = index + 1
                        val checked = number in settings.days
                        Column(
                            Modifier.weight(1f)
                                .heightIn(min = 64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .toggleable(checked, role = Role.Checkbox) {
                                    onChange(
                                        settings.copy(
                                            days =
                                                if (checked) settings.days - number
                                                else settings.days + number
                                        )
                                    )
                                }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Checkbox(checked, null, Modifier.size(20.dp))
                            Text(day, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row {
                    TextButton(onClick = { pickTime(true) }) {
                        Text("from ${timeLabel(settings.startMinute)}")
                    }
                    TextButton(onClick = { pickTime(false) }) {
                        Text("until ${timeLabel(settings.endMinute)}")
                    }
                }
                Text(
                    "Delivery can vary with Android battery settings. Invitations arriving after your window are discarded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = {
                        onChange(settings.copy(skipDate = LocalDate.now().toString(), laterAt = 0))
                    }
                ) {
                    Text("skip today")
                }
            }
        }
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "catalog search",
                    fontSize = 19.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "GameTDB works without an account. Your search service adds broader Nintendo and PC results."
                )
                AeroTextField(
                    endpoint,
                    { endpoint = it },
                    label = "catalog service HTTPS address",
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        try {
                            if (endpoint.isNotBlank())
                                require(
                                    URI(endpoint).scheme == "https" &&
                                        URI(endpoint).host != null &&
                                        URI(endpoint).userInfo == null &&
                                        URI(endpoint).query == null &&
                                        URI(endpoint).fragment == null
                                ) {
                                    "Use an HTTPS base address"
                                }
                            onChange(settings.copy(catalogUrl = endpoint.trim().trimEnd('/')))
                            endpointError = null
                        } catch (e: Exception) {
                            endpointError = e.message
                        }
                    }
                ) {
                    Text("save address")
                }
                Text(
                    "Only catalog searches leave your phone. Your shelves and notes stay local.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        endpointError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AeroButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("export backup") }
        AeroButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("import backup") }
        Text(
            "Savepoint · ${LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0).versionName}\nOriginal code © cmyksoda · GPLv3\nGame artwork belongs to its respective owners.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

fun timeLabel(minute: Int): String =
    java.time.LocalTime.of((minute / 60) % 24, minute % 60)
        .format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
fun Savepoint(openLater: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { Library(context) }
    val settingsStore = remember { SettingsStore(context) }
    val artwork = remember { Artwork(context) }
    val catalog = remember { Catalog(context) }
    val games by library.games.collectAsState()
    var settings by remember { mutableStateOf(Settings()) }
    var loaded by remember { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf("home") }
    var selected by rememberSaveable { mutableStateOf("") }
    var editingShelf by rememberSaveable { mutableStateOf(Shelf.PLAY_THROUGH) }
    var addShelf by rememberSaveable { mutableStateOf<Shelf?>(Shelf.PLAY_THROUGH) }
    var addReturn by rememberSaveable { mutableStateOf("home") }
    var destinationChosen by rememberSaveable { mutableStateOf(false) }
    var form by
        rememberSaveable(
            stateSaver =
                Saver<Game?, String>(
                    save = { it?.let { game -> JsonCodec.game(game).toString() } },
                    restore = { JsonCodec.game(JSONObject(it)) },
                )
        ) {
            mutableStateOf<Game?>(null)
        }
    var dialog by rememberSaveable { mutableStateOf("") }
    var logEntry by
        rememberSaveable(
            stateSaver =
                Saver<Playthrough?, String>(
                    save = { it?.let { entry -> JsonCodec.playthrough(entry).toString() } },
                    restore = { JsonCodec.playthrough(JSONObject(it)) },
                )
        ) {
            mutableStateOf<Playthrough?>(null)
        }
    var note by rememberSaveable { mutableStateOf("") }
    var encounterValue by rememberSaveable { mutableStateOf("1") }
    var encounterMode by rememberSaveable { mutableStateOf("add encounters") }
    var encounterError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf<RestoredLibrary?>(null) }
    var undoCount by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val selectedGame = games.find { it.id == selected }

    fun mutate(action: (List<Game>) -> List<Game>) {
        scope.launch {
            try {
                library.change { current -> fillMissingStarterCovers(action(current)) }
            } catch (e: Exception) {
                error = e.message ?: "Could not save this change"
            }
        }
    }
    fun changeEncounters(id: String, transform: (Long) -> Long) {
        scope.launch {
            try {
                var previous = 0L
                library.change { list ->
                    list.map { game ->
                        if (game.id == id) {
                            previous = game.encounters
                            game.copy(encounters = transform(game.encounters))
                        } else game
                    }
                }
                undoCount = id to previous
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    fun saveGame(game: Game) {
        val newFinishedGame = games.none { it.id == game.id } && game.place == Place.FINISHED
        mutate { list ->
            val previous = list.firstOrNull { it.id == game.id }
            val positioned =
                game.copy(
                    added = previous?.added ?: System.currentTimeMillis(),
                    position =
                        if (
                            previous == null ||
                                previous.shelf != game.shelf ||
                                previous.place != game.place
                        )
                            (list
                                .filter {
                                    it.shelf == game.shelf &&
                                        it.place == game.place &&
                                        it.id != game.id
                                }
                                .maxOfOrNull { it.position } ?: -1) + 1
                        else previous.position,
                )
            if (previous == null) list + positioned
            else list.map { if (it.id == game.id) positioned else it }
        }
        selected = game.id
        route = if (game.place == Place.FINISHED) "journal" else "details"
        if (newFinishedGame) {
            logEntry = Playthrough(finishedAt = null)
            dialog = "entry"
        }
    }
    fun startAdding(shelf: Shelf?, from: String) {
        addShelf = shelf
        addReturn = from
        destinationChosen = false
        selected = ""
        form = null
        route = "add"
    }
    fun backRoute(): String =
        when {
            route == "form" -> if (selectedGame != null) "details" else "add"
            route == "add" -> addReturn
            route == "journal" -> "log"
            else -> "home"
        }
    fun updateSettings(value: Settings) {
        val previous = settings
        scope.launch {
            try {
                settings =
                    settingsStore.change { current -> mergeSettings(current, previous, value) }
                Reminders.schedule(context, settings)
                if (!settings.reminders || settings.skipDate == LocalDate.now().toString())
                    Reminders.clear(context)
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) updateSettings(settings.copy(reminders = true))
            else
                error =
                    "Invitations are off. You can enable notifications in Android settings whenever you want."
        }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        Backup.export(context, uri, games, settings, artwork)
                        error = "backup exported"
                    } catch (e: Exception) {
                        error = "Export failed: ${e.message}"
                    }
                }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        restored = Backup.inspect(context, uri)
                        dialog = "restore"
                    } catch (e: Exception) {
                        error = "Import failed: ${e.message}"
                    }
                }
        }
    LaunchedEffect(Unit) {
        try {
            artwork.installBundledCovers()
            library.load()
            if (updateStarterArtwork(library.games.value) != library.games.value)
                library.change(::updateStarterArtwork)
            settings = settingsStore.read()
            loaded = true
            if (route == "finished") route = "log"
            Reminders.schedule(context, settings)
            if (openLater) dialog = "later"
        } catch (e: Exception) {
            error = "Could not open library: ${e.message}"
        }
    }
    LaunchedEffect(settingsStore) {
        try {
            settingsStore.changes.collect { settings = it }
        } catch (e: Exception) {
            error = "Could not read settings: ${e.message}"
        }
    }
    val dark =
        when (settings.theme) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    LaunchedEffect(dark) {
        val style =
            if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = style,
            navigationBarStyle = style,
        )
        (context as? ComponentActivity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightNavigationBars = !dark
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        }
    }
    val backdrop = remember(dark) { Modifier.aeroBackdrop(dark) }
    val colors = aeroColors(dark)
    MaterialTheme(colorScheme = colors, typography = AeroTypography) {
        CompositionLocalProvider(
            LocalContentColor provides colors.onBackground,
            LocalAeroDark provides dark,
        ) {
            BackHandler(route != "home" || dialog.isNotBlank()) {
                if (dialog.isNotBlank()) dialog = "" else route = backRoute()
            }
            Column(Modifier.fillMaxSize().then(backdrop).safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (route != "home")
                        AeroIconButton(AeroSymbol.BACK, "back", onClick = { route = backRoute() })
                    else
                        Image(painterResource(R.drawable.icon_handheld), null, Modifier.size(48.dp))
                    Text(
                        when (route) {
                            "home" -> "Savepoint"
                            "find" -> "search"
                            "shelf" -> editingShelf.label
                            "details" -> "your game"
                            "form" -> if (selectedGame == null) "add game" else "edit game"
                            "log",
                            "journal" -> "play log"
                            else -> route
                        },
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (route == "home")
                        AeroIconButton(
                            AeroSymbol.SETTINGS,
                            "settings",
                            { route = "settings" },
                            compact = true,
                        )
                    if (route in listOf("home", "backlog", "paused", "log"))
                        AeroIconButton(AeroSymbol.SEARCH, "search your games", { route = "find" })
                }
                Box(Modifier.weight(1f)) {
                    if (!loaded) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else
                        AnimatedContent(
                            targetState = route,
                            transitionSpec = {
                                (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 32 })
                                    .togetherWith(fadeOut(tween(100)))
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "screen transition",
                        ) { screen ->
                            when (screen) {
                                "home" ->
                                    Home(
                                        games,
                                        artwork,
                                        {
                                            selected = it
                                            route = "details"
                                        },
                                        {
                                            editingShelf = it
                                            route = "shelf"
                                        },
                                        { startAdding(Shelf.PLAY_THROUGH, "home") },
                                        { mutate { old -> old + starterGames() } },
                                    )
                                "backlog",
                                "paused" ->
                                    LibraryList(
                                        games,
                                        when (screen) {
                                            "paused" -> Place.PAUSED
                                            else -> Place.BACKLOG
                                        },
                                        settings,
                                        artwork,
                                        ::updateSettings,
                                        {
                                            selected = it
                                            route = "details"
                                        },
                                        { id ->
                                            selected = id
                                            dialog = "delete"
                                        },
                                        { route = "collections" },
                                    )
                                "find" ->
                                    FindGames(
                                        games,
                                        artwork,
                                        {
                                            selected = it
                                            route = "details"
                                        },
                                        { route = "collections" },
                                    )
                                "collections" ->
                                    CollectionsScreen(
                                        games,
                                        settings,
                                        artwork,
                                        { name ->
                                            updateSettings(
                                                settings.copy(
                                                    collectionNames =
                                                        settings.collectionNames + name
                                                )
                                            )
                                        },
                                        { name, ids ->
                                            updateSettings(
                                                settings.copy(
                                                    collectionNames =
                                                        settings.collectionNames + name
                                                )
                                            )
                                            mutate { setCollectionMembers(it, name, ids) }
                                        },
                                        { name, id, direction ->
                                            mutate { reorderCollection(it, name, id, direction) }
                                        },
                                        {
                                            selected = it
                                            route = "details"
                                        },
                                    )
                                "log" ->
                                    PlayLogList(
                                        games,
                                        artwork,
                                        {
                                            selected = it
                                            route = "journal"
                                        },
                                    )
                                "journal" ->
                                    selectedGame?.let { game ->
                                        GameJournal(
                                            game,
                                            artwork,
                                            { entry ->
                                                logEntry = entry
                                                dialog = "entry"
                                            },
                                            { first ->
                                                mutate {
                                                    LibraryRules.move(
                                                        it,
                                                        game.id,
                                                        Place.ACTIVE,
                                                        first,
                                                    )
                                                }
                                                route = "home"
                                            },
                                            { route = "details" },
                                        )
                                    }
                                "shelf" ->
                                    ShelfEditor(
                                        editingShelf,
                                        games,
                                        { id, direction ->
                                            mutate { LibraryRules.reorder(it, id, direction) }
                                        },
                                        {
                                            selected = it
                                            route = "details"
                                        },
                                        { id ->
                                            mutate { LibraryRules.move(it, id, Place.BACKLOG) }
                                        },
                                        { startAdding(editingShelf, "shelf") },
                                        { route = "backlog" },
                                    )
                                "details" ->
                                    selectedGame?.let { game ->
                                        GameDetails(
                                            game,
                                            artwork,
                                            {
                                                note = game.notes
                                                dialog = "notes"
                                            },
                                            {
                                                if (game.shelf == Shelf.HUNTS) {
                                                    mutate {
                                                        LibraryRules.move(it, game.id, Place.PAUSED)
                                                    }
                                                    route = "home"
                                                } else {
                                                    note = game.pauseNote
                                                    dialog = "pause"
                                                }
                                            },
                                            {
                                                form = game
                                                route = "form"
                                            },
                                            {
                                                if (game.shelf == Shelf.HUNTS) {
                                                    mutate {
                                                        LibraryRules.move(
                                                            it,
                                                            game.id,
                                                            Place.FINISHED,
                                                        )
                                                    }
                                                    route = "home"
                                                } else {
                                                    logEntry = Playthrough()
                                                    dialog = "finish"
                                                }
                                            },
                                            { route = "journal" },
                                            { place, first ->
                                                mutate {
                                                    LibraryRules.move(it, game.id, place, first)
                                                }
                                                route = "home"
                                            },
                                            { delta ->
                                                changeEncounters(game.id) {
                                                    increasedEncounterCount(it, delta)
                                                }
                                            },
                                            {
                                                undoCount?.let { (id, count) ->
                                                    mutate { list ->
                                                        list.map {
                                                            if (it.id == id)
                                                                it.copy(encounters = count)
                                                            else it
                                                        }
                                                    }
                                                    undoCount = null
                                                }
                                            },
                                            undoCount?.first == game.id,
                                            {
                                                encounterMode = "add encounters"
                                                encounterValue = "1"
                                                encounterError = null
                                                dialog = "encounters"
                                            },
                                            {
                                                form = game
                                                dialog = "destination"
                                            },
                                            { dialog = "delete" },
                                            { dialog = "cover" },
                                        )
                                    }
                                "add" ->
                                    AddScreen(
                                        catalog,
                                        settings.catalogUrl,
                                        { game ->
                                            form = game.copy(shelf = addShelf ?: Shelf.PLAY_THROUGH)
                                            selected = ""
                                            dialog = "destination"
                                        },
                                        {
                                            form =
                                                Game(
                                                    title = "",
                                                    shelf = addShelf ?: Shelf.PLAY_THROUGH,
                                                )
                                            selected = ""
                                            route = "form"
                                        },
                                    )
                                "form" ->
                                    form?.let { initial ->
                                        GameForm(
                                            initial,
                                            artwork,
                                            catalog,
                                            games.any { it.id == initial.id },
                                            { game ->
                                                if (
                                                    games.any { it.id == game.id } ||
                                                        destinationChosen
                                                )
                                                    saveGame(game)
                                                else {
                                                    form = game
                                                    dialog = "destination"
                                                }
                                            },
                                            {
                                                route =
                                                    if (selectedGame != null) "details" else "add"
                                            },
                                        )
                                    }
                                "settings" ->
                                    SettingsScreen(
                                        settings,
                                        ::updateSettings,
                                        {
                                            if (Build.VERSION.SDK_INT >= 33)
                                                notificationPermission.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                )
                                            else updateSettings(settings.copy(reminders = true))
                                        },
                                        { export.launch("savepoint-${LocalDate.now()}.zip") },
                                        {
                                            import.launch(
                                                arrayOf(
                                                    "application/zip",
                                                    "application/octet-stream",
                                                )
                                            )
                                        },
                                    )
                            }
                        }
                }
                if (
                    route in
                        listOf("home", "backlog", "paused", "log", "add", "find", "collections") ||
                        (route == "form" && selectedGame == null)
                )
                    AeroDock(
                        route,
                        { route = it },
                        {
                            if (route != "add" && route != "form")
                                startAdding(
                                    if (route == "backlog") null else Shelf.PLAY_THROUGH,
                                    route,
                                )
                        },
                    )
            }
            if (dialog == "cover" && selectedGame != null)
                CoverPicker(
                    selectedGame,
                    artwork,
                    catalog,
                    settings.catalogUrl,
                    { cover ->
                        val id = selectedGame.id
                        mutate { list ->
                            list.map {
                                if (it.id == id)
                                    it.copy(cover = cover.url, coverProvider = cover.provider)
                                else it
                            }
                        }
                        dialog = ""
                    },
                    { dialog = "" },
                )
            if (dialog == "encounters" && selectedGame != null) {
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("encounters") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Choice("change", encounterMode, listOf("add encounters", "set total")) {
                                encounterMode = it
                                encounterValue =
                                    if (it == "set total") selectedGame.encounters.toString()
                                    else "1"
                                encounterError = null
                            }
                            AeroTextField(
                                encounterValue,
                                {
                                    encounterValue = it
                                    encounterError = null
                                },
                                label =
                                    if (encounterMode == "set total") "total encounters"
                                    else "how many to add?",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            encounterError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        PinkButton(
                            onClick = {
                                val amount = encounterValue.toLongOrNull()
                                if (
                                    amount == null ||
                                        amount < 0 ||
                                        (encounterMode == "add encounters" && amount == 0L)
                                )
                                    encounterError =
                                        "Enter a ${if (encounterMode == "set total") "nonnegative" else "positive"} whole number"
                                else {
                                    val add = encounterMode == "add encounters"
                                    changeEncounters(selectedGame.id) {
                                        if (add) increasedEncounterCount(it, amount) else amount
                                    }
                                    dialog = ""
                                }
                            }
                        ) {
                            Text(
                                if (encounterMode == "set total") "save total" else "add encounters"
                            )
                        }
                    },
                    dismissButton = { TextButton(onClick = { dialog = "" }) { Text("cancel") } },
                )
            }
            if (dialog == "delete" && selectedGame != null) {
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("delete ${selectedGame.title}?") },
                    text = {
                        Text(
                            "Remove this game and its saved notes and playthroughs from Savepoint?"
                        )
                    },
                    confirmButton = {
                        AeroButton(
                            onClick = {
                                val id = selectedGame.id
                                mutate { list -> list.filterNot { it.id == id } }
                                dialog = ""
                                selected = ""
                                if (route !in listOf("backlog", "paused", "home", "log"))
                                    route = "backlog"
                            }
                        ) {
                            Text("delete game", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { dialog = "" }) { Text("cancel") } },
                )
            }
            if (dialog == "destination" && form != null) {
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("where should it go?") },
                    text = {
                        Column {
                            Text(
                                form!!.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            Shelf.entries.forEach { destination ->
                                TextButton(
                                    onClick = {
                                        val game =
                                            form!!.copy(shelf = destination, place = Place.ACTIVE)
                                        dialog = ""
                                        if (destination == Shelf.HUNTS && game.pokemon.isBlank()) {
                                            form =
                                                game.copy(
                                                    pokemonVersion = inferHuntVersion(game.title) ?: "other"
                                                )
                                            destinationChosen = true
                                            route = "form"
                                        } else saveGame(game)
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    AeroIcon(AeroSymbol.SHELVES, Modifier.size(20.dp))
                                    Text(
                                        destination.label,
                                        Modifier.weight(1f).padding(start = 12.dp),
                                    )
                                    AeroIcon(AeroSymbol.NEXT, Modifier.size(14.dp))
                                }
                            }
                            HorizontalDivider(
                                Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = .3f),
                            )
                            listOf(Place.BACKLOG to "backlog", Place.FINISHED to "play log")
                                .forEach { (place, label) ->
                                    TextButton(
                                        onClick = {
                                            val game =
                                                form!!.copy(
                                                    place = place,
                                                    hasPlayed =
                                                        form!!.hasPlayed || place == Place.FINISHED,
                                                )
                                            if (
                                                place == Place.FINISHED &&
                                                    games.any {
                                                        it.id == game.id &&
                                                            it.place != Place.FINISHED
                                                    }
                                            ) {
                                                selected = game.id
                                                logEntry = Playthrough()
                                                dialog = "finish"
                                            } else {
                                                dialog = ""
                                                saveGame(game)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    ) {
                                        AeroIcon(
                                            if (place == Place.BACKLOG) AeroSymbol.BACKLOG
                                            else AeroSymbol.LOG,
                                            Modifier.size(20.dp),
                                        )
                                        Text(label, Modifier.weight(1f).padding(start = 12.dp))
                                        AeroIcon(AeroSymbol.NEXT, Modifier.size(14.dp))
                                    }
                                }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = "" }) { Text("cancel") } },
                )
            }
            if (
                (dialog == "finish" || dialog == "entry") &&
                    selectedGame != null &&
                    logEntry != null
            ) {
                PlaythroughEditor(
                    selectedGame,
                    logEntry!!,
                    dialog == "finish",
                    { entry, replays ->
                        val id = selectedGame.id
                        if (dialog == "finish")
                            mutate { LibraryRules.finish(it, id, entry, replays) }
                        else mutate { LibraryRules.editPlaythrough(it, id, entry, replays) }
                        dialog = ""
                        logEntry = null
                        route = "journal"
                    },
                    {
                        dialog = ""
                        logEntry = null
                    },
                )
            }
            if (dialog == "notes" || dialog == "pause")
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = {
                        Text(if (dialog == "notes") "notes" else "pause ${selectedGame?.title}?")
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (dialog == "pause")
                                Text(
                                    "Leave yourself something for when you return. This stays separate from your general notes."
                                )
                            AeroTextField(
                                note,
                                { note = it },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth(),
                                label =
                                    if (dialog == "notes") "general notes"
                                    else "pause note (optional)",
                            )
                        }
                    },
                    confirmButton = {
                        PinkButton(
                            onClick = {
                                val id = selected
                                if (dialog == "pause") {
                                    mutate { LibraryRules.pause(it, id, note) }
                                    route = "home"
                                } else
                                    mutate { list ->
                                        list.map { if (it.id == id) it.copy(notes = note) else it }
                                    }
                                dialog = ""
                            }
                        ) {
                            Text(if (dialog == "notes") "save" else "save & pause")
                        }
                    },
                    dismissButton = { QuietAction(onClick = { dialog = "" }) { Text("cancel") } },
                )
            if (dialog == "later")
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("another time today?") },
                    text = {
                        Text(
                            "Choose when you'd like one follow-up invitation. There won't be another unless you ask."
                        )
                    },
                    confirmButton = {
                        PinkButton(
                            onClick = {
                                val current = java.time.LocalTime.now().plusMinutes(30)
                                TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            val at =
                                                LocalDate.now()
                                                    .atTime(hour, minute)
                                                    .atZone(ZoneId.systemDefault())
                                                    .toInstant()
                                                    .toEpochMilli()
                                            if (
                                                at > System.currentTimeMillis() &&
                                                    settings.followupDate !=
                                                        LocalDate.now().toString()
                                            ) {
                                                updateSettings(
                                                    settings.copy(laterAt = at, skipDate = "")
                                                )
                                                Reminders.clear(context)
                                                dialog = ""
                                            } else
                                                error =
                                                    "Choose a future time today. Only one follow-up is available each day."
                                        },
                                        current.hour,
                                        current.minute,
                                        false,
                                    )
                                    .show()
                            }
                        ) {
                            Text("choose time")
                        }
                    },
                    dismissButton = { QuietAction(onClick = { dialog = "" }) { Text("cancel") } },
                )
            if (dialog == "restore" && restored != null)
                AeroDialog(
                    onDismissRequest = {
                        restored?.artwork?.deleteRecursively()
                        restored = null
                        dialog = ""
                    },
                    title = { Text("replace this library?") },
                    text = {
                        Text(
                            "This backup contains ${restored!!.games.size} entries. Import replaces your current library. Rest invitations will be off."
                        )
                    },
                    confirmButton = {
                        PinkButton(
                            onClick = {
                                val backup = restored!!
                                scope.launch {
                                    try {
                                        backup.artwork.resolve("artwork").listFiles()?.forEach {
                                            val destination = File(artwork.directory, it.name)
                                            if (!destination.exists()) it.copyTo(destination)
                                        }
                                        val previous = games
                                        val previousSettings = settings
                                        try {
                                            library.change { updateStarterArtwork(backup.games) }
                                            settings = settingsStore.change { backup.settings }
                                        } catch (e: Exception) {
                                            library.change { previous }
                                            settingsStore.change { previousSettings }
                                            throw e
                                        }
                                        Reminders.schedule(context, settings)
                                        Reminders.clear(context)
                                        route = "home"
                                        dialog = ""
                                        error = "backup restored"
                                    } catch (e: Exception) {
                                        error = "Restore failed: ${e.message}"
                                    } finally {
                                        backup.artwork.deleteRecursively()
                                        restored = null
                                    }
                                }
                            }
                        ) {
                            Text("replace & import")
                        }
                    },
                    dismissButton = {
                        QuietAction(
                            onClick = {
                                restored?.artwork?.deleteRecursively()
                                restored = null
                                dialog = ""
                            }
                        ) {
                            Text("cancel")
                        }
                    },
                )
            error?.let { message ->
                AeroDialog(
                    onDismissRequest = { error = null },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = { error = null }) { Text("okay") } },
                )
            }
        }
    }
}
