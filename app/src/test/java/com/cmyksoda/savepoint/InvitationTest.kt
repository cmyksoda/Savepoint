package com.cmyksoda.savepoint

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class InvitationTest {
    @Test
    fun deliveryRejectsExpiredWindowAndDuplicateNotification() {
        val invitation = Invitation(at(hour = 19), at(hour = 21), false)
        val eight = at(hour = 20)
        assertTrue(canDeliver(settings, invitation, none, eight, zone))
        assertFalse(canDeliver(settings, invitation, none, at(hour = 21), zone))
        assertFalse(
            canDeliver(settings.copy(sentDate = today.toString()), invitation, none, eight, zone)
        )
    }

    @Test
    fun deliveryRespectsChangedDaysAndPausedState() {
        val invitation = Invitation(at(hour = 19), at(hour = 21), false)
        val eight = at(hour = 20)
        assertFalse(canDeliver(settings.copy(reminders = false), invitation, none, eight, zone))
        assertFalse(canDeliver(settings.copy(days = setOf(2)), invitation, none, eight, zone))
        assertFalse(
            canDeliver(settings.copy(startMinute = 20 * 60 + 30), invitation, none, eight, zone)
        )
    }

    @Test
    fun loggingPlayTodayStopsInvitationsIncludingLater() {
        val invitation = Invitation(at(hour = 19), at(hour = 21), false)
        assertFalse(canDeliver(settings, invitation, setOf(today), at(hour = 20), zone))
        assertTrue(
            canDeliver(settings, invitation, setOf(today.minusDays(1)), at(hour = 20), zone)
        )
        val later = Invitation(at(hour = 20), at(hour = 21), true)
        val s = settings.copy(sentDate = today.toString(), laterAt = later.at)
        assertTrue(canDeliver(s, later, none, at(hour = 20, minute = 5), zone))
        assertFalse(canDeliver(s, later, setOf(today), at(hour = 20, minute = 5), zone))
    }

    @Test
    fun changingThemeCannotUndoPausingInvitationsFromNotification() {
        val previous = Settings(reminders = true)
        val current = previous.copy(reminders = false, sentDate = "2026-10-12")
        val merged = mergeSettings(current, previous, previous.copy(theme = "dark"))
        assertFalse(merged.reminders)
        assertEquals("dark", merged.theme)
        assertEquals(current.sentDate, merged.sentDate)
    }

    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 10, 12)
    private val none = emptySet<LocalDate>()

    private fun at(day: LocalDate = today, hour: Int, minute: Int = 0) =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val settings = Settings(reminders = true, startMinute = 19 * 60, endMinute = 21 * 60)

    @Test
    fun disabledInvitationsNeverSchedule() {
        assertNull(nextInvitation(settings.copy(reminders = false), at(hour = 12), zone))
        assertNull(nextInvitation(settings.copy(days = emptySet()), at(hour = 12), zone))
    }

    @Test
    fun ignoredInvitationDoesNotCauseAnotherThatDay() {
        val next = nextInvitation(settings.copy(sentDate = today.toString()), at(hour = 20), zone)!!
        assertEquals(at(today.plusDays(1), 19), next.at)
        assertFalse(next.requested)
    }

    @Test
    fun explicitLaterAllowsOnlyOneRequestedInvitation() {
        val s = settings.copy(sentDate = today.toString(), laterAt = at(hour = 20))
        assertTrue(nextInvitation(s, at(hour = 19, minute = 30), zone)!!.requested)
        assertFalse(
            nextInvitation(
                    s.copy(requestedDate = today.toString()),
                    at(hour = 19, minute = 30),
                    zone,
                )!!
                .requested
        )
    }

    @Test
    fun skipTodayCancelsExplicitLaterToo() {
        val s = settings.copy(skipDate = today.toString(), laterAt = at(hour = 20))
        assertEquals(at(today.plusDays(1), 19), nextInvitation(s, at(hour = 18), zone)!!.at)
    }

    @Test
    fun expiredWindowsDoNotCatchUpAndWeekdaysAreRespected() {
        val next = nextInvitation(settings.copy(days = setOf(1)), at(hour = 22), zone)!!
        assertEquals(at(today.plusDays(7), 19), next.at)
    }
}
