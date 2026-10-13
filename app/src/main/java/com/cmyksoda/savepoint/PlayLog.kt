package com.cmyksoda.savepoint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatHours(hours: Double): String =
    NumberFormat.getNumberInstance()
        .apply {
            maximumFractionDigits = 2
        }
        .format(hours)

@Composable
fun PlaythroughContent(entry: Playthrough?) {
    entry?.feeling?.let { feeling ->
        Text(
            if (feeling == Feeling.LOVED) "♥ ${feeling.label}" else feeling.label,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
    if (!entry?.thoughts.isNullOrBlank()) Text(entry!!.thoughts)
}

@Composable
fun LogExtras(game: Game) {
    game.recordedHours?.let {
        Text("${formatHours(it)} hours recorded", style = MaterialTheme.typography.bodySmall)
    }
    game.replayCount?.let {
        Text(
            "$it ${if (it == 1) "replay" else "replays"}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun PlayLogList(games: List<Game>, artwork: Artwork, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("recently finished") }
    val filtered = games.filter { it.inPlayLog && it.title.contains(query.trim(), true) }
    val ordered =
        when (sort) {
            "title · A–Z" -> filtered.sortedBy { it.title.lowercase() }
            "title · Z–A" -> filtered.sortedByDescending { it.title.lowercase() }
            else ->
                filtered.sortedWith(
                    compareBy<Game> { it.playthroughs.lastOrNull()?.finishedAt == null }
                        .thenByDescending { it.playthroughs.lastOrNull()?.finishedAt }
                        .thenBy { it.title.lowercase() }
                )
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("find a game") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Choice("sort", sort, listOf("recently finished", "title · A–Z", "title · Z–A")) {
            sort = it
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (ordered.isEmpty())
                item {
                    Text(
                        "Games you finish will stay here, along with any thoughts you leave.",
                        Modifier.padding(vertical = 16.dp),
                    )
                }
            items(ordered, key = { it.id }) { game ->
                Glass(Modifier.fillMaxWidth().clickable { onOpen(game.id) }) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Art(
                            if (game.shelf == Shelf.HUNTS) game.sprite else game.cover,
                            artwork,
                            game.title,
                            Modifier.size(70.dp, 94.dp).clip(RoundedCornerShape(12.dp)),
                            game.shelf == Shelf.HUNTS,
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(game.title, fontWeight = FontWeight.SemiBold)
                            PlaythroughContent(game.playthroughs.lastOrNull())
                            LogExtras(game)
                        }
                        Text("›")
                    }
                }
            }
        }
    }
}

@Composable
fun GameJournal(
    game: Game,
    artwork: Artwork,
    onEdit: (Playthrough) -> Unit,
    onReplay: (Boolean) -> Unit,
    onCurrentGame: () -> Unit,
) {
    var showHistory by rememberSaveable(game.id) { mutableStateOf(false) }
    val latest = game.playthroughs.lastOrNull()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(
                if (game.shelf == Shelf.HUNTS) game.sprite else game.cover,
                artwork,
                game.title,
                Modifier.size(108.dp, 146.dp).clip(RoundedCornerShape(16.dp)),
                game.shelf == Shelf.HUNTS,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(game.title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text(game.system, style = MaterialTheme.typography.bodySmall)
            }
        }
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { onEdit(latest ?: Playthrough(finishedAt = null)) },
                        modifier =
                            Modifier.semantics { contentDescription = "edit thoughts and extras" },
                    ) {
                        Text("✎", fontSize = 22.sp)
                    }
                }
                PlaythroughContent(latest)
                LogExtras(game)
            }
        }
        if (game.playthroughs.size > 1) {
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "hide earlier playthroughs" else "earlier playthroughs")
            }
            if (showHistory)
                game.playthroughs.dropLast(1).asReversed().forEach { entry ->
                    Glass(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                entry.finishedAt?.let { timestamp ->
                                    Text(
                                        Instant.ofEpochMilli(timestamp)
                                            .atZone(ZoneId.systemDefault())
                                            .format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } ?: Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = { onEdit(entry) },
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription = "edit earlier playthrough"
                                        },
                                ) {
                                    Text("✎", fontSize = 22.sp)
                                }
                            }
                            PlaythroughContent(entry)
                            entry.hours?.let {
                                Text(
                                    "${formatHours(it)} hours recorded",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
        }
        if (game.place == Place.ACTIVE) {
            OutlinedButton(onClick = onCurrentGame, modifier = Modifier.fillMaxWidth()) {
                Text("open current game")
            }
        } else {
            PinkButton(onClick = { onReplay(true) }, modifier = Modifier.fillMaxWidth()) {
                Text("replay now")
            }
            OutlinedButton(onClick = { onReplay(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (game.shelf == Shelf.PLAY_THROUGH) "queue a replay"
                    else "return to ${game.shelf.label}"
                )
            }
        }
    }
}

@Composable
fun PlaythroughEditor(
    game: Game,
    initial: Playthrough,
    isNew: Boolean,
    onSave: (Playthrough, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var thoughts by rememberSaveable(initial.id) { mutableStateOf(initial.thoughts) }
    var feeling by rememberSaveable(initial.id) { mutableStateOf(initial.feeling) }
    var hours by rememberSaveable(initial.id) { mutableStateOf(initial.hours?.toString() ?: "") }
    var replays by
        rememberSaveable(initial.id) {
            mutableStateOf(
                game.replayCount?.let { count ->
                    if (isNew && game.inPlayLog && count < Int.MAX_VALUE) (count + 1).toString()
                    else count.toString()
                } ?: ""
            )
        }
    var showExtras by
        rememberSaveable(initial.id) { mutableStateOf(!isNew || initial.hours != null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "finished ${game.title}?" else "thoughts & details") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isNew)
                    Text("Leave a thought if you'd like. Your earlier playthroughs stay saved.")
                Text("how did it feel?", style = MaterialTheme.typography.labelLarge)
                Feeling.entries.forEach { option ->
                    FilterChip(
                        selected = feeling == option,
                        onClick = { feeling = if (feeling == option) null else option },
                        label = { Text(option.label) },
                    )
                }
                OutlinedTextField(
                    thoughts,
                    { thoughts = it },
                    label = { Text("thoughts (optional)") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!showExtras)
                    TextButton(
                        onClick = { showExtras = true },
                        modifier =
                            Modifier.semantics { contentDescription = "add optional details" },
                    ) {
                        Text("✎ optional details")
                    }
                if (showExtras) {
                    OutlinedTextField(
                        hours,
                        { hours = it },
                        label = { Text("hours for this playthrough (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        replays,
                        { replays = it },
                        label = { Text("replays overall (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val parsedHours =
                            if (hours.isBlank()) null
                            else hours.trim().toDoubleOrNull() ?: error("Enter hours as a number")
                        require(parsedHours == null || parsedHours.isFinite() && parsedHours >= 0) {
                            "Hours cannot be negative or infinite"
                        }
                        val count =
                            if (replays.isBlank()) null
                            else replays.trim().toIntOrNull() ?: error("Enter a whole replay count")
                        require(count == null || count >= 0) { "Replays cannot be negative" }
                        onSave(
                            initial.copy(
                                feeling = feeling,
                                thoughts = thoughts.trim(),
                                hours = parsedHours,
                            ),
                            count,
                        )
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    } catch (e: IllegalStateException) {
                        error = e.message
                    }
                }
            ) {
                Text(if (isNew) "finish & save" else "save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("cancel") } },
    )
}
