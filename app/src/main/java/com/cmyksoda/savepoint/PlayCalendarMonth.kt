package com.cmyksoda.savepoint

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlayCalendarDay(val date: LocalDate, val description: String)

data class PlayCalendarMonth(
    val month: YearMonth,
    val locale: Locale,
    val title: String,
    val firstWeekday: Int,
    val weeks: Int,
    val days: List<PlayCalendarDay>,
)

fun playCalendarMonth(month: YearMonth, locale: Locale): PlayCalendarMonth {
    val descriptions = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale)
    val offset = month.atDay(1).dayOfWeek.value - 1
    return PlayCalendarMonth(
        month,
        locale,
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
        offset,
        (offset + month.lengthOfMonth() + 6) / 7,
        (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            PlayCalendarDay(date, date.format(descriptions))
        },
    )
}
