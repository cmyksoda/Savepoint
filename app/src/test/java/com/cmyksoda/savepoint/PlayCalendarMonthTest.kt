package com.cmyksoda.savepoint

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PlayCalendarMonthTest {
    @Test
    fun februaryThatStartsMondayUsesFourWeeks() {
        val month = playCalendarMonth(YearMonth.of(2021, 2), Locale.US)
        assertEquals(0, month.firstWeekday)
        assertEquals(4, month.weeks)
        assertEquals(28, month.days.size)
        assertEquals("Sunday, February 28, 2021", month.days.last().description)
    }

    @Test
    fun sixWeekMonthKeepsTheLastDayInItsOwnMonth() {
        val month = playCalendarMonth(YearMonth.of(2026, 8), Locale.US)
        assertEquals(5, month.firstWeekday)
        assertEquals(6, month.weeks)
        assertEquals(LocalDate.of(2026, 8, 31), month.days.last().date)
        assertEquals("Monday, August 31, 2026", month.days.last().description)
    }

    @Test
    fun preparedLabelsFollowTheChosenLocaleAndYear() {
        val month = playCalendarMonth(YearMonth.of(2027, 1), Locale.FRANCE)
        assertEquals("janvier 2027", month.title)
        assertEquals("vendredi, janvier 1, 2027", month.days.first().description)
        assertEquals(31, month.days.map { it.date }.toSet().size)
    }
}
