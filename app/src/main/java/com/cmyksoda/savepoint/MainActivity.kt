package com.cmyksoda.savepoint

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    describe: Boolean = true,
    preview: Boolean = false,
    shadow: Dp = 0.dp,
    rim: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current.density
    val decodeSize = coverBounds?.let { (maxOf(it.width, it.height) * density).roundToInt() } ?: 0
    var bitmap by remember(url, decodeSize) { mutableStateOf(artwork.memoryBitmap(url, sprite, decodeSize)) }
    var settled by remember(url, decodeSize) { mutableStateOf(bitmap != null) }
    LaunchedEffect(url, sprite, decodeSize) {
        try {
            if (sprite) {
                bitmap =
                    runCatching { if (preview) artwork.previewSprite(url) else artwork.spriteBitmap(url) }
                        .getOrNull()
                return@LaunchedEffect
            }
            if (preview) {
                bitmap = runCatching { artwork.previewBitmap(url) }.getOrNull()
                return@LaunchedEffect
            }
            bitmap = runCatching { artwork.cachedCoverBitmap(url, decodeSize) }.getOrNull() ?: bitmap
            if (bitmap != null) return@LaunchedEffect
            val preferred = preferredCoverUrl(url)
            bitmap =
                runCatching { artwork.bitmap(preferred, decodeSize) }.getOrNull()
                    ?: bitmap
                    ?: runCatching { artwork.bitmap(url, decodeSize) }.getOrNull()
        } finally {
            settled = true
        }
    }
    // A cover already saved on the phone fades in while it decodes instead of flashing the no-cover art.
    val reveal by
        animateFloatAsState(
            if (settled || artwork.cachedAspectRatio(url) == null) 1f else 0f,
            tween(140),
            label = "cover reveal",
        )
    val frame =
        coverBounds?.let {
            if (sprite) it
            else
                fitCover(
                    // The saved file's shape comes first, so a card keeps one height whether or not it's decoded.
                    artwork.cachedAspectRatio(url)
                        ?: bitmap?.let { cover -> cover.width.toFloat() / cover.height }
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
    val cased = LocalGameCases.current && !sprite && !transparent && cornerRadius > 0.dp
    val radius =
        if (sprite || cornerRadius == 0.dp) cornerRadius
        else coverRadius(system, cornerRadius, frame?.width?.dp)
    val body: @Composable () -> Unit = {
        Box(
            size
                .graphicsLayer { alpha = reveal }
                .then(if (shadow > 0.dp) Modifier.softGlassShadow(radius, shadow) else Modifier)
                .then(
                    if (sprite || cornerRadius == 0.dp) Modifier
                    else Modifier.clip(RoundedCornerShape(radius))
                )
                .then(modifier)
                .then(if (rim) Modifier.border(1.dp, coverRim(system), RoundedCornerShape(radius)) else Modifier)
                .then(if (cased) Modifier.caseFinish(caseKind(system)) else Modifier)
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
                    if (describe) title else null,
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

val ShelfCoverBounds = CoverSize(144f, 176f)
val PlayingCoverBounds = CoverSize(216f, 244f)

@Composable
fun GameCover(
    game: Game,
    artwork: Artwork,
    focus: Boolean,
    modifier: Modifier = Modifier,
    ground: CoverGround = CoverGround.NONE,
    onOpen: () -> Unit,
) {
    val hunt = game.shelf == Shelf.HUNTS
    if (hunt) {
        Column(
            modifier.width(112.dp).feedbackClickable(onClick = onOpen).clearAndSetSemantics {
                contentDescription =
                    "${displayPokemonName(game.pokemon).ifBlank { "shiny hunt" }}, " +
                        (huntVersions[game.pokemonVersion] ?: game.title)
                role = Role.Button
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
                describe = false,
            )
            Text(
                displayPokemonName(game.pokemon).ifBlank { "Shiny hunt" },
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
    val dark = LocalAeroDark.current
    Box(
        modifier.then(
            when (ground) {
                CoverGround.CONTACT -> Modifier.contactShadow(dark)
                CoverGround.FOOT -> Modifier.footShadow(dark)
                CoverGround.REFLECTION -> Modifier.coverReflection(26.dp, dark)
                CoverGround.NONE -> Modifier
            }
        )
    ) {
        Art(
            game.cover,
            artwork,
            game.title,
            Modifier.feedbackClickable(onClick = onOpen)
                .semantics {
                    contentDescription = "${game.title}${if (focus) ", playing now" else ""}"
                },
            cornerRadius = 10.dp,
            reserveHeight = false,
            describe = false,
            system = game.system,
            coverBounds = if (focus) PlayingCoverBounds else ShelfCoverBounds,
            shadow = 8.dp,
            rim = true,
        ) {
            if (focus)
                Surface(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color(0xE0212A40),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "Playing now",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
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
    onRestore: () -> Unit,
    style: ShelfStyle = ShelfStyle.LEDGE,
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
                            "A place for your games",
                            fontSize = 22.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Bring a few games onto your shelves. The rest can stay tucked away.")
                        PinkButton(onClick = onAdd) { Text("Add a game") }
                        if (games.isEmpty())
                            AeroButton(onClick = onRestore) { Text("Restore a backup") }
                    }
                }
            }
        Shelf.entries.forEach { shelf ->
            val row = active.filter { it.shelf == shelf }.sortedBy { it.position }
            if (row.isNotEmpty())
                item(key = shelf.name) {
                    ShelfSection(shelf, row, style, artwork, onOpen, onEditShelf)
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
                Choice("Sort", settings.sort.label, LibrarySort.entries.map { it.label }) { value ->
                    onSettings(
                        settings.copy(sort = LibrarySort.entries.first { it.label == value })
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        val present = games.filter { it.place == place }.map { it.system }.toSet()
                        Choice(
                            "System",
                            settings.systemFilter.ifBlank { "All systems" },
                            listOf("All systems") +
                                systems.filter { it in present || it == settings.systemFilter },
                        ) {
                            onSettings(
                                settings.copy(systemFilter = if (it == "All systems") "" else it)
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Choice(
                            "Collection",
                            settings.collectionFilter.ifBlank { "All collections" },
                            listOf("All collections") + collections,
                            menuAction = "Manage collections" to onCollections,
                        ) {
                            onSettings(
                                settings.copy(
                                    collectionFilter = if (it == "All collections") "" else it
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
    artwork: Artwork,
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
                PinkButton(onClick = onAdd) {
                    Text(if (shelf == Shelf.HUNTS) "Add a hunt" else "Add a game")
                }
                if (shelf != Shelf.HUNTS)
                    AeroButton(onClick = onChooseBacklog) { Text("From backlog") }
            }
        }
        items(row, key = { it.id }) { game ->
            val name = displayPokemonName(game.pokemon).ifBlank { "Shiny hunt" }
            ArrangeCard(
                Modifier.fillMaxWidth().animateItem(placementSpec = spring(stiffness = 350f)),
                canMoveUp = row.first().id != game.id,
                canMoveDown = row.last().id != game.id,
                onOpen = { onOpen(game.id) },
                onMove = { onMove(game.id, it) },
                extra =
                    if (shelf == Shelf.HUNTS) null
                    else {
                        {
                            AeroIconButton(
                                AeroSymbol.BACKLOG,
                                "Send ${game.title} to backlog",
                                { onBacklog(game.id) },
                            )
                        }
                    },
            ) {
                if (shelf == Shelf.HUNTS) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            huntVersions[game.pokemonVersion] ?: game.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            encounterLabel(game.encounters),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Art(
                        game.sprite,
                        artwork,
                        name,
                        Modifier.size(72.dp),
                        sprite = true,
                        transparent = true,
                        showPlaceholderTitle = false,
                        describe = false,
                    )
                } else
                    GameArrangeIdentity(
                        game,
                        artwork,
                        if (shelf == Shelf.PLAY_THROUGH && row.first().id == game.id) "Playing now"
                        else null,
                    )
            }
        }
    }
}

fun encounterLabel(encounters: Long): String =
    "${NumberFormat.getIntegerInstance().format(encounters)} " +
        if (encounters == 1L) "encounter" else "encounters"

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
    playingNow: Boolean = false,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameIdentity(game, artwork, showShelf = true, prominent = true, playingNow = playingNow)
        if (game.shelf != Shelf.HUNTS && game.pauseNote.isNotBlank())
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("For when you return", fontWeight = FontWeight.SemiBold)
                    Text(game.pauseNote)
                }
            }
        if (game.shelf == Shelf.HUNTS)
            AeroButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                AeroIcon(AeroSymbol.EDIT, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit hunt")
            }
        else {
            AeroActionRow(
                first = {
                    AeroButton(onClick = onNotes) {
                        AeroIcon(AeroSymbol.LOG, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Notes")
                    }
                },
                second = {
                    AeroButton(onClick = onEdit) {
                        AeroIcon(AeroSymbol.EDIT, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit game")
                    }
                },
            )
        }
        if (game.shelf != Shelf.HUNTS) {
            if (game.place == Place.ACTIVE || game.place == Place.BACKLOG) {
                AeroActionRow(
                    first = {
                        AeroButton(onClick = onRelocate) {
                            AeroIcon(AeroSymbol.SHELVES, Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Move game")
                        }
                    },
                    second = {
                        AeroButton(onClick = onPause) {
                            AeroIcon(AeroSymbol.PAUSE, Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pause game")
                        }
                    },
                )
            } else
                AeroButton(onClick = onRelocate, modifier = Modifier.fillMaxWidth()) {
                    AeroIcon(AeroSymbol.SHELVES, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Move game")
                }
        }
        if (game.inPlayLog)
            AeroButton(onClick = onPlayLog, modifier = Modifier.fillMaxWidth()) {
                Text("Finished playthroughs")
            }
        if (game.shelf == Shelf.HUNTS) {
            val odds = calculatedOdds(game)
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .feedbackClickable(onClick = onEditCount)
                            .semantics { contentDescription = "Change encounter count" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            encounterLabel(game.encounters),
                            fontSize = 24.sp,
                            modifier = Modifier.weight(1f),
                        )
                        AeroIcon(
                            AeroSymbol.EDIT,
                            Modifier.size(17.dp),
                            MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(game.method.ifBlank { "Custom method" })
                    Text(
                        odds.first?.let { "1 in ${formatHuntOdds(it)} · ${odds.second}" }
                            ?: odds.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (
                        game.shinyCharm &&
                            charmApplies(game.pokemonVersion, game.method, game.pokemon)
                    )
                        Text("Shiny charm enabled")
                    huntModifierSummary(game).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AeroButton(
                            onClick = { onEncounter(-1) },
                            modifier = Modifier.weight(1f),
                            enabled = game.encounters > 0,
                            strongHaptic = true,
                            minHeight = 56.dp,
                        ) {
                            Text("−1", fontSize = 20.sp)
                        }
                        PinkButton(
                            onClick = { onEncounter(1) },
                            modifier = Modifier.weight(1f),
                            enabled = game.encounters < Long.MAX_VALUE,
                            strongHaptic = true,
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
            if (game.shelf == Shelf.PLAY_THROUGH && !playingNow)
                PinkButton(
                    onClick = { onMove(Place.ACTIVE, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Play now")
                }
            AeroButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(if (game.shelf == Shelf.HUNTS) "Complete hunt" else "Mark finished")
            }
        } else {
            if (game.shelf == Shelf.PLAY_THROUGH)
                PinkButton(
                    onClick = { onMove(Place.ACTIVE, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (game.inPlayLog) "Replay now" else "Play now")
                }
            AeroButton(
                onClick = { onMove(Place.ACTIVE, false) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (game.shelf == Shelf.PLAY_THROUGH && game.inPlayLog) "Queue a replay"
                    else if (game.shelf == Shelf.PLAY_THROUGH) "Add to queue"
                    else "Send to ${game.shelf.label.lowercase()}"
                )
            }
        }
        val credits =
            listOfNotNull(
                "Title source: GameTDB".takeIf { game.source == "gametdb" },
                "Game data provided by IGDB".takeIf { game.source == "igdb" },
                "Game data from the LaunchBox Games Database".takeIf { game.source == "launchbox" },
                "Game data from Steam".takeIf { game.source == "steam" },
                "Game data from the App Store".takeIf { game.source == "appstore" },
                when (val provider = coverProvider(game)) {
                    "This phone" -> "Cover is the game's app icon"
                    "Selected image" -> "Cover is an image you chose"
                    else -> "Cover via $provider"
                }.takeIf { game.cover.isNotBlank() && game.shelf != Shelf.HUNTS },
                "Pokémon artwork via PokéAPI · respective owners retain artwork rights"
                    .takeIf { game.sprite.startsWith("https://raw.githubusercontent.com/PokeAPI") },
            )
        if (credits.isNotEmpty())
            Text(
                credits.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (game.shelf != Shelf.HUNTS)
                AeroButton(onClick = onCover, modifier = Modifier.weight(1f)) {
                    Text("Change cover art")
                }
            AeroIconButton(
                AeroSymbol.DELETE,
                "Delete game",
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
    offeredSystems: List<String> = systems,
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
    var huntConfig by
        rememberSaveable(initial.id, stateSaver = HuntConfigSaver) {
            mutableStateOf(initial.huntConfig)
        }
    var huntIndex by remember { mutableStateOf<HuntCatalogIndex?>(null) }
    var huntCatalogError by remember { mutableStateOf(false) }
    LaunchedEffect(shelf) {
        if (shelf == Shelf.HUNTS) {
            try {
                huntIndex = catalog.pokemon.index()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                huntCatalogError = true
            }
        }
    }
    val availableGames = huntIndex?.games(pokemon).orEmpty()
    val methods =
        if (version == "other") emptyList() else huntIndex?.methods(pokemon, version).orEmpty()
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
            title = { Text("Choose a Pokémon") },
            text = {
                Box(Modifier.height(400.dp).fillMaxWidth()) {
                    PokemonPicker(catalog.pokemon, artwork) { choice ->
                        if (pokemon != choice.slug) {
                            pokemon = choice.slug
                            spriteRequest++
                            busy = false
                            sprite = ""
                            odds = ""
                            huntConfig = HuntConfig()
                            if (
                                huntIndex?.supports(pokemon, version) != true && version != "other"
                            ) {
                                version = ""
                                charm = false
                            }
                            method =
                                huntIndex?.methods(pokemon, version)?.firstOrNull()
                                    ?: "Random encounters"
                            findSprite()
                        }
                        selectingPokemon = false
                    }
                }
            },
            confirmButton = {
                AeroButton(onClick = { selectingPokemon = false }) { Text("Cancel") }
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
                label = "Title",
                modifier = Modifier.fillMaxWidth(),
            )
            Choice("System", system, offeredSystems) { system = it }
            if (existing)
                Choice("Shelf", shelf.label, Shelf.entries.map { it.label }) { label ->
                    shelf = Shelf.entries.first { it.label == label }
                }
            AeroTextField(
                collection,
                { collection = it },
                label = "Collection (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            if (collection.isNotBlank())
                AeroTextField(
                    collectionOrder,
                    { collectionOrder = it },
                    label = "Position in collection",
                    modifier = Modifier.fillMaxWidth(),
                )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Release date (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AeroButton(
                        onClick = {
                            val shown = release.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
                            android.app.DatePickerDialog(
                                    context,
                                    { _, y, m, d -> release = LocalDate.of(y, m + 1, d).toString() },
                                    shown.year,
                                    shown.monthValue - 1,
                                    shown.dayOfMonth,
                                )
                                .show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        AeroIcon(AeroSymbol.CALENDAR, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            release.takeIf { it.isNotBlank() }
                                ?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) }
                                ?: "Choose a date",
                            Modifier.weight(1f),
                        )
                    }
                    if (release.isNotBlank())
                        AeroIconButton(AeroSymbol.CLOSE, "Clear release date", { release = "" })
                }
            }
            AeroButton(
                onClick = {
                    selectingSprite = false
                    imagePicker.launch("image/*")
                }
            ) {
                Text("Choose cover image")
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
            AeroButton(onClick = { selectingPokemon = true }) { Text("Change Pokémon") }
            Choice(
                "Pokémon game",
                huntVersions[version] ?: "Choose a game",
                availableGames.map { huntVersions.getValue(it) } + huntVersions.getValue("other"),
            ) { value ->
                val nextVersion = huntVersions.entries.first { it.value == value }.key
                if (version != nextVersion) {
                    version = nextVersion
                    method = huntIndex?.methods(pokemon, version)?.firstOrNull() ?: ""
                    charm = charm && hasShinyCharm(version)
                    huntConfig =
                        HuntConfig(research = if (charm && version == "legends-arceus") 1 else 0)
                    spriteRequest++
                    busy = false
                    error = null
                    sprite = ""
                    odds = ""
                    title = "Pokémon ${huntVersions.getValue(version)}"
                    system = huntSystem(version)
                    findSprite()
                }
            }
            if (huntIndex == null && !huntCatalogError)
                Text("Loading hunt options…", style = MaterialTheme.typography.bodySmall)
            if (huntCatalogError)
                Text(
                    "Hunt options couldn’t load. Reopen this screen to retry.",
                    color = MaterialTheme.colorScheme.error,
                )
            if (huntIndex != null && availableGames.isEmpty())
                Text(
                    "No huntable shiny encounter is listed for this Pokémon or form. Custom games can use your own odds.",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (
                existing &&
                    version !in huntGameEditions &&
                    version != "other" &&
                    version.isNotBlank()
            )
                Text(
                    "This hunt uses a paired game name. Choose an edition for version-specific options.",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (version.isNotBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AeroButton(
                        onClick = { findSprite() },
                        enabled = !busy && pokemon.isNotBlank() && version.isNotBlank(),
                    ) {
                        Text(if (busy) "Finding artwork…" else "Find shiny artwork")
                    }
                    AeroButton(
                        onClick = {
                            selectingSprite = true
                            imagePicker.launch("image/*")
                        }
                    ) {
                        Text("Choose image")
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
                        Text(
                            "Shiny artwork via Serebii",
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
                Choice(
                    "Hunt method",
                    if (method in methods) method else "Other / custom",
                    methods + "Other / custom",
                ) {
                    val nextMethod = if (it == "Other / custom") "" else it
                    if (method != nextMethod) {
                        method = nextMethod
                        huntConfig =
                            huntConfig.copy(
                                chain = 0,
                                sparklingPower = 0,
                                lure = false,
                                searchLevel = 0,
                                battled = 0,
                                diglett = false,
                                radarMusic = false,
                            )
                        odds = ""
                    }
                }
                val targets = huntIndex?.targets(pokemon, version, method).orEmpty()
                if (targets.isNotEmpty() && displayPokemonName(pokemon) !in targets)
                    Text(
                        "Hunt ${targets.joinToString(" or ")}, then evolve or change form to get your target.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (method !in methods)
                    AeroTextField(
                        method,
                        {
                            method = it
                            odds = ""
                        },
                        label = "Custom method",
                        modifier = Modifier.fillMaxWidth(),
                    )
                AeroTextField(
                    count,
                    { count = it },
                    label = "Encounters",
                    modifier = Modifier.fillMaxWidth(),
                )
                HuntModifiers(
                    version,
                    method,
                    charm,
                    huntConfig,
                    pokemon = pokemon,
                    onCharm = {
                        charm = it
                        odds = ""
                    },
                    onConfig = {
                        if (huntConfig != it) {
                            huntConfig = it
                            odds = ""
                        }
                    },
                )
                HuntOddsPreview(
                    initial.copy(
                        pokemonVersion = version,
                        method = method,
                        shinyCharm = charm,
                        huntConfig = huntConfig,
                        manualOdds = odds.toIntOrNull()?.takeIf { it > 0 },
                    )
                )
                AeroTextField(
                    odds,
                    { odds = it },
                    label = "Odds override · 1 in… (optional)",
                    singleLine = true,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "For custom rules or event rates. Changing the game, method, or settings clears this override.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        AeroButton(onClick = onCancel) { Text("Cancel") }
        PinkButton(
            onClick = {
                try {
                    require(shelf != Shelf.HUNTS || version in huntVersions) {
                        "Choose the Pokémon game"
                    }
                    require(
                        shelf != Shelf.HUNTS ||
                            version == "other" ||
                            huntIndex?.supports(pokemon, version) == true ||
                            (existing &&
                                pokemon == initial.pokemon &&
                                version == initial.pokemonVersion)
                    ) {
                        "Choose a game with a huntable encounter for this Pokémon"
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
                            shinyCharm = charm && hasShinyCharm(version),
                            huntConfig = huntConfig,
                            manualOdds = denominator,
                        )
                    )
                } catch (e: Exception) {
                    error = e.message ?: "Please check these values"
                }
            },
            modifier = Modifier.weight(1f),
            enabled =
                !busy &&
                    (shelf != Shelf.HUNTS ||
                        version == "other" ||
                        (huntIndex != null && version in huntVersions)),
        ) {
            Text(if (shelf == Shelf.HUNTS) "Save hunt" else "Save game")
        }
        }
    }
}

@Composable
fun AddScreen(
    catalog: Catalog,
    artwork: Artwork,
    results: List<CatalogResult>,
    onResults: (List<CatalogResult>) -> Unit,
    onChoose: (CatalogResult) -> Unit,
    onManual: () -> Unit,
    onHunt: () -> Unit,
    families: Set<String> = defaultPlatforms,
    library: List<Game> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf("") }
    var warning by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var activeSearch by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Results keep arriving from slower sources; stay at the top until you scroll them yourself.
    var browsing by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is androidx.compose.foundation.interaction.DragInteraction.Start) browsing = true
        }
    }
    fun search() {
        if (query.isBlank()) return
        val generation = ++searchGeneration
        searchJob?.cancel()
        focus.clearFocus()
        keyboard?.hide()
        val submittedQuery = query.trim()
        val submittedSystem = system
        activeSearch = submittedQuery to submittedSystem
        busy = true
        browsing = false
        warning = null
        onResults(emptyList())
        searchJob =
            scope.launch {
                try {
                    catalog.searchUpdates(submittedQuery, submittedSystem, families).collect { answer ->
                        if (
                            query.trim() != submittedQuery ||
                                system != submittedSystem ||
                                generation != searchGeneration
                        )
                            return@collect
                        val page = groupCatalogResults(answer.games)
                        onResults(page)
                        warning = answer.warning
                        if (!browsing && page.isNotEmpty()) listState.requestScrollToItem(0)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    warning = "Search unavailable. You can try again or add a game manually."
                } finally {
                    if (generation == searchGeneration) busy = false
                }
            }
    }
    val phoneGames by
        produceState(emptyList<PhoneGame>(), "mobile" in families) {
            value =
                if ("mobile" in families) withContext(Dispatchers.IO) { installedGames(context) }
                else emptyList()
        }
    val onPhone =
        remember(phoneGames, library, query, system) {
            if (system.isNotEmpty() && system != "Android") emptyList()
            else phoneGameMatches(phoneGames, library, query.trim())
        }
    fun choosePhoneGame(game: PhoneGame) {
        scope.launch {
            val cover = artwork.saveAppIcon(game.packageName).orEmpty()
            onChoose(
                CatalogResult(
                    Game(
                        title = game.title,
                        system = "Android",
                        source = "phone",
                        sourceId = game.packageName,
                        cover = cover,
                        coverProvider = if (cover.isEmpty()) "" else "This phone",
                    ),
                    listOfNotNull(cover.ifEmpty { null }?.let { CoverCandidate(it, "This phone", "App icon") }),
                )
            )
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AeroTextField(
            query,
            { query = it },
            label = "Game title",
            autoFocus = true,
            onSubmit = { if (query.isNotBlank()) search() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Choice(
            "System",
            system.ifBlank { "All systems" },
            listOf("All systems") + playableSystems(families),
        ) {
            system = if (it == "All systems") "" else it
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PinkButton(
                onClick = { search() },
                enabled = query.isNotBlank() && (!busy || activeSearch != (query.trim() to system)),
            ) {
                Text(if (busy && activeSearch == (query.trim() to system)) "Searching…" else "Search")
            }
            AeroButton(onClick = onManual) { Text("Add manually") }
            AeroButton(onClick = onHunt) { Text("Add a hunt") }
        }
        warning?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (onPhone.isNotEmpty()) {
                item(key = "phone") {
                    Text("On this phone", style = MaterialTheme.typography.labelLarge)
                }
                items(onPhone, key = { "phone:" + it.packageName }) { game ->
                    PhoneGameRow(game) { choosePhoneGame(game) }
                }
                if (results.isNotEmpty())
                    item(key = "results") {
                        Text(
                            "Search results",
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
            }
            items(results, key = { catalogTitleKey(it.game.title) + ":" + it.game.system }) { result ->
                CatalogResultRow(result, catalog, artwork) { onChoose(result) }
            }
        }
    }
}

@Composable
private fun PhoneGameRow(game: PhoneGame, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by
        produceState<ImageBitmap?>(null, game.packageName) {
            value = withContext(Dispatchers.IO) { appIconBitmap(context, game.packageName, 144)?.asImageBitmap() }
        }
    Glass(Modifier.fillMaxWidth().feedbackClickable(onClick = onClick)) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clearAndSetSemantics {}) {
                icon?.let { Image(it, null, Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(game.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "Android · on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CatalogResultRow(result: CatalogResult, catalog: Catalog, artwork: Artwork, onClick: () -> Unit) {
    val game = result.game
    val cover by
        produceState(result.covers.firstOrNull()?.url ?: "", game.source, game.sourceId) {
            if (value.isBlank()) value = runCatching { catalog.resultCover(game) }.getOrNull() ?: ""
        }
    Glass(Modifier.fillMaxWidth().feedbackClickable(onClick = onClick)) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.width(48.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Art(
                    cover,
                    artwork,
                    game.title,
                    showPlaceholderTitle = false,
                    system = game.system,
                    coverBounds = CoverSize(48f, 64f),
                    reserveHeight = false,
                    preview = true,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(game.title, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                            game.system,
                            game.release?.let {
                                java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year.toString()
                            },
                        )
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onEnable: (Settings) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    var timeError by remember { mutableStateOf<String?>(null) }
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
                    else timeError = "Choose an end time after the start time."
                },
                minute / 60,
                minute % 60,
                false,
            )
            .show()
    }
    val invitations = settings.reminders
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard("Appearance") {
            Choice(
                "Theme",
                settings.theme.replaceFirstChar { it.uppercase() },
                listOf("System", "Light", "Dark"),
            ) {
                onChange(settings.copy(theme = it.lowercase()))
            }
            Choice(
                "Shelf style",
                shelfStyle(settings.shelfStyle).label,
                ShelfStyle.entries.map { it.label },
            ) { label ->
                onChange(
                    settings.copy(shelfStyle = ShelfStyle.entries.first { it.label == label }.key)
                )
            }
            Choice(
                "Cover style",
                if (settings.gameCases) "Game cases" else "Flat art",
                listOf("Game cases", "Flat art"),
            ) {
                onChange(settings.copy(gameCases = it == "Game cases"))
            }
            Text(
                "App icon",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppIconPicker()
            Text(
                "With themed icons on, your home screen shows the monochrome handheld for every choice. Android may take Savepoint off your home screen while the icon changes; add it back from your apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsCard("What you play on") {
            Text(
                "Savepoint offers these systems when you add games and search the catalog.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlatformChoices(settings.platforms) { onChange(settings.copy(platforms = it)) }
            Text(
                "Searches look up LaunchBox, Steam and the App Store, and with Mobile on, Add a game also lists the games on this phone. Only your searches leave your phone; your shelves and notes stay here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsCard("Time for yourself") {
            Text(
                "A general invitation to enjoy your games, and an optional evening check-in for your play log. Each works on its own."
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rest invitations", Modifier.weight(1f))
                AeroSwitch(
                    settings.reminders,
                    { enabled ->
                        if (enabled) onEnable(settings.copy(reminders = true))
                        else onChange(settings.copy(reminders = false, laterAt = 0))
                    },
                )
            }
            // The days serve both notifications; the time window only shapes invitations.
            val scheduled = invitations || settings.eveningCheckIn
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val firstDay = WeekFields.of(LocalConfiguration.current.locales[0]).firstDayOfWeek
                Row(Modifier.fillMaxWidth().alpha(if (scheduled) 1f else .45f)) {
                    (0L until 7L).map { firstDay.plus(it) }.forEach { weekday ->
                        val number = weekday.value
                        val day = weekday.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        val checked = number in settings.days
                        Column(
                            Modifier.weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .feedbackToggleable(checked, enabled = scheduled, role = Role.Checkbox) {
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
                Glass(Modifier.fillMaxWidth().alpha(if (invitations) 1f else .45f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "From ${timeLabel(settings.startMinute)}",
                            Modifier.weight(1f)
                                .feedbackClickable(enabled = invitations, role = Role.Button) {
                                    pickTime(true)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        VerticalDivider(
                            Modifier.height(22.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = .3f),
                        )
                        Text(
                            "To ${timeLabel(settings.endMinute)}",
                            Modifier.weight(1f)
                                .feedbackClickable(enabled = invitations, role = Role.Button) {
                                    pickTime(false)
                                }
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
                        Text("Evening play log")
                        Text(
                            "Around 9pm on these days. Skipped or already logged days stay quiet.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    AeroSwitch(
                        settings.eveningCheckIn,
                        { enabled ->
                            if (enabled) onEnable(settings.copy(eveningCheckIn = true))
                            else onChange(settings.copy(eveningCheckIn = false))
                        },
                    )
                }
                Text(
                    "Delivery can vary with Android battery settings. Invitations arriving after your window are discarded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                AeroButton(
                    onClick = {
                        onChange(settings.copy(skipDate = LocalDate.now().toString(), laterAt = 0))
                    },
                    enabled = scheduled,
                ) {
                    Text("Skip today")
                }
            }
        }
        timeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SettingsCard("Backup") {
            Text(
                "Your library lives only on this phone. A backup file carries it, artwork included, to a new phone or a fresh install.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AeroActionRow(
                first = { AeroButton(onClick = onExport) { Text("Export backup") } },
                second = { AeroButton(onClick = onImport) { Text("Import backup") } },
            )
        }
        Text(
            "Savepoint · ${LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0).versionName}\nOriginal code © cmyksoda · GPLv3\nGame artwork belongs to its respective owners.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** Console families as chips; one always stays chosen so adding a game still offers systems. */
@Composable
fun PlatformChoices(platforms: Set<String>, onChange: (Set<String>) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        systemFamilies.forEach { family ->
            val chosen = family.key in platforms
            AeroChip(chosen, {
                if (!chosen) onChange(platforms + family.key)
                else if (platforms.size > 1) onChange(platforms - family.key)
            }) {
                Text(family.label)
            }
        }
    }
}

fun timeLabel(minute: Int): String =
    java.time.LocalTime.of((minute / 60) % 24, minute % 60)
        .format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
fun Savepoint(openLater: Boolean, openDailyLog: Boolean = false, openSettings: Boolean = false) {
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
    var encounterMode by rememberSaveable { mutableStateOf("Add encounters") }
    var encounterError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf<RestoredLibrary?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
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
                    val skipped = settings.skipDate == LocalDate.now().toString()
                    if (!settings.eveningCheckIn || skipped) LogReminders.clear(context)
                    if (!settings.reminders || skipped) Reminders.clear(context)
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
    var awaitingPermission by remember { mutableStateOf<Settings?>(null) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val wanted = awaitingPermission
            awaitingPermission = null
            if (granted && wanted != null) updateSettings(wanted)
            else if (!granted)
                error =
                    "Notifications are off for Savepoint. You can turn them on in Android settings whenever you want."
        }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        Backup.export(context, uri, games, settings, artwork)
                        notice = "Backup exported"
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
            artwork.forgetOldPreviews()
            library.load()
            if (updateStarterArtwork(library.games.value) != library.games.value)
                library.change(::updateStarterArtwork)
            artwork.prepareLibraryArtwork(library.games.value)
            settings = settingsStore.read()
            if (!settings.onboarded && library.games.value.isNotEmpty())
                settings = settingsStore.change { it.copy(onboarded = true) }
            preparedCalendar =
                withContext(Dispatchers.Default) {
                    playCalendarMonth(YearMonth.from(today), calendarLocale)
                }
            loaded = true
            Reminders.schedule(context, settings)
            if (openLater) dialog = "later"
            if (openSettings) route = "settings"
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
            LocalGameCases provides settings.gameCases,
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
                        AeroIconButton(AeroSymbol.BACK, "Back", onClick = { route = backRoute() })
                    else
                        Image(painterResource(R.drawable.icon_handheld), null, Modifier.size(48.dp))
                    Text(
                        when (route) {
                            "home" -> "Savepoint"
                            "find" -> "Search"
                            "hunt-add" -> "Add a hunt"
                            "shelf" -> editingShelf.label
                            "collection" -> "Collections"
                            "details" ->
                                if (selectedGame?.shelf == Shelf.HUNTS) "Your hunt" else "Your game"
                            "catalog",
                            "cover" -> "Cover art"
                            "form" ->
                                if (form?.shelf == Shelf.HUNTS) {
                                    if (selectedGame == null) "Add a hunt" else "Edit hunt"
                                } else if (selectedGame == null) "Add game" else "Edit game"
                            "log" -> "Play log"
                            "finished" -> "Finished games"
                            "daily" -> "Log play"
                            "journal" -> "Finished playthroughs"
                            "add" -> "Add a game"
                            "backlog" -> "Backlog"
                            "paused" -> "Paused"
                            "settings" -> "Settings"
                            "collections" -> "Collections"
                            else -> route.replaceFirstChar { it.uppercase() }
                        },
                        fontWeight = FontWeight.Medium,
                        fontSize = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (route == "home")
                        AeroIconButton(AeroSymbol.SETTINGS, "Settings", { route = "settings" })
                    if (route == "details" && selectedGame != null)
                        AeroIconButton(
                            AeroSymbol.CALENDAR,
                            "Log play",
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
                                "List view"
                            else "Grid view",
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
                        AeroIconButton(AeroSymbol.SEARCH, "Search your games", { route = "find" })
                }
                Box(Modifier.weight(1f)) {
                    if (!loaded) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else if (!settings.onboarded && games.isEmpty())
                        WelcomeScreen { chosen ->
                            updateSettings(settings.copy(platforms = chosen, onboarded = true))
                        }
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
                                                {
                                                    import.launch(
                                                        arrayOf(
                                                            "application/zip",
                                                            "application/octet-stream",
                                                        )
                                                    )
                                                },
                                                shelfStyle(settings.shelfStyle),
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
                                                artwork,
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
                                                        encounterMode = "Add encounters"
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
                                                    playingNow =
                                                        game.place == Place.ACTIVE &&
                                                            game.shelf == Shelf.PLAY_THROUGH &&
                                                            games
                                                                .filter {
                                                                    it.place == Place.ACTIVE &&
                                                                        it.shelf == Shelf.PLAY_THROUGH
                                                                }
                                                                .minByOrNull { it.position }
                                                                ?.id == game.id,
                                                )
                                            }
                                        "hunt-add" ->
                                            Box(
                                                Modifier.fillMaxSize().padding(horizontal = 20.dp)
                                            ) {
                                                PokemonPicker(catalog.pokemon, artwork) { pokemon ->
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
                                                artwork,
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
                                                families = settings.platforms,
                                                library = games,
                                            )
                                        "cover" ->
                                            selectedGame?.let { game ->
                                                CoverSelection(
                                                    game,
                                                    emptyList(),
                                                    artwork,
                                                    catalog,
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
                                                    playableSystems(
                                                        settings.platforms,
                                                        listOf(initial.system),
                                                    ),
                                                )
                                            }
                                        "settings" ->
                                            SettingsScreen(
                                                settings,
                                                ::updateSettings,
                                                { wanted ->
                                                    if (Build.VERSION.SDK_INT >= 33) {
                                                        awaitingPermission = wanted
                                                        notificationPermission.launch(
                                                            Manifest.permission.POST_NOTIFICATIONS
                                                        )
                                                    } else updateSettings(wanted)
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
                    val welcoming = loaded && !settings.onboarded && games.isEmpty()
                    // While you type, the keyboard needs the room more than the tabs do.
                    val typing = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    if (
                        !welcoming &&
                            !typing &&
                            (route in dockRoutes || (route == "form" && selectedGame == null))
                    )
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
                    // Good news shouldn't need a tap to go away, unlike an error.
                    Notice(notice, Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp)) {
                        notice = null
                    }
                }
            }
            if (dialog == "encounters" && selectedGame != null) {
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("Encounters") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Choice("Change", encounterMode, listOf("Add encounters", "Set total")) {
                                encounterMode = it
                                encounterValue =
                                    if (it == "Set total") selectedGame.encounters.toString()
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
                                    if (encounterMode == "Set total") "Total encounters"
                                    else "How many to add?",
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
                                        (encounterMode == "Add encounters" && amount == 0L)
                                )
                                    encounterError =
                                        "Enter a ${if (encounterMode == "set total") "nonnegative" else "positive"} whole number"
                                else {
                                    val add = encounterMode == "Add encounters"
                                    changeEncounters(selectedGame.id) {
                                        if (add) increasedEncounterCount(it, amount) else amount
                                    }
                                    dialog = ""
                                }
                            }
                        ) {
                            Text(
                                if (encounterMode == "Set total") "Save total" else "Add encounters"
                            )
                        }
                    },
                    dismissButton = { AeroButton(onClick = { dialog = "" }) { Text("Cancel") } },
                )
            }
            if (dialog == "delete" && selectedGame != null) {
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("Delete ${selectedGame.title}?") },
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
                            Text("Delete game", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { AeroButton(onClick = { dialog = "" }) { Text("Cancel") } },
                )
            }
            if (dialog == "destination" && form != null) {
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("Where should it go?") },
                    text = {
                        val now = games.firstOrNull { it.id == form?.id }
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
                                    val here = now?.place == Place.ACTIVE && now.shelf == destination
                                    AeroIcon(AeroSymbol.SHELVES, Modifier.size(20.dp))
                                    Text(
                                        if (here) "${destination.label} · here now" else destination.label,
                                        Modifier.weight(1f).padding(start = 12.dp),
                                    )
                                    AeroIcon(
                                        if (here) AeroSymbol.CHECK else AeroSymbol.NEXT,
                                        Modifier.size(if (here) 18.dp else 14.dp),
                                    )
                                }
                            }
                            HorizontalDivider(
                                Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = .3f),
                            )
                            listOf(Place.BACKLOG to "Backlog", Place.FINISHED to "Finished games")
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
                                        val here = now?.place == place
                                        Text(
                                            if (here) "$label · here now" else label,
                                            Modifier.weight(1f).padding(start = 12.dp),
                                        )
                                        AeroIcon(
                                            if (here) AeroSymbol.CHECK else AeroSymbol.NEXT,
                                            Modifier.size(if (here) 18.dp else 14.dp),
                                        )
                                    }
                                }
                        }
                    },
                    confirmButton = { AeroButton(onClick = { dialog = "" }) { Text("Cancel") } },
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
                        Text(if (dialog == "notes") "Notes" else "Pause ${selectedGame?.title}?")
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
                                    if (dialog == "notes") "General notes"
                                    else "Pause note (optional)",
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
                            Text(if (dialog == "notes") "Save" else "Save & pause")
                        }
                    },
                    dismissButton = { QuietAction(onClick = { dialog = "" }) { Text("Cancel") } },
                )
            if (dialog == "later")
                AeroDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("Another time today?") },
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
                            Text("Choose time")
                        }
                    },
                    dismissButton = { QuietAction(onClick = { dialog = "" }) { Text("Cancel") } },
                )
            if (dialog == "restore" && restored != null)
                AeroDialog(
                    onDismissRequest = {
                        restored?.artwork?.deleteRecursively()
                        restored = null
                        dialog = ""
                    },
                    title = { Text("Replace this library?") },
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
                                        LogReminders.clear(context)
                                        route = "home"
                                        dialog = ""
                                        notice = "Backup restored"
                                    } catch (e: Exception) {
                                        error = "Restore failed: ${e.message}"
                                    } finally {
                                        backup.artwork.deleteRecursively()
                                        restored = null
                                    }
                                }
                            }
                        ) {
                            Text("Replace & import")
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
                            Text("Cancel")
                        }
                    },
                )
            error?.let { message ->
                AeroDialog(
                    onDismissRequest = { error = null },
                    text = { Text(message) },
                    confirmButton = { AeroButton(onClick = { error = null }) { Text("Okay") } },
                )
            }
        }
    }
}

@Composable
private fun Notice(message: String?, modifier: Modifier, onGone: () -> Unit) {
    var shown by remember { mutableStateOf("") }
    if (message != null) shown = message
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2600)
            onGone()
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        message != null,
        modifier,
        enter =
            androidx.compose.animation.fadeIn() +
                androidx.compose.animation.slideInVertically { it / 2 },
        exit = androidx.compose.animation.fadeOut(),
    ) {
        Glass(strong = true) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AeroIcon(AeroSymbol.CHECK, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                Text(shown, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
