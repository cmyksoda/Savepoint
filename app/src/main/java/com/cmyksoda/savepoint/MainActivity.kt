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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val dockRoutes =
    setOf(
        "home",
        "backlog",
        "paused",
        "log",
        "add",
        "catalog",
        "find",
        "collections",
        "collection",
        "hunt-add",
        "daily",
        "finished",
    )

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Savepoint(
                intent.getBooleanExtra("later", false),
                intent.getBooleanExtra("log-play", false),
            )
        }
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
    reserveHeight: Boolean = true,
    imageScale: ContentScale = ContentScale.Fit,
    cornerRadius: Dp = 8.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var bitmap by remember(url) { mutableStateOf(artwork.memoryBitmap(url, sprite)) }
    LaunchedEffect(url, sprite) {
        if (sprite) {
            bitmap = runCatching { artwork.spriteBitmap(url) }.getOrNull()
            return@LaunchedEffect
        }
        bitmap = runCatching { artwork.cachedCoverBitmap(url) }.getOrNull() ?: bitmap
        if (bitmap != null) return@LaunchedEffect
        val preferred = preferredCoverUrl(url)
        bitmap =
            runCatching { artwork.bitmap(preferred) }.getOrNull()
                ?: bitmap
                ?: runCatching { artwork.bitmap(url) }.getOrNull()
    }
    val frame =
        coverBounds?.let {
            if (sprite) it
            else
                fitCover(
                    bitmap?.let { cover -> cover.width.toFloat() / cover.height }
                        ?: artwork.cachedAspectRatio(url)
                        ?: coverAspectRatio(catalogSystemName(system), null, null),
                    it,
                )
        }
    val slotWidth =
        remember(url, system, coverBounds) {
            coverBounds?.let { bounds ->
                artwork.cachedAspectRatio(url)?.let { fitCover(it, bounds).width } ?: bounds.width
            }
        }
    val size = frame?.let { Modifier.size(it.width.dp, it.height.dp) } ?: Modifier
    val body: @Composable () -> Unit = {
        Box(
            size
                .then(modifier)
                .then(
                    if (sprite || cornerRadius == 0.dp) Modifier
                    else Modifier.clip(RoundedCornerShape(cornerRadius))
                )
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
                    Modifier.fillMaxSize(),
                    contentScale = imageScale,
                    filterQuality =
                        if (
                            sprite &&
                                !Regex("/Shiny/(XY|SM|SWSH|LGPE|BDSP|LA|SV|ZA)/")
                                    .containsMatchIn(url) &&
                                maxOf(bitmap!!.width, bitmap!!.height) <= 96
                        )
                            androidx.compose.ui.graphics.FilterQuality.None
                        else androidx.compose.ui.graphics.FilterQuality.Medium,
                )
            } else
                Column(
                    Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
    if (coverBounds != null && !sprite) {
        Box(
            Modifier.size(
                (slotWidth ?: frame?.width ?: coverBounds.width).dp,
                (if (reserveHeight) coverBounds.height else frame?.height ?: coverBounds.height).dp,
            ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            body()
        }
    } else body()
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
            modifier.width(112.dp).clickable(onClick = onOpen).semantics {
                contentDescription = game.title
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Art(
                game.sprite,
                artwork,
                displayPokemonName(game.pokemon),
                Modifier.size(84.dp),
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
    Box(modifier) {
        Art(
            if (hunt) game.sprite else game.cover,
            artwork,
            if (hunt) game.pokemon.ifBlank { game.title } else game.title,
            Modifier.softGlassShadow(10.dp, 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = .65f), RoundedCornerShape(10.dp))
                .clickable(onClick = onOpen)
                .semantics {
                    contentDescription = "${game.title}${if (focus) ", playing now" else ""}"
                },
            sprite = hunt,
            cornerRadius = 10.dp,
            reserveHeight = false,
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
    val active = remember(games) { games.filter { it.place == Place.ACTIVE } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
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
                            AeroButton(onClick = onSeed) {
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
                        val height =
                            (if (shelf == Shelf.PLAY_THROUGH) 264
                                else if (shelf == Shelf.HUNTS) 148 else 196)
                                .dp
                        if (shelf == Shelf.HUNTS) {
                            ShelfGames(
                                row,
                                shelf,
                                artwork,
                                onOpen,
                                Modifier.fillMaxWidth()
                                    .height(height)
                                    .glassShelf(LocalAeroDark.current),
                                PaddingValues(start = 20.dp, end = 20.dp, bottom = 18.dp),
                            )
                        } else {
                            Column(Modifier.fillMaxWidth().height(height)) {
                                ShelfGames(
                                    row,
                                    shelf,
                                    artwork,
                                    onOpen,
                                    Modifier.fillMaxWidth().weight(1f),
                                    PaddingValues(horizontal = 20.dp),
                                )
                                Spacer(
                                    Modifier.fillMaxWidth()
                                        .height(ShelfSurfaceInset)
                                        .glassShelf(LocalAeroDark.current)
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun ShelfGames(
    games: List<Game>,
    shelf: Shelf,
    artwork: Artwork,
    onOpen: (String) -> Unit,
    modifier: Modifier,
    padding: PaddingValues,
) {
    LazyRow(
        modifier,
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(games, key = { it.id }) { game ->
            GameCover(
                game,
                artwork,
                shelf == Shelf.PLAY_THROUGH && games.first().id == game.id,
                Modifier.animateItem(placementSpec = spring(stiffness = 350f)),
            ) {
                onOpen(game.id)
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
    onCollections: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val collections = remember(games, settings.collectionNames) { collectionNames(games, settings) }
    val filtered =
        remember(
            games,
            place,
            query,
            settings.sort,
            settings.systemFilter,
            settings.collectionFilter,
        ) {
            sortedLibrary(
                games.filter {
                    it.place == place &&
                        (query.isBlank() ||
                            searchMatchScore(it.title, query) != null ||
                            searchMatchScore(it.pokemon, query) != null) &&
                        (settings.systemFilter.isEmpty() || it.system == settings.systemFilter) &&
                        (settings.collectionFilter.isEmpty() ||
                            it.collection == settings.collectionFilter)
                },
                settings.sort,
            )
        }
    LibraryBrowser(
        filtered,
        (if (place == Place.BACKLOG) "backlog" else "paused") in settings.gridViews,
        if (place == Place.PAUSED) "Games you pause will keep their place here."
        else "No games here yet.",
        controls = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Choice("sort", settings.sort.label, LibrarySort.entries.map { it.label }) { value ->
                    onSettings(
                        settings.copy(sort = LibrarySort.entries.first { it.label == value })
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Choice(
                            "system",
                            settings.systemFilter.ifBlank { "all systems" },
                            listOf("all systems") + systems,
                        ) {
                            onSettings(
                                settings.copy(systemFilter = if (it == "all systems") "" else it)
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Choice(
                            "collection",
                            settings.collectionFilter.ifBlank { "all collections" },
                            listOf("all collections") + collections,
                            menuAction = "manage collections" to onCollections,
                        ) {
                            onSettings(
                                settings.copy(
                                    collectionFilter = if (it == "all collections") "" else it
                                )
                            )
                        }
                    }
                }
            }
        },
        card = { game, grid, modifier ->
            LibraryGameCard(game, artwork, grid, modifier, { onOpen(game.id) }) {
                if (game.collection.isNotBlank())
                    Text(game.collection, style = MaterialTheme.typography.bodySmall)
                if (place == Place.PAUSED && game.pauseNote.isNotBlank())
                    Text(
                        game.pauseNote,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
        },
    )
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
                AeroButton(onClick = onChooseBacklog) { Text("from backlog") }
            }
        }
        items(row, key = { it.id }) { game ->
            Glass(Modifier.fillMaxWidth().animateItem(placementSpec = spring(stiffness = 350f))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AeroButton(onClick = { onOpen(game.id) }) {
                        Text(game.title, fontWeight = FontWeight.SemiBold)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AeroButton(
                            onClick = { onMove(game.id, -1) },
                            enabled = row.first().id != game.id,
                        ) {
                            AeroIcon(AeroSymbol.UP, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("move up")
                        }
                        AeroButton(
                            onClick = { onMove(game.id, 1) },
                            enabled = row.last().id != game.id,
                        ) {
                            AeroIcon(AeroSymbol.DOWN, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("move down")
                        }
                        AeroButton(onClick = { onBacklog(game.id) }) { Text("to backlog") }
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
    onEditCount: () -> Unit,
    onRelocate: () -> Unit,
    onDelete: () -> Unit,
    onCover: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameIdentity(game, artwork, showShelf = true, prominent = true)
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
        if (game.shelf != Shelf.HUNTS) {
            if (game.place == Place.ACTIVE || game.place == Place.BACKLOG) {
                AeroActionRow(
                    first = { AeroButton(onClick = onRelocate) { Text("move game") } },
                    second = { AeroButton(onClick = onPause) { Text("pause game") } },
                )
            } else
                AeroButton(onClick = onRelocate, modifier = Modifier.fillMaxWidth()) {
                    Text("move game")
                }
        }
        if (game.inPlayLog)
            AeroButton(onClick = onPlayLog, modifier = Modifier.fillMaxWidth()) {
                Text("finished playthroughs")
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
                            "${game.encounters} ${if (game.encounters == 1L) "encounter" else "encounters"}",
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
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AeroButton(
                            onClick = { onEncounter(-1) },
                            modifier = Modifier.weight(1f),
                            enabled = game.encounters > 0,
                            haptic = true,
                            minHeight = 56.dp,
                        ) {
                            Text("−1", fontSize = 20.sp)
                        }
                        PinkButton(
                            onClick = { onEncounter(1) },
                            modifier = Modifier.weight(1f),
                            enabled = game.encounters < Long.MAX_VALUE,
                            haptic = true,
                            minHeight = 56.dp,
                        ) {
                            Text("+1", fontSize = 20.sp)
                        }
                    }
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
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (game.shelf != Shelf.HUNTS)
                AeroButton(onClick = onCover, modifier = Modifier.weight(1f)) {
                    Text("change cover art")
                }
            AeroIconButton(
                AeroSymbol.DELETE,
                "delete game",
                onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
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
    var spriteRequest by remember { mutableIntStateOf(0) }
    var selectingPokemon by remember { mutableStateOf(false) }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        val selected = artwork.select(uri)
                        if (selectingSprite) {
                            spriteRequest++
                            busy = false
                            sprite = selected
                        } else {
                            cover = selected
                            selectedCoverProvider = "Selected image"
                        }
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
        }
    fun findSprite() {
        if (pokemon.isBlank() || version.isBlank() || version == "other") return
        val request = ++spriteRequest
        val wantedPokemon = pokemon
        val wantedVersion = version
        busy = true
        error = null
        scope.launch {
            try {
                val found = catalog.shinySprite(wantedPokemon, wantedVersion)
                if (
                    request == spriteRequest && pokemon == wantedPokemon && version == wantedVersion
                )
                    sprite = found
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (request == spriteRequest) error = e.message
            } finally {
                if (request == spriteRequest) busy = false
            }
        }
    }
    if (selectingPokemon)
        AeroDialog(
            onDismissRequest = { selectingPokemon = false },
            title = { Text("choose a Pokémon") },
            text = {
                Box(Modifier.height(400.dp).fillMaxWidth()) {
                    PokemonPicker(catalog.pokemon) { choice ->
                        pokemon = choice.slug
                        sprite = ""
                        selectingPokemon = false
                        findSprite()
                    }
                }
            },
            confirmButton = {
                AeroButton(onClick = { selectingPokemon = false }) { Text("cancel") }
            },
        )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        if (shelf != Shelf.HUNTS) {
            AeroTextField(
                title,
                { title = it },
                label = "title",
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
            AeroButton(
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
                    cornerRadius = 12.dp,
                    showPlaceholderTitle = false,
                    system = system,
                    coverBounds = CoverSize(90f, 120f),
                )
        }
        if (shelf == Shelf.HUNTS) {
            Text(displayPokemonName(pokemon), style = MaterialTheme.typography.titleLarge)
            AeroButton(onClick = { selectingPokemon = true }) { Text("change Pokémon") }
            Choice(
                "Pokémon game",
                huntVersions[version] ?: "choose a game",
                huntVersions.values.toList(),
            ) { value ->
                version = huntVersions.entries.first { it.value == value }.key
                spriteRequest++
                busy = false
                error = null
                sprite = ""
                odds = ""
                title = "Pokémon ${huntVersions.getValue(version)}"
                system = huntSystem(version)
                findSprite()
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AeroButton(
                    onClick = { findSprite() },
                    enabled = !busy && pokemon.isNotBlank() && version.isNotBlank(),
                ) {
                    Text(if (busy) "finding artwork…" else "find shiny artwork")
                }
                AeroButton(
                    onClick = {
                        selectingSprite = true
                        imagePicker.launch("image/*")
                    }
                ) {
                    Text("choose image")
                }
            }
            if (sprite.isNotBlank()) {
                Art(
                    sprite,
                    artwork,
                    pokemon,
                    Modifier.size(112.dp),
                    sprite = true,
                    transparent = true,
                )
                if ("serebii.net" in sprite)
                    Text("Shiny artwork via Serebii", style = MaterialTheme.typography.bodySmall)
            }
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
                AeroCheckbox(
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
                    require(shelf != Shelf.HUNTS || version in huntVersions) {
                        "Choose the Pokémon game"
                    }
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
            Text(if (shelf == Shelf.HUNTS) "save hunt" else "save game")
        }
        AeroButton(onClick = onCancel) { Text("cancel") }
    }
}

@Composable
fun AddScreen(
    catalog: Catalog,
    endpoint: String,
    results: List<CatalogResult>,
    onResults: (List<CatalogResult>) -> Unit,
    onChoose: (CatalogResult) -> Unit,
    onManual: () -> Unit,
    onHunt: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf("") }
    var warning by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var nextOffset by rememberSaveable { mutableStateOf<Int?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    fun search(offset: Int = 0) {
        if (busy) return
        focus.clearFocus()
        keyboard?.hide()
        val submittedQuery = query.trim()
        val anchorIndex = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        val submittedSystem = system
        busy = true
        loadingMore = offset > 0
        warning = null
        scope.launch {
            try {
                val answer = catalog.searchPage(submittedQuery, submittedSystem, endpoint, offset)
                if (query.trim() != submittedQuery || system != submittedSystem) return@launch
                val page = groupCatalogResults(answer.games)
                onResults(if (offset == 0) page else mergeCatalogResults(results, page))
                nextOffset = answer.nextOffset
                warning = answer.warning
                if (offset == 0) listState.requestScrollToItem(0)
                else listState.requestScrollToItem(anchorIndex, anchorOffset)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                warning = "Search unavailable. You can try again or add a game manually."
            } finally {
                busy = false
                loadingMore = false
            }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AeroTextField(
            query,
            {
                query = it
                nextOffset = null
            },
            label = "game title",
            autoFocus = true,
            onSubmit = { if (query.isNotBlank()) search() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Choice("system", system.ifBlank { "all systems" }, listOf("all systems") + systems) {
            system = if (it == "all systems") "" else it
            nextOffset = null
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PinkButton(onClick = { search() }, enabled = !busy && query.isNotBlank()) {
                Text(if (busy && !loadingMore) "searching…" else "search")
            }
            AeroButton(onClick = onManual) { Text("add manually") }
            AeroButton(onClick = onHunt) { Text("add a hunt") }
        }
        warning?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(results, key = { catalogTitleKey(it.game.title) + ":" + it.game.system }) { result
                ->
                val game = result.game
                Glass(Modifier.fillMaxWidth().clickable { onChoose(result) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(game.title, fontWeight = FontWeight.SemiBold)
                        Text(game.system, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            nextOffset?.let { offset ->
                item(key = "next catalog page") {
                    AeroButton(
                        onClick = { search(offset) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    ) {
                        Text(if (loadingMore) "loading…" else "load more games")
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
                    "A general invitation to enjoy your games, with an optional evening check-in for your play log."
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("rest invitations", Modifier.weight(1f))
                    AeroSwitch(
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
                                .height(64.dp)
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
                            verticalArrangement =
                                Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                        ) {
                            AeroCheckbox(checked, null, Modifier.size(20.dp))
                            Text(day, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Glass(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "from ${timeLabel(settings.startMinute)}",
                            Modifier.weight(1f)
                                .clickable(role = Role.Button) { pickTime(true) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        VerticalDivider(
                            Modifier.height(22.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = .3f),
                        )
                        Text(
                            "to ${timeLabel(settings.endMinute)}",
                            Modifier.weight(1f)
                                .clickable(role = Role.Button) { pickTime(false) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("evening play log")
                        Text(
                            "Around 9pm on these days. Skipped or already logged days stay quiet.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    AeroSwitch(settings.logReminder, { onChange(settings.copy(logReminder = it)) })
                }
                Text(
                    "Delivery can vary with Android battery settings. Invitations arriving after your window are discarded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                AeroButton(
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
                AeroButton(
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
fun Savepoint(openLater: Boolean, openDailyLog: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { Library(context) }
    val settingsStore = remember { SettingsStore(context) }
    val artwork = remember { Artwork(context) }
    val catalog = remember { Catalog(context) }
    val games by library.games.collectAsState()
    var settings by remember { mutableStateOf(Settings()) }
    var loaded by remember { mutableStateOf(false) }
    val screenStates = rememberSaveableStateHolder()
    var searchCovers by remember { mutableStateOf<List<CoverCandidate>>(emptyList()) }
    var catalogResults by remember { mutableStateOf<List<CatalogResult>>(emptyList()) }
    val navigation =
        rememberSaveable(
            saver =
                Saver<ScreenHistory, List<String>>(
                    save = { it.savedRoutes },
                    restore = { ScreenHistory(it) },
                )
        ) {
            ScreenHistory()
        }
    var route by navigation
    var dailyDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var dailyPreselected by rememberSaveable { mutableStateOf<String?>(null) }
    var savingDaily by remember { mutableStateOf(false) }
    var celebration by rememberSaveable { mutableStateOf<String?>(null) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val calendarLocale = LocalConfiguration.current.locales[0]
    var preparedCalendar by remember { mutableStateOf<PlayCalendarMonth?>(null) }
    LaunchedEffect(loaded, today, calendarLocale) {
        val month = YearMonth.from(today)
        if (
            loaded &&
                (preparedCalendar?.month != month || preparedCalendar?.locale != calendarLocale)
        ) {
            preparedCalendar =
                withContext(Dispatchers.Default) { playCalendarMonth(month, calendarLocale) }
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) today = LocalDate.now()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var selected by rememberSaveable { mutableStateOf("") }
    var selectedCollection by rememberSaveable { mutableStateOf("") }
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
                library.change { list ->
                    list.map { game ->
                        if (game.id == id) {
                            game.copy(encounters = transform(game.encounters))
                        } else game
                    }
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    fun saveGame(game: Game) {
        screenStates.removeState("form:${game.id}")
        val adding = games.none { it.id == game.id }
        val newFinishedGame = adding && game.place == Place.FINISHED
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
        val destination = if (game.place == Place.FINISHED) "journal" else "details"
        if (adding) navigation.finishAdding(destination) else route = destination
        if (newFinishedGame) {
            logEntry = Playthrough(finishedAt = null)
            dialog = "entry"
        }
    }
    fun openDaily(game: String? = null) {
        dailyPreselected = game
        screenStates.removeState("daily:$dailyDate")
        route = "daily"
    }
    fun saveDay(chosen: Map<String, Int?>) {
        if (savingDaily) return
        savingDaily = true
        scope.launch {
            try {
                var before = emptySet<String>()
                library.change { current ->
                    before = reachedPlayMilestones(playedDates(current)).map { it.key }.toSet()
                    saveDailyPlays(current, LocalDate.parse(dailyDate), chosen, LocalDate.now())
                }
                val reached = reachedPlayMilestones(playedDates(library.games.value))
                var fresh = emptyList<PlayMilestone>()
                settings =
                    settingsStore.change { current ->
                        fresh =
                            reached.filter {
                                it.key !in before && it.key !in current.celebratedMilestones
                            }
                        current.copy(
                            celebratedMilestones =
                                current.celebratedMilestones + reached.map { it.key }
                        )
                    }
                celebration =
                    (fresh.lastOrNull { it.key.startsWith("streak:") } ?: fresh.lastOrNull())
                        ?.message
                if (dailyDate == LocalDate.now().toString()) LogReminders.clear(context)
                screenStates.removeState("daily:$dailyDate")
                dailyPreselected = null
                route = "log"
            } catch (e: Exception) {
                error = e.message ?: "Could not save this day"
            } finally {
                savingDaily = false
            }
        }
    }
    fun startAdding(shelf: Shelf?, from: String) {
        screenStates.removeState("add")
        catalogResults = emptyList()
        addShelf = shelf
        addReturn = from
        destinationChosen = false
        selected = ""
        form = null
        screenStates.removeState("hunt-add")
        route = if (shelf == Shelf.HUNTS) "hunt-add" else "add"
    }
    fun backRoute(): String = navigation.previous
    fun updateSettings(value: Settings) {
        val previous = settings
        scope.launch {
            try {
                var reschedule = false
                settings =
                    settingsStore.change { current ->
                        mergeSettings(current, previous, value).also { changed ->
                            reschedule = reminderSettingsChanged(current, changed)
                        }
                    }
                if (reschedule) {
                    Reminders.schedule(context, settings)
                    if (!settings.logReminder) LogReminders.clear(context)
                    if (!settings.reminders || settings.skipDate == LocalDate.now().toString())
                        Reminders.clear(context)
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    fun renameSavedCollection(previous: String, name: String, completed: (String?) -> Unit) {
        scope.launch {
            var members = emptySet<String>()
            try {
                val savedSettings = settingsStore.read()
                library.change { current ->
                    val renamed = renameCollection(current, savedSettings, previous, name)
                    members = current.filter { it.collection == previous }.map { it.id }.toSet()
                    renamed.games
                }
                settings = settingsStore.change { collectionRenameSettings(it, previous, name) }
                if (selectedCollection == previous) selectedCollection = name
                completed(null)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (members.isNotEmpty())
                    runCatching {
                        library.change { current ->
                            current.map {
                                if (it.id in members && it.collection == name)
                                    it.copy(collection = previous)
                                else it
                            }
                        }
                    }
                completed(e.message ?: "Could not rename this collection")
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
            artwork.prepareLibraryArtwork(library.games.value)
            settings = settingsStore.read()
            preparedCalendar =
                withContext(Dispatchers.Default) {
                    playCalendarMonth(YearMonth.from(today), calendarLocale)
                }
            loaded = true
            Reminders.schedule(context, settings)
            if (openLater) dialog = "later"
            if (openDailyLog) {
                dailyDate = LocalDate.now().toString()
                route = "log"
                openDaily()
            }
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
    val backdrop = rememberAeroBackdrop(dark)
    val colors = remember(dark) { aeroColors(dark) }
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
                            "hunt-add" -> "add a hunt"
                            "shelf" -> editingShelf.label
                            "collection" -> "collections"
                            "details" ->
                                if (selectedGame?.shelf == Shelf.HUNTS) "your hunt" else "your game"
                            "catalog",
                            "cover" -> "cover art"
                            "form" ->
                                if (form?.shelf == Shelf.HUNTS) {
                                    if (selectedGame == null) "add a hunt" else "edit hunt"
                                } else if (selectedGame == null) "add game" else "edit game"
                            "log" -> "play log"
                            "finished" -> "finished games"
                            "daily" -> "log play"
                            "journal" -> "finished playthroughs"
                            else -> route
                        },
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (route == "home")
                        AeroIconButton(AeroSymbol.SETTINGS, "settings", { route = "settings" })
                    if (route == "details" && selectedGame != null)
                        AeroIconButton(
                            AeroSymbol.CALENDAR,
                            "log play",
                            {
                                dailyDate = LocalDate.now().toString()
                                openDaily(selectedGame.id)
                            },
                        )
                    if (route in listOf("backlog", "paused", "finished"))
                        AeroIconButton(
                            if ((if (route == "finished") "log" else route) in settings.gridViews)
                                AeroSymbol.LIST
                            else AeroSymbol.GRID,
                            if ((if (route == "finished") "log" else route) in settings.gridViews)
                                "list view"
                            else "grid view",
                            {
                                updateSettings(
                                    settings.copy(
                                        gridViews =
                                            if (
                                                (if (route == "finished") "log" else route) in
                                                    settings.gridViews
                                            )
                                                settings.gridViews -
                                                    (if (route == "finished") "log" else route)
                                            else
                                                settings.gridViews +
                                                    (if (route == "finished") "log" else route)
                                    )
                                )
                            },
                        )
                    if (route in listOf("home", "backlog", "paused", "finished"))
                        AeroIconButton(AeroSymbol.SEARCH, "search your games", { route = "find" })
                }
                Box(Modifier.weight(1f)) {
                    if (!loaded) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else
                        ScreenContentHost(route, Modifier.fillMaxSize()) { screen ->
                            val stateKey =
                                when (screen) {
                                    "details",
                                    "journal",
                                    "cover" -> "$screen:$selected"
                                    "form",
                                    "catalog" -> "$screen:${form?.id}"
                                    "shelf" -> "$screen:${editingShelf.name}"
                                    "collection" -> "$screen:$selectedCollection"
                                    "daily" -> "$screen:$dailyDate"
                                    else -> screen
                                }
                            screenStates.SaveableStateProvider(stateKey) {
                                val reservesDock =
                                    remember(screen) {
                                        screen in dockRoutes ||
                                            (screen == "form" && selectedGame == null)
                                    }
                                Box(
                                    Modifier.fillMaxSize()
                                        .padding(bottom = if (reservesDock) 88.dp else 0.dp)
                                ) {
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
                                        "collections",
                                        "collection" ->
                                            CollectionsScreen(
                                                games,
                                                settings,
                                                artwork,
                                                if (screen == "collection") selectedCollection
                                                else "",
                                                { name ->
                                                    updateSettings(
                                                        settings.copy(
                                                            collectionNames =
                                                                settings.collectionNames + name
                                                        )
                                                    )
                                                },
                                                ::renameSavedCollection,
                                                { name, ids ->
                                                    updateSettings(
                                                        settings.copy(
                                                            collectionNames =
                                                                settings.collectionNames + name
                                                        )
                                                    )
                                                    mutate { setCollectionMembers(it, name, ids) }
                                                },
                                                { name, id, direction, finished ->
                                                    scope.launch {
                                                        try {
                                                            var changed = false
                                                            library.change { current ->
                                                                val updated =
                                                                    reorderCollection(
                                                                        current,
                                                                        name,
                                                                        id,
                                                                        direction,
                                                                    )
                                                                changed = updated != current
                                                                updated
                                                            }
                                                            finished(changed)
                                                        } catch (e: Exception) {
                                                            if (e is CancellationException) throw e
                                                            error = e.message
                                                            finished(false)
                                                        }
                                                    }
                                                },
                                                { name ->
                                                    selectedCollection = name
                                                    route = "collection"
                                                },
                                                {
                                                    selected = it
                                                    route = "details"
                                                },
                                            )
                                        "log" ->
                                            DailyLogScreen(
                                                games,
                                                artwork,
                                                LocalDate.parse(dailyDate),
                                                today,
                                                { dailyDate = it.toString() },
                                                { openDaily() },
                                                { route = "finished" },
                                                celebration,
                                                { celebration = null },
                                                preparedCalendar,
                                            )
                                        "daily" ->
                                            DailyPlayEditor(
                                                games,
                                                artwork,
                                                LocalDate.parse(dailyDate),
                                                today,
                                                savingDaily,
                                                ::saveDay,
                                                { route = backRoute() },
                                                dailyPreselected,
                                            )
                                        "finished" ->
                                            PlayLogList(
                                                games,
                                                artwork,
                                                "log" in settings.gridViews,
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
                                                        route = navigation.libraryReturn
                                                    },
                                                    { route = "details" },
                                                )
                                            }
                                        "shelf" ->
                                            ShelfEditor(
                                                editingShelf,
                                                games,
                                                { id, direction ->
                                                    mutate {
                                                        LibraryRules.reorder(it, id, direction)
                                                    }
                                                },
                                                {
                                                    selected = it
                                                    route = "details"
                                                },
                                                { id ->
                                                    mutate {
                                                        LibraryRules.move(it, id, Place.BACKLOG)
                                                    }
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
                                                                LibraryRules.move(
                                                                    it,
                                                                    game.id,
                                                                    Place.PAUSED,
                                                                )
                                                            }
                                                            route = navigation.libraryReturn
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
                                                            route = navigation.libraryReturn
                                                        } else {
                                                            logEntry = Playthrough()
                                                            dialog = "finish"
                                                        }
                                                    },
                                                    { route = "journal" },
                                                    { place, first ->
                                                        mutate {
                                                            LibraryRules.move(
                                                                it,
                                                                game.id,
                                                                place,
                                                                first,
                                                            )
                                                        }
                                                        route = navigation.libraryReturn
                                                    },
                                                    { delta ->
                                                        changeEncounters(game.id) {
                                                            adjustedEncounterCount(it, delta)
                                                        }
                                                    },
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
                                                    { route = "cover" },
                                                )
                                            }
                                        "hunt-add" ->
                                            Box(
                                                Modifier.fillMaxSize().padding(horizontal = 20.dp)
                                            ) {
                                                PokemonPicker(catalog.pokemon) { pokemon ->
                                                    form =
                                                        Game(
                                                            title = "",
                                                            shelf = Shelf.HUNTS,
                                                            place = Place.ACTIVE,
                                                            pokemon = pokemon.slug,
                                                            pokemonVersion = "",
                                                        )
                                                    selected = ""
                                                    destinationChosen = true
                                                    route = "form"
                                                }
                                            }
                                        "add" ->
                                            AddScreen(
                                                catalog,
                                                settings.catalogUrl,
                                                catalogResults,
                                                { catalogResults = it },
                                                { result ->
                                                    form =
                                                        result.game.copy(
                                                            shelf = addShelf ?: Shelf.PLAY_THROUGH
                                                        )
                                                    searchCovers = result.covers
                                                    selected = ""
                                                    route = "catalog"
                                                },
                                                {
                                                    if (
                                                        form == null ||
                                                            games.any { it.id == form?.id }
                                                    )
                                                        form =
                                                            Game(
                                                                title = "",
                                                                shelf =
                                                                    addShelf ?: Shelf.PLAY_THROUGH,
                                                            )
                                                    selected = ""
                                                    route = "form"
                                                },
                                                onHunt = { startAdding(Shelf.HUNTS, "add") },
                                            )
                                        "cover" ->
                                            selectedGame?.let { game ->
                                                CoverSelection(
                                                    game,
                                                    emptyList(),
                                                    artwork,
                                                    catalog,
                                                    settings.catalogUrl,
                                                    onPick = { cover ->
                                                        mutate { list ->
                                                            list.map {
                                                                if (it.id == game.id)
                                                                    it.copy(
                                                                        cover = cover?.url ?: "",
                                                                        coverProvider =
                                                                            cover?.provider ?: "",
                                                                    )
                                                                else it
                                                            }
                                                        }
                                                        route = "details"
                                                    },
                                                    existing = true,
                                                )
                                            }
                                        "catalog" ->
                                            form?.let { game ->
                                                CoverSelection(
                                                    game,
                                                    searchCovers,
                                                    artwork,
                                                    catalog,
                                                    settings.catalogUrl,
                                                    { cover ->
                                                        form =
                                                            game.copy(
                                                                cover = cover?.url ?: "",
                                                                coverProvider =
                                                                    cover?.provider ?: "",
                                                            )
                                                        dialog = "destination"
                                                    },
                                                )
                                            }
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
                                                        screenStates.removeState(
                                                            "form:${initial.id}"
                                                        )
                                                        if (selectedGame != null) route = "details"
                                                        else {
                                                            form = null
                                                            route = "add"
                                                        }
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
                                                    else
                                                        updateSettings(
                                                            settings.copy(reminders = true)
                                                        )
                                                },
                                                {
                                                    export.launch(
                                                        "savepoint-${LocalDate.now()}.zip"
                                                    )
                                                },
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
                        }
                    if (route in dockRoutes || (route == "form" && selectedGame == null))
                        AeroDock(
                            if (route in setOf("daily", "finished")) "log" else route,
                            { route = it },
                            {
                                if (route == "add") {
                                    if (form == null || games.any { it.id == form?.id })
                                        form =
                                            Game(title = "", shelf = addShelf ?: Shelf.PLAY_THROUGH)
                                    route = "form"
                                } else if (route == "form" || route == "catalog") route = "add"
                                else
                                    startAdding(
                                        if (route == "backlog") null else Shelf.PLAY_THROUGH,
                                        route,
                                    )
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                }
            }
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
                    dismissButton = { AeroButton(onClick = { dialog = "" }) { Text("cancel") } },
                )
            }
            if (dialog == "delete" && selectedGame != null) {
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("delete ${selectedGame.title}?") },
                    text = {
                        Text(
                            "Remove this game and its saved notes, daily logs and playthroughs from Savepoint?"
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
                    dismissButton = { AeroButton(onClick = { dialog = "" }) { Text("cancel") } },
                )
            }
            if (dialog == "destination" && form != null) {
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("where should it go?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                form!!.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            Shelf.entries.forEach { destination ->
                                AeroButton(
                                    onClick = {
                                        val game =
                                            form!!.copy(shelf = destination, place = Place.ACTIVE)
                                        dialog = ""
                                        if (destination == Shelf.HUNTS && game.pokemon.isBlank()) {
                                            form =
                                                game.copy(
                                                    pokemonVersion =
                                                        inferHuntVersion(game.title) ?: "other"
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
                            listOf(Place.BACKLOG to "backlog", Place.FINISHED to "finished games")
                                .forEach { (place, label) ->
                                    AeroButton(
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
                    confirmButton = { AeroButton(onClick = { dialog = "" }) { Text("cancel") } },
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
                                    route = navigation.libraryReturn
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
                    confirmButton = { AeroButton(onClick = { error = null }) { Text("okay") } },
                )
            }
        }
    }
}
