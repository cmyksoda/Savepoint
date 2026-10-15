package com.cmyksoda.savepoint

import java.time.LocalDate

/** A calendar date stays attached to the day chosen, even after changing time zones. */
data class DailyPlay(val date: String, val minutes: Int? = null)

fun validateDailyPlay(entry: DailyPlay) {
    require(LocalDate.parse(entry.date).toString() == entry.date) { "Invalid play date" }
    require(entry.minutes == null || entry.minutes >= 0) { "Playtime cannot be negative" }
}

fun saveDailyPlays(
    games: List<Game>,
    date: LocalDate,
    chosen: Map<String, Int?>,
    today: LocalDate,
): List<Game> {
    require(!date.isAfter(today)) { "Choose today or an earlier date" }
    require(chosen.keys.all { id -> games.any { it.id == id } }) {
        "This game is no longer in your library"
    }
    chosen.values.forEach { require(it == null || it >= 0) { "Playtime cannot be negative" } }
    return games.map { game ->
        val remaining = game.dailyPlays.filterNot { it.date == date.toString() }
        game.copy(
            dailyPlays =
                if (chosen.containsKey(game.id)) {
                    (remaining + DailyPlay(date.toString(), chosen[game.id])).sortedBy { it.date }
                } else remaining
        )
    }
}

fun playedDates(games: List<Game>): Set<LocalDate> =
    games.flatMap { it.dailyPlays }.map { LocalDate.parse(it.date) }.toSet()

fun currentPlayStreak(dates: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in dates) today else today.minusDays(1)
    var count = 0
    while (day in dates) {
        count++
        day = day.minusDays(1)
    }
    return count
}

fun longestPlayStreak(dates: Set<LocalDate>): Int {
    var longest = 0
    var count = 0
    var previous: LocalDate? = null
    for (day in dates.sorted()) {
        count = if (previous?.plusDays(1) == day) count + 1 else 1
        longest = maxOf(longest, count)
        previous = day
    }
    return longest
}

data class PlayMilestone(val key: String, val message: String)

fun reachedPlayMilestones(dates: Set<LocalDate>): List<PlayMilestone> {
    val longest = longestPlayStreak(dates)
    return listOf(3, 7, 14, 30, 60, 100, 365)
        .filter { longest >= it }
        .map { PlayMilestone("streak:$it", "$it days of play in a row 💕") } +
        listOf(7, 25, 50, 100, 250, 365, 500, 1000)
            .filter { dates.size >= it }
            .map { PlayMilestone("days:$it", "You've made time for play on $it days 💕") }
}

fun durationMinutes(hours: String, minutes: String): Int? {
    if (hours.isBlank() && minutes.isBlank()) return null
    fun value(text: String): Long =
        if (text.isBlank()) 0
        else
            text.trim().toLongOrNull()?.takeIf { it >= 0 }
                ?: error("Enter a positive whole number or leave it blank")
    val h = value(hours)
    val m = value(minutes)
    require(m <= 59) { "Use the hours field for 60 minutes or more" }
    require(h <= Int.MAX_VALUE / 60 && h * 60 + m <= Int.MAX_VALUE) { "That duration is too large" }
    return (h * 60 + m).toInt()
}

fun playDuration(minutes: Int): String =
    when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} hr"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
