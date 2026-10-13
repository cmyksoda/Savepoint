package com.cmyksoda.savepoint

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
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

private fun feelingSymbol(feeling: Feeling): AeroSymbol =
    when (feeling) {
        Feeling.DIDNT_LIKE -> AeroSymbol.THUMB_DOWN
        Feeling.LIKED -> AeroSymbol.THUMB_UP
        Feeling.LOVED -> AeroSymbol.HEART
    }

@Composable
fun FeelingBadge(feeling: Feeling) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AeroIcon(feelingSymbol(feeling), Modifier.size(17.dp), MaterialTheme.colorScheme.primary)
        Text(
            feeling.label,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun PlaythroughContent(entry: Playthrough?) {
    entry?.feeling?.let { FeelingBadge(it) }
    if (!entry?.thoughts.isNullOrBlank()) Text(entry.thoughts)
}

@Composable
fun LogMetric(symbol: AeroSymbol, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AeroIcon(symbol, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LogExtras(game: Game) {
    if (game.recordedHours != null || game.replayCount != null)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            game.recordedHours?.let {
                LogMetric(AeroSymbol.CLOCK, "${formatHours(it)} hours recorded")
            }
            game.replayCount?.let {
                LogMetric(AeroSymbol.REPLAY, "$it ${if (it == 1) "replay" else "replays"}")
            }
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
        AeroTextField(
            query,
            { query = it },
            label = "find a game",
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
        GameIdentity(game, artwork)
        Glass(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().padding(end = 42.dp)) {
                        latest?.feeling?.let { FeelingBadge(it) }
                            ?: Text(
                                "thoughts",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }
                    if (!latest?.thoughts.isNullOrBlank()) Text(latest.thoughts)
                    LogExtras(game)
                }
                AeroIconButton(
                    AeroSymbol.EDIT,
                    "edit thoughts and extras",
                    { onEdit(latest ?: Playthrough(finishedAt = null)) },
                    Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 8.dp),
                    compact = true,
                )
            }
        }
        if (game.playthroughs.size > 1) {
            TextButton(
                onClick = { showHistory = !showHistory },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(if (showHistory) "hide earlier playthroughs" else "earlier playthroughs")
            }
            if (showHistory)
                game.playthroughs.dropLast(1).asReversed().forEach { entry ->
                    Glass(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(
                                start = 18.dp,
                                end = 12.dp,
                                top = 6.dp,
                                bottom = 18.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                AeroIconButton(
                                    AeroSymbol.EDIT,
                                    "edit earlier playthrough",
                                    { onEdit(entry) },
                                    compact = true,
                                )
                            }
                            PlaythroughContent(entry)
                            entry.hours?.let {
                                LogMetric(AeroSymbol.CLOCK, "${formatHours(it)} hours recorded")
                            }
                        }
                    }
                }
        }
        if (game.place == Place.ACTIVE) {
            AeroButton(onClick = onCurrentGame, modifier = Modifier.fillMaxWidth()) {
                Text("open current game")
            }
        } else {
            PinkButton(onClick = { onReplay(true) }, modifier = Modifier.fillMaxWidth()) {
                AeroIcon(AeroSymbol.REPLAY, Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text("replay now")
            }
            AeroButton(onClick = { onReplay(false) }, modifier = Modifier.fillMaxWidth()) {
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
    AeroDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "finished ${game.title}?" else "thoughts & details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isNew)
                    Text("Leave a thought if you'd like. Your earlier playthroughs stay saved.")
                Text("how did it feel?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feeling.entries.forEach { option ->
                        val selected = feeling == option
                        FilterChip(
                            selected = selected,
                            onClick = { feeling = if (selected) null else option },
                            leadingIcon = { AeroIcon(feelingSymbol(option), Modifier.size(18.dp)) },
                            label = { Text(option.label, fontSize = 13.sp, lineHeight = 18.sp) },
                            shape = RoundedCornerShape(10.dp),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (selected)
                                        MaterialTheme.colorScheme.primary.copy(alpha = .7f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = .8f),
                                ),
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surface.copy(alpha = .7f),
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContainerColor =
                                        if (LocalAeroDark.current) Color(0xFF403149)
                                        else Color(0xFFF0C9DF),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                    iconColor = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                    }
                }
                AeroTextField(
                    thoughts,
                    { thoughts = it },
                    label = "thoughts (optional)",
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
                        AeroIcon(AeroSymbol.EDIT, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("optional details")
                    }
                if (showExtras) {
                    AeroTextField(
                        hours,
                        { hours = it },
                        label = "hours for this playthrough (optional)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AeroTextField(
                        replays,
                        { replays = it },
                        label = "replays overall (optional)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            PinkButton(
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
        dismissButton = { QuietAction(onClick = onDismiss) { Text("cancel") } },
    )
}
