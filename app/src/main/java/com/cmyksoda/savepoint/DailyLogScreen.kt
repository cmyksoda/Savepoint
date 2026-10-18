package com.cmyksoda.savepoint

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d")

@Composable
fun DailyLogScreen(
    games: List<Game>,
    artwork: Artwork,
    date: LocalDate,
    today: LocalDate,
    onDate: (LocalDate) -> Unit,
    onLog: () -> Unit,
    onFinished: () -> Unit,
    celebration: String?,
    onDismissCelebration: () -> Unit,
    preparedCalendar: PlayCalendarMonth? = null,
) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(date).toString()) }
    LaunchedEffect(date) { monthText = YearMonth.from(date).toString() }
    val month = YearMonth.parse(monthText)
    val locale = LocalConfiguration.current.locales[0]
    val monthModel =
        remember(month, locale, preparedCalendar) {
            preparedCalendar?.takeIf { it.month == month && it.locale == locale }
                ?: playCalendarMonth(month, locale)
        }
    val dates = remember(games) { playedDates(games) }
    val streak = remember(dates, today) { currentPlayStreak(dates, today) }
    val entries =
        remember(games, date) {
            games.filter { game -> game.dailyPlays.any { it.date == date.toString() } }
        }
    val context = LocalContext.current
    val feedback = rememberActionFeedback()
    fun selectDate(chosen: LocalDate) {
        if (chosen != date) {
            feedback(false)
            onDate(chosen)
        }
    }
    fun openLog() {
        feedback(false)
        onLog()
    }
    fun pickDate() {
        DatePickerDialog(
                context,
                { _, y, m, d ->
                    val chosen = LocalDate.of(y, m + 1, d)
                    monthText = YearMonth.from(chosen).toString()
                    selectDate(chosen)
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            )
            .apply {
                datePicker.maxDate =
                    today
                        .plusDays(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli() - 1
            }
            .show()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        AeroIcon(
                            AeroSymbol.HEART,
                            Modifier.size(19.dp),
                            MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            if (streak > 0) "$streak day streak" else "your days of play",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                        )
                    }
                    if (dates.isNotEmpty())
                        Text(
                            "${dates.size} ${if (dates.size == 1) "day" else "days"} saved",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
                AeroButton(onClick = onFinished) {
                    AeroIcon(AeroSymbol.LOG, Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("finished", fontSize = 13.sp)
                }
            }
        }
        if (celebration != null)
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            celebration,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        AeroIconButton(
                            AeroSymbol.CLOSE,
                            "dismiss celebration",
                            onDismissCelebration,
                            compact = true,
                        )
                    }
                }
            }
        item {
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AeroIconButton(
                            AeroSymbol.PREVIOUS,
                            "previous month",
                            {
                                feedback(false)
                                monthText = month.minusMonths(1).toString()
                            },
                            compact = true,
                        )
                        Text(
                            monthModel.title,
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClick = ::pickDate)
                                .padding(vertical = 13.dp)
                                .semantics { contentDescription = "choose a date" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                        if (month < YearMonth.from(today))
                            AeroIconButton(
                                AeroSymbol.NEXT,
                                "next month",
                                {
                                    feedback(false)
                                    monthText = month.plusMonths(1).toString()
                                },
                                compact = true,
                            )
                        else Spacer(Modifier.size(48.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                        monthModel.weekdays.forEach { day ->
                            Text(
                                day,
                                Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val offset = monthModel.firstWeekday
                    repeat(monthModel.weeks) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { weekday ->
                                val number = week * 7 + weekday - offset + 1
                                if (number !in 1..month.lengthOfMonth())
                                    Spacer(Modifier.weight(1f).height(45.dp))
                                else {
                                    val calendarDay = monthModel.days[number - 1]
                                    val cell = calendarDay.date
                                    val chosen = cell == date
                                    val future = cell > today
                                    val shape = RoundedCornerShape(12.dp)
                                    val primary = MaterialTheme.colorScheme.primary
                                    Column(
                                        Modifier.weight(1f)
                                            .height(45.dp)
                                            .padding(2.dp)
                                            .clip(shape)
                                            .then(
                                                if (chosen)
                                                    Modifier.background(
                                                        primary.copy(
                                                            alpha =
                                                                if (LocalAeroDark.current) .25f
                                                                else .14f
                                                        )
                                                    )
                                                else Modifier
                                            )
                                            .then(
                                                if (cell == today || chosen)
                                                    Modifier.border(
                                                        1.dp,
                                                        primary.copy(
                                                            alpha = if (chosen) .65f else .3f
                                                        ),
                                                        shape,
                                                    )
                                                else Modifier
                                            )
                                            .clickable(enabled = !future, role = Role.Button) {
                                                selectDate(cell)
                                            }
                                            .semantics {
                                                selected = chosen
                                                contentDescription =
                                                    "${calendarDay.description}${if (cell in dates) ", played" else ""}"
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            number.toString(),
                                            fontSize = 14.sp,
                                            lineHeight = 19.sp,
                                            fontWeight =
                                                if (chosen || cell == today) FontWeight.SemiBold
                                                else FontWeight.Normal,
                                            color =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = if (future) .35f else 1f
                                                ),
                                        )
                                        Box(
                                            Modifier.height(12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (cell in dates)
                                                AeroIcon(
                                                    AeroSymbol.HEART,
                                                    Modifier.size(10.dp),
                                                    primary,
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (date == today) "today"
                        else date.format(DateTimeFormatter.ofPattern("MMMM d")),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        date.format(
                            DateTimeFormatter.ofPattern(
                                "EEEE${if (date.year != today.year) ", yyyy" else ""}"
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (date != today)
                    AeroIconButton(
                        AeroSymbol.CLOCK,
                        "back to today",
                        {
                            monthText = YearMonth.from(today).toString()
                            selectDate(today)
                        },
                        compact = true,
                    )
                if (entries.isNotEmpty())
                    PinkButton(onClick = ::openLog) {
                        AeroIcon(AeroSymbol.EDIT, Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("edit day", fontSize = 13.sp)
                    }
            }
        }
        if (entries.isEmpty())
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("What did you play?", fontWeight = FontWeight.Medium)
                        Text(
                            "A quick entry is plenty.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PinkButton(onClick = ::openLog, modifier = Modifier.fillMaxWidth()) {
                            AeroIcon(AeroSymbol.ADD, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("log what I played")
                        }
                    }
                }
            }
        items(entries, key = { it.id }) { game ->
            val entry = game.dailyPlays.first { it.date == date.toString() }
            Glass(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = ::openLog)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GameThumbnail(game, artwork)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            if (game.shelf == Shelf.HUNTS) displayPokemonName(game.pokemon)
                            else game.title,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (game.shelf == Shelf.HUNTS)
                                huntVersions[game.pokemonVersion] ?: game.system
                            else game.system,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        entry.minutes?.let { LogMetric(AeroSymbol.CLOCK, playDuration(it)) }
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

@Composable
fun DailyPlayEditor(
    games: List<Game>,
    artwork: Artwork,
    date: LocalDate,
    today: LocalDate,
    busy: Boolean,
    onSave: (Map<String, Int?>) -> Unit,
    onCancel: () -> Unit,
    preselected: String? = null,
) {
    val initial =
        remember(date) {
            games
                .filter { g -> g.dailyPlays.any { it.date == date.toString() } }
                .associate { g ->
                    g.id to g.dailyPlays.first { it.date == date.toString() }.minutes
                }
        }
    var chosen by
        rememberSaveable(date.toString()) {
            mutableStateOf((initial.keys + listOfNotNull(preselected)).distinct())
        }
    var times by
        rememberSaveable(date.toString()) {
            mutableStateOf(initial.mapNotNull { (id, m) -> m?.let { id to it } }.toMap())
        }
    var query by rememberSaveable { mutableStateOf("") }
    var editingTime by rememberSaveable { mutableStateOf<String?>(null) }
    val feedback = rememberActionFeedback()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ordered =
        remember(games, query, initial, preselected) {
            games
                .mapNotNull { g ->
                    searchMatchScore(
                            "${g.title} ${g.system} ${if (g.shelf == Shelf.HUNTS) displayPokemonName(g.pokemon) else ""}",
                            query,
                        )
                        ?.let { it to g }
                }
                .sortedWith(
                    compareBy<Pair<Int, Game>> { it.first }
                        .thenBy { it.second.id !in initial && it.second.id != preselected }
                        .thenBy { it.second.place != Place.ACTIVE }
                        .thenBy { it.second.shelf.ordinal }
                        .thenBy { it.second.position }
                        .thenBy { it.second.title }
                )
                .map { it.second }
        }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (date == today) "what did you play today?" else "what did you play?",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    date.format(dayFormat),
                    Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                AeroTextField(
                    query,
                    { query = it },
                    label = "find a game",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    onSubmit = {
                        focus.clearFocus()
                        keyboard?.hide()
                    },
                )
            }
            item {
                Text(
                    "Choose what you played. The pencil adds time if you want.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ordered.isEmpty())
                item {
                    Text(
                        if (games.isEmpty()) "Add a game to your library first, then log it here."
                        else "No matching games."
                    )
                }
            items(ordered, key = { it.id }) { game ->
                val selected = game.id in chosen
                Glass(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(selected, enabled = !busy, role = Role.Checkbox) { checked
                                ->
                                feedback(false)
                                chosen = if (checked) chosen + game.id else chosen - game.id
                            }
                            .semantics {
                                contentDescription = "log ${game.title}"
                                this.selected = selected
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AeroCheckbox(selected, null, Modifier.size(21.dp))
                        GameThumbnail(game, artwork)
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                if (game.shelf == Shelf.HUNTS) displayPokemonName(game.pokemon)
                                else game.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (game.shelf == Shelf.HUNTS)
                                    huntVersions[game.pokemonVersion] ?: game.system
                                else game.system,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (selected)
                                times[game.id]?.let {
                                    Text(
                                        playDuration(it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                        }
                        if (selected)
                            AeroIconButton(
                                AeroSymbol.EDIT,
                                "add time for ${game.title}",
                                { editingTime = game.id },
                                compact = true,
                            )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).imePadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AeroButton(onClick = onCancel, enabled = !busy) { Text("cancel") }
            PinkButton(
                onClick = {
                    focus.clearFocus()
                    keyboard?.hide()
                    feedback(false)
                    onSave(chosen.associateWith { times[it] })
                },
                enabled = !busy && (chosen.isNotEmpty() || initial.isNotEmpty()),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (busy) "saving…"
                    else if (chosen.isEmpty()) "save changes"
                    else "save ${if (chosen.size == 1) "day" else "${chosen.size} games"}"
                )
            }
        }
    }
    editingTime?.let { id ->
        val game = games.firstOrNull { it.id == id }
        if (game != null)
            DurationEditor(
                game.title,
                times[id],
                { value ->
                    times = if (value == null) times - id else times + (id to value)
                    editingTime = null
                },
                { editingTime = null },
            )
    }
}

@Composable
private fun DurationEditor(
    title: String,
    initial: Int?,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var hours by rememberSaveable {
        mutableStateOf(initial?.let { (it / 60).takeIf { h -> h > 0 }?.toString() } ?: "")
    }
    var minutes by rememberSaveable { mutableStateOf(initial?.let { (it % 60).toString() } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AeroDialog(
        onDismissRequest = onDismiss,
        title = { Text("time played") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AeroTextField(
                        hours,
                        { hours = it },
                        label = "hours",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardType = KeyboardType.Number,
                    )
                    AeroTextField(
                        minutes,
                        { minutes = it },
                        label = "minutes",
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardType = KeyboardType.Number,
                    )
                }
                if (initial != null) AeroButton(onClick = { onSave(null) }) { Text("clear time") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            PinkButton(
                onClick = {
                    try {
                        onSave(durationMinutes(hours, minutes))
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    } catch (e: IllegalStateException) {
                        error = e.message
                    }
                }
            ) {
                Text("save")
            }
        },
        dismissButton = { AeroButton(onClick = onDismiss) { Text("cancel") } },
    )
}
