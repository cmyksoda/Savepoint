package com.cmyksoda.savepoint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun finishedDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

fun formatHours(hours: Double): String =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(hours)

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
                val across = game.playthroughs.count { it.hours != null } > 1
                LogMetric(
                    AeroSymbol.CLOCK,
                    "${formatHours(it)} hours ${if (across) "in all" else "recorded"}",
                )
            }
            game.replayCount?.let {
                LogMetric(AeroSymbol.REPLAY, "$it ${if (it == 1) "replay" else "replays"}")
            }
        }
}

@Composable
fun PlayLogList(games: List<Game>, artwork: Artwork, grid: Boolean, onOpen: (String) -> Unit) {
    var sort by rememberSaveable { mutableStateOf("Recently finished") }
    val filtered = games.filter { it.inPlayLog }
    val ordered =
        when (sort) {
            "Title · A–Z" -> filtered.sortedBy { it.title.lowercase() }
            "Title · Z–A" -> filtered.sortedByDescending { it.title.lowercase() }
            else ->
                filtered.sortedWith(
                    compareBy<Game> { it.playthroughs.lastOrNull()?.finishedAt == null }
                        .thenByDescending { it.playthroughs.lastOrNull()?.finishedAt }
                        .thenBy { it.title.lowercase() }
                )
        }
    LibraryBrowser(
        ordered,
        grid,
        "Games you finish will stay here, along with any thoughts you leave.",
        controls = {
            Choice("Sort", sort, listOf("Recently finished", "Title · A–Z", "Title · Z–A")) {
                sort = it
            }
        },
        card = { game, isGrid, modifier ->
            LibraryGameCard(
                game,
                artwork,
                isGrid,
                modifier,
                { onOpen(game.id) },
                showSystem = false,
            ) {
                PlaythroughContent(game.playthroughs.lastOrNull())
                LogExtras(game)
            }
        },
    )
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
        GameIdentity(game, artwork, prominent = true)
        Glass(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    latest?.finishedAt?.let {
                        Text(
                            finishedDate(it),
                            Modifier.padding(end = 42.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.fillMaxWidth().padding(end = 42.dp)) {
                        latest?.feeling?.let { FeelingBadge(it) }
                            ?: Text(
                                "Thoughts",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                    }

                    if (!latest?.thoughts.isNullOrBlank()) Text(latest.thoughts)
                    LogExtras(game)
                }
                AeroIconButton(
                    AeroSymbol.EDIT,
                    "Edit thoughts and extras",
                    { onEdit(latest ?: Playthrough(finishedAt = null)) },
                    Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 8.dp),
                    compact = true,
                )
            }
        }

        if (game.playthroughs.size > 1) {
            AeroButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "Hide earlier playthroughs" else "Earlier playthroughs")
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
                                        finishedDate(timestamp),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } ?: Spacer(Modifier.weight(1f))
                                AeroIconButton(
                                    AeroSymbol.EDIT,
                                    "Edit earlier playthrough",
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
                Text("Open current game")
            }
        } else {
            PinkButton(onClick = { onReplay(true) }, modifier = Modifier.fillMaxWidth()) {
                AeroIcon(AeroSymbol.REPLAY, Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text("Replay now")
            }
            AeroButton(onClick = { onReplay(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (game.shelf == Shelf.PLAY_THROUGH) "Queue a replay"
                    else "Send to ${game.shelf.label.lowercase()}"
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
        title = { Text(if (isNew) "Finished ${game.title}?" else "Thoughts & details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isNew)
                    Text("Leave a thought if you'd like. Your earlier playthroughs stay saved.")
                Text("How did it feel?", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Feeling.entries.forEach { option ->
                        val selected = feeling == option
                        AeroChip(selected, { feeling = if (selected) null else option }) {
                            AeroIcon(feelingSymbol(option), Modifier.size(17.dp))
                            Text(option.label, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }

                AeroTextField(
                    thoughts,
                    { thoughts = it },
                    label = "Thoughts (optional)",
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!showExtras)
                    AeroButton(
                        onClick = { showExtras = true },
                        modifier =
                            Modifier.semantics { contentDescription = "Add optional details" },
                    ) {
                        AeroIcon(AeroSymbol.EDIT, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Optional details")
                    }

                if (showExtras) {
                    AeroTextField(
                        hours,
                        { hours = it },
                        label = "Hours for this playthrough (optional)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AeroTextField(
                        replays,
                        { replays = it },
                        label = "Replays overall (optional)",
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
                Text(if (isNew) "Finish & save" else "Save")
            }
        },
        dismissButton = { QuietAction(onClick = onDismiss) { Text("Cancel") } },
    )
}
