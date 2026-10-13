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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < 0.3f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (dark) Color(0xFF9C6688).copy(alpha = .5f) else Color.White),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier =
                Modifier.background(
                    Brush.verticalGradient(
                        if (dark) listOf(Color(0xEF68405C), Color(0xDB3D253B))
                        else listOf(Color(0xF5FFFFFF), Color(0xCFFFE5F1))
                    )
                ),
            content = content,
        )
    }
}

@Composable
fun PinkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.red < 0.3f
    val shape = RoundedCornerShape(18.dp)
    val gradient =
        if (dark) listOf(Color(0xFFFFD6E9), Color(0xFFEE94BD))
        else listOf(Color(0xFFBB638B), Color(0xFF94315E))
    Button(
        onClick = onClick,
        modifier = modifier.background(Brush.verticalGradient(gradient), shape),
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .45f)),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = if (dark) Color(0xFF47132C) else Color.White,
                disabledContainerColor = Color(0x99775168),
            ),
        content = content,
    )
}

@Composable
fun Choice(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value, modifier = Modifier.weight(1f), maxLines = 2)
                Text("⌄")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                options.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            expanded = false
                            onSelect(item)
                        },
                    )
                }
            }
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
) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) { bitmap = runCatching { artwork.bitmap(url) }.getOrNull() }
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFFDD9BBE), Color(0xFF774E83)))),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null)
            Image(
                bitmap!!.asImageBitmap(),
                title,
                Modifier.fillMaxSize().then(if (sprite) Modifier.padding(12.dp) else Modifier),
                contentScale = if (sprite) ContentScale.Fit else ContentScale.Crop,
                filterQuality =
                    if (sprite) androidx.compose.ui.graphics.FilterQuality.None
                    else androidx.compose.ui.graphics.FilterQuality.Medium,
            )
        else
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.icon_memory_card), null, Modifier.size(48.dp))
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

@Composable
fun GameCover(game: Game, artwork: Artwork, focus: Boolean, onOpen: () -> Unit) {
    val hunt = game.shelf == Shelf.HUNTS
    val width = if (focus) 166.dp else if (hunt) 142.dp else 116.dp
    val height = if (focus) 218.dp else if (hunt) 154.dp else 168.dp
    Box(
        Modifier.width(width)
            .height(height)
            .clip(RoundedCornerShape(17.dp))
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "${game.title}${if (focus) ", playing now" else ""}" }
    ) {
        Art(
            if (hunt) game.sprite else game.cover,
            artwork,
            if (hunt) game.pokemon.ifBlank { game.title } else game.title,
            Modifier.fillMaxSize(),
            hunt,
        )
        if (focus)
            Surface(
                Modifier.align(Alignment.TopStart).padding(8.dp),
                color = Color(0xCC31152E),
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
                color = Color(0xCC31152E),
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
                            fontSize = 25.sp,
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
                                fontSize = 23.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { onEditShelf(shelf) },
                                modifier =
                                    Modifier.semantics {
                                        contentDescription = "edit ${shelf.label} shelf"
                                    },
                            ) {
                                Text("›", fontSize = 28.sp)
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            items(row, key = { it.id }) { game ->
                                GameCover(
                                    game,
                                    artwork,
                                    shelf == Shelf.PLAY_THROUGH && row.first().id == game.id,
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
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("find a game") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
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
                Glass(Modifier.fillMaxWidth().clickable { onOpen(game.id) }) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Art(
                            if (game.shelf == Shelf.HUNTS) game.sprite else game.cover,
                            artwork,
                            game.title,
                            Modifier.size(54.dp, 72.dp).clip(RoundedCornerShape(8.dp)),
                            game.shelf == Shelf.HUNTS,
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
                        Text("›")
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
        items(row, key = { it.id }) { game ->
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    TextButton(onClick = { onOpen(game.id) }) {
                        Text(game.title, fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
        item { PinkButton(onClick = onAdd) { Text("add from backlog") } }
    }
}

@Composable
fun GameDetails(
    game: Game,
    artwork: Artwork,
    onNotes: () -> Unit,
    onPause: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Place, Boolean) -> Unit,
    onEncounter: (Long) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Art(
            if (game.shelf == Shelf.HUNTS) game.sprite else game.cover,
            artwork,
            if (game.shelf == Shelf.HUNTS) game.pokemon else game.title,
            Modifier.align(Alignment.CenterHorizontally)
                .size(184.dp, 232.dp)
                .clip(RoundedCornerShape(22.dp)),
            game.shelf == Shelf.HUNTS,
        )
        Text(
            if (game.shelf == Shelf.HUNTS) game.pokemon.ifBlank { "shiny hunt" } else game.title,
            fontSize = 28.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (game.shelf == Shelf.HUNTS) "${game.title} · ${game.system}"
            else "${game.system} · ${game.shelf.label}"
        )
        if (game.pauseNote.isNotBlank())
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("for when you return", fontWeight = FontWeight.SemiBold)
                    Text(game.pauseNote)
                }
            }
        OutlinedButton(onClick = onNotes, modifier = Modifier.fillMaxWidth()) { Text("notes") }
        if (game.shelf == Shelf.HUNTS) {
            val odds = calculatedOdds(game)
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${game.encounters} encounters", fontSize = 28.sp)
                    Text(game.method)
                    Text(odds.first?.let { "1 in $it · ${odds.second}" } ?: odds.second)
                    Text(huntVersions[game.pokemonVersion] ?: game.pokemonVersion)
                    if (game.shinyCharm) Text("shiny charm enabled")
                    PinkButton(
                        onClick = { if (game.encounters < Long.MAX_VALUE) onEncounter(1) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    ) {
                        Text("+1 encounter", fontSize = 20.sp)
                    }
                    TextButton(onClick = onUndo, enabled = canUndo) { Text("undo last count") }
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
            OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                Text("take a break from this game")
            }
            TextButton(onClick = { onMove(Place.FINISHED, false) }) {
                Text(if (game.shelf == Shelf.HUNTS) "mark hunt complete" else "mark finished")
            }
        } else {
            if (game.shelf == Shelf.PLAY_THROUGH)
                PinkButton(
                    onClick = { onMove(Place.ACTIVE, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("play now")
                }
            OutlinedButton(
                onClick = { onMove(Place.ACTIVE, false) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (game.shelf == Shelf.PLAY_THROUGH) "add to queue"
                    else "return to ${game.shelf.label}"
                )
            }
        }
        TextButton(onClick = onEdit) { Text("edit game & artwork") }
        if (game.source == "gametdb")
            Text("Title and cover source: GameTDB", style = MaterialTheme.typography.bodySmall)
        if (game.source == "igdb")
            Text("Game data provided by IGDB", style = MaterialTheme.typography.bodySmall)
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
    onSave: (Game) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var system by rememberSaveable(initial.id) { mutableStateOf(initial.system) }
    var shelf by rememberSaveable(initial.id) { mutableStateOf(initial.shelf) }
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
    var pokemon by rememberSaveable(initial.id) { mutableStateOf(initial.pokemon) }
    var version by rememberSaveable(initial.id) { mutableStateOf(initial.pokemonVersion) }
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
                        if (selectingSprite) sprite = selected else cover = selected
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
        }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        OutlinedTextField(
            title,
            { title = it },
            label = { Text(if (shelf == Shelf.HUNTS) "game title" else "title") },
            modifier = Modifier.fillMaxWidth(),
        )
        Choice("system", system, systems) { system = it }
        Choice("shelf", shelf.label, Shelf.entries.map { it.label }) { label ->
            shelf = Shelf.entries.first { it.label == label }
        }
        OutlinedTextField(
            collection,
            { collection = it },
            label = { Text("collection (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (collection.isNotBlank())
            OutlinedTextField(
                collectionOrder,
                { collectionOrder = it },
                label = { Text("position in collection") },
                modifier = Modifier.fillMaxWidth(),
            )
        OutlinedTextField(
            release,
            { release = it },
            label = { Text("release date · YYYY-MM-DD (optional)") },
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
            Art(cover, artwork, title, Modifier.size(90.dp, 120.dp).clip(RoundedCornerShape(12.dp)))
        if (shelf == Shelf.HUNTS) {
            OutlinedTextField(
                pokemon,
                {
                    pokemon = it
                    sprite = ""
                },
                label = { Text("Pokémon name or form slug") },
                modifier = Modifier.fillMaxWidth(),
            )
            Choice(
                "game artwork version",
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
            OutlinedTextField(
                method,
                {
                    method = it
                    odds = ""
                },
                label = { Text("hunt method") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                count,
                { count = it },
                label = { Text("encounters") },
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
            OutlinedTextField(
                odds,
                { odds = it },
                label = { Text("verified odds · 1 in… (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Verified standard odds are shown for supported games. Other methods need your verified value. Changing method or charm clears the old value.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PinkButton(
            onClick = {
                try {
                    require(title.isNotBlank()) { "Give this game a title" }
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
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("game title") },
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
                    fontSize = 23.sp,
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
                listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").forEachIndexed { index, day
                    ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                val number = index + 1
                                onChange(
                                    settings.copy(
                                        days =
                                            if (number in settings.days) settings.days - number
                                            else settings.days + number
                                    )
                                )
                            },
                    ) {
                        Checkbox(index + 1 in settings.days, null)
                        Text(day)
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
                    fontSize = 23.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "GameTDB works without an account. Your search service adds broader Nintendo and PC results."
                )
                OutlinedTextField(
                    endpoint,
                    { endpoint = it },
                    label = { Text("catalog service HTTPS address") },
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
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Text("export backup")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("import backup")
        }
        Text(
            "savepoint · 0.1.0\nOriginal code © cmyksoda · GPLv3\nGame artwork belongs to its respective owners.",
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
    var dialog by remember { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf<RestoredLibrary?>(null) }
    var undoCount by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val selectedGame = games.find { it.id == selected }

    fun mutate(action: (List<Game>) -> List<Game>) {
        scope.launch {
            try {
                library.change(action)
            } catch (e: Exception) {
                error = e.message ?: "Could not save this change"
            }
        }
    }
    fun updateSettings(value: Settings) {
        val previous = settings
        scope.launch {
            try {
                settings = settingsStore.change { current ->
                    mergeSettings(current, previous, value)
                }
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
            library.load()
            settings = settingsStore.read()
            loaded = true
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
    }
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFFFFAFD0),
                background = Color(0xFF241324),
                surface = Color(0xFF3C2237),
                onSurface = Color(0xFFFFEAF4),
                onBackground = Color(0xFFFFEAF4),
                onPrimary = Color(0xFF47132C),
            )
        else
            lightColorScheme(
                primary = Color(0xFF9A315F),
                background = Color(0xFFF9DFEC),
                surface = Color(0xFFFFF4F9),
                onSurface = Color(0xFF3E2335),
                onBackground = Color(0xFF3E2335),
            )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            BackHandler(route != "home" || dialog.isNotBlank()) {
                if (dialog.isNotBlank()) dialog = ""
                else if (route == "form" && selectedGame != null) route = "details"
                else route = "home"
            }
            Column(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            if (dark)
                                listOf(Color(0xFF43203B), Color(0xFF211322), Color(0xFF342039))
                            else listOf(Color(0xFFFFEDF5), Color(0xFFF4D3E4), Color(0xFFEACFE9))
                        )
                    )
                    .safeDrawingPadding()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (route != "home")
                        TextButton(
                            onClick = {
                                route =
                                    if (route == "form" && selectedGame != null) "details"
                                    else "home"
                            }
                        ) {
                            Text("‹ back")
                        }
                    Text(
                        when (route) {
                            "home" -> "savepoint"
                            "shelf" -> editingShelf.label
                            "details" -> "your game"
                            "form" -> "edit game"
                            else -> route
                        },
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (route == "home")
                        TextButton(onClick = { route = "settings" }) { Text("settings") }
                }
                Box(Modifier.weight(1f)) {
                    if (!loaded) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else
                        when (route) {
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
                                    {
                                        selected = ""
                                        route = "add"
                                    },
                                    {
                                        mutate { old ->
                                            old +
                                                listOf(
                                                    Game(
                                                        title = "Rhythm Heaven Groove",
                                                        system = "Nintendo Switch",
                                                        place = Place.ACTIVE,
                                                        cover =
                                                            "https://art.gametdb.com/switch/cover/US/BFLTA.jpg",
                                                        source = "gametdb",
                                                        sourceId = "BFLTA",
                                                    ),
                                                    Game(
                                                        title = "DELTARUNE",
                                                        system = "Nintendo Switch",
                                                        place = Place.ACTIVE,
                                                        position = 1,
                                                        cover =
                                                            "https://art.gametdb.com/switch/cover/US/BN5WA.jpg",
                                                        source = "gametdb",
                                                        sourceId = "BN5WA",
                                                    ),
                                                    Game(
                                                        title = "Metroid: Zero Mission",
                                                        system = "Game Boy Advance",
                                                        place = Place.ACTIVE,
                                                        position = 2,
                                                        collection = "2d metroid",
                                                        collectionOrder = 1,
                                                    ),
                                                    Game(
                                                        title = "AM2R",
                                                        system = "PC",
                                                        place = Place.ACTIVE,
                                                        position = 3,
                                                        collection = "2d metroid",
                                                        collectionOrder = 2,
                                                    ),
                                                    Game(
                                                        title = "Pokémon Pokopia",
                                                        system = "Nintendo Switch 2",
                                                        shelf = Shelf.EASY,
                                                        place = Place.ACTIVE,
                                                    ),
                                                )
                                        }
                                    },
                                )
                            "backlog",
                            "paused",
                            "finished" ->
                                LibraryList(
                                    games,
                                    when (route) {
                                        "paused" -> Place.PAUSED
                                        "finished" -> Place.FINISHED
                                        else -> Place.BACKLOG
                                    },
                                    settings,
                                    artwork,
                                    ::updateSettings,
                                    {
                                        selected = it
                                        route = "details"
                                    },
                                )
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
                                    { id -> mutate { LibraryRules.move(it, id, Place.BACKLOG) } },
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
                                            note = game.pauseNote
                                            dialog = "pause"
                                        },
                                        {
                                            form = game
                                            route = "form"
                                        },
                                        { place, first ->
                                            mutate { LibraryRules.move(it, game.id, place, first) }
                                            route = "home"
                                        },
                                        { delta ->
                                            scope.launch {
                                                try {
                                                    var before = 0L
                                                    library.change { list ->
                                                        list.map {
                                                            if (it.id == game.id) {
                                                                before = it.encounters
                                                                it.copy(
                                                                    encounters =
                                                                        if (
                                                                            it.encounters ==
                                                                                Long.MAX_VALUE
                                                                        )
                                                                            it.encounters
                                                                        else it.encounters + delta
                                                                )
                                                            } else it
                                                        }
                                                    }
                                                    undoCount = game.id to before
                                                } catch (e: Exception) {
                                                    error = e.message
                                                }
                                            }
                                        },
                                        {
                                            undoCount?.let { (id, count) ->
                                                mutate { list ->
                                                    list.map {
                                                        if (it.id == id) it.copy(encounters = count)
                                                        else it
                                                    }
                                                }
                                                undoCount = null
                                            }
                                        },
                                        undoCount?.first == game.id,
                                    )
                                }
                            "add" ->
                                AddScreen(
                                    catalog,
                                    settings.catalogUrl,
                                    {
                                        form = it
                                        selected = ""
                                        route = "form"
                                    },
                                    {
                                        form = Game(title = "")
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
                                        { game ->
                                            mutate { list ->
                                                if (list.any { it.id == game.id })
                                                    list.map {
                                                        if (it.id == game.id)
                                                            game.copy(
                                                                position =
                                                                    if (game.shelf != initial.shelf)
                                                                        (list
                                                                            .filter { peer ->
                                                                                peer.shelf ==
                                                                                    game.shelf &&
                                                                                    peer.place ==
                                                                                        game.place
                                                                            }
                                                                            .maxOfOrNull { peer ->
                                                                                peer.position
                                                                            } ?: -1) + 1
                                                                    else game.position
                                                            )
                                                        else it
                                                    }
                                                else
                                                    list +
                                                        game.copy(
                                                            added = System.currentTimeMillis(),
                                                            position =
                                                                (list
                                                                    .filter {
                                                                        it.shelf == game.shelf &&
                                                                            it.place == game.place
                                                                    }
                                                                    .maxOfOrNull { it.position }
                                                                    ?: -1) + 1,
                                                        )
                                            }
                                            selected = game.id
                                            route = "details"
                                        },
                                        { route = if (selectedGame != null) "details" else "add" },
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
                                            arrayOf("application/zip", "application/octet-stream")
                                        )
                                    },
                                )
                        }
                }
                if (
                    route == "home" ||
                        route == "backlog" ||
                        route == "paused" ||
                        route == "finished"
                )
                    Glass(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { route = "home" }) { Text("shelves") }
                            TextButton(onClick = { route = "backlog" }) { Text("backlog") }
                            TextButton(onClick = { route = "paused" }) { Text("paused") }
                            if (route == "backlog" || route == "finished")
                                TextButton(
                                    onClick = {
                                        route = if (route == "finished") "backlog" else "finished"
                                    }
                                ) {
                                    Text(if (route == "finished") "backlog" else "finished")
                                }
                            else
                                TextButton(
                                    onClick = {
                                        selected = ""
                                        route = "add"
                                    }
                                ) {
                                    Text("+ add")
                                }
                        }
                    }
            }
            if (dialog == "notes" || dialog == "pause")
                AlertDialog(
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
                            OutlinedTextField(
                                note,
                                { note = it },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth(),
                                label = {
                                    Text(
                                        if (dialog == "notes") "general notes"
                                        else "pause note (optional)"
                                    )
                                },
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
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
                    dismissButton = { TextButton(onClick = { dialog = "" }) { Text("cancel") } },
                )
            if (dialog == "later")
                AlertDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("another time today?") },
                    text = {
                        Text(
                            "Choose when you'd like one follow-up invitation. There won't be another unless you ask."
                        )
                    },
                    confirmButton = {
                        TextButton(
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
                    dismissButton = { TextButton(onClick = { dialog = "" }) { Text("cancel") } },
                )
            if (dialog == "restore" && restored != null)
                AlertDialog(
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
                        TextButton(
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
                                            library.change { backup.games }
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
                        TextButton(
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
                AlertDialog(
                    onDismissRequest = { error = null },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = { error = null }) { Text("okay") } },
                )
            }
        }
    }
}
