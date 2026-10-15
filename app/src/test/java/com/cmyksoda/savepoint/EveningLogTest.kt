package com.cmyksoda.savepoint

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class EveningLogTest {
    private val zone = ZoneId.of("America/New_York")
    private val day = LocalDate.of(2026, 10, 14)
    private val settings = Settings(reminders = true)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day) =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun eveningIsSeparateFromEarlierInvitationAndSkipSuppressesBoth() {
        val alarm = nextEveningLog(settings.copy(sentDate = day.toString()), at(18), zone)!!
        assertEquals(at(21), alarm.at)
        assertTrue(
            canDeliverEveningLog(
                settings.copy(sentDate = day.toString()),
                alarm,
                emptySet(),
                at(21, 5),
                zone,
            )
        )
        assertFalse(
            canDeliverEveningLog(
                settings.copy(skipDate = day.toString()),
                alarm,
                emptySet(),
                at(21, 5),
                zone,
            )
        )
        assertEquals(
            at(21, date = day.plusDays(1)),
            nextEveningLog(settings.copy(skipDate = day.toString()), at(18), zone)!!.at,
        )
    }

    @Test
    fun loggedDayPausedRemindersAndDuplicatesNeverDeliver() {
        val alarm = nextEveningLog(settings, at(18), zone)!!
        assertFalse(canDeliverEveningLog(settings, alarm, setOf(day), at(21, 5), zone))
        assertFalse(
            canDeliverEveningLog(
                settings.copy(reminders = false),
                alarm,
                emptySet(),
                at(21, 5),
                zone,
            )
        )
        assertFalse(
            canDeliverEveningLog(
                settings.copy(logReminder = false),
                alarm,
                emptySet(),
                at(21, 5),
                zone,
            )
        )
        assertFalse(
            canDeliverEveningLog(
                settings.copy(logSentDate = day.toString()),
                alarm,
                emptySet(),
                at(21, 5),
                zone,
            )
        )
        assertFalse(
            canDeliverEveningLog(settings.copy(days = setOf(1)), alarm, emptySet(), at(21, 5), zone)
        )
    }

    @Test
    fun delayedRemindersDoNotSpillPastTenOrIntoAnotherDate() {
        val alarm = nextEveningLog(settings, at(18), zone)!!
        assertFalse(canDeliverEveningLog(settings, alarm, emptySet(), at(20, 59), zone))
        assertFalse(canDeliverEveningLog(settings, alarm, emptySet(), at(22), zone))
        assertFalse(
            canDeliverEveningLog(settings, alarm, emptySet(), at(21, date = day.plusDays(1)), zone)
        )
        assertEquals(at(21, date = day.plusDays(1)), nextEveningLog(settings, at(22), zone)!!.at)
        assertNull(nextEveningLog(settings.copy(reminders = false), at(18), zone))
        assertNull(nextEveningLog(settings.copy(logReminder = false), at(18), zone))
    }

    @Test
    fun localNinePmSurvivesDaylightSavingChange() {
        val dst = LocalDate.of(2026, 11, 1)
        assertEquals(at(21, date = dst), nextEveningLog(settings, at(12, date = dst), zone)!!.at)
    }

    @Test
    fun staleAppearanceChangesCannotOverwriteNotificationClaimsOrMilestones() {
        val current =
            settings.copy(logSentDate = day.toString(), celebratedMilestones = setOf("streak:3"))
        val merged = mergeSettings(current, settings, settings.copy(theme = "dark"))
        assertEquals(current.logSentDate, merged.logSentDate)
        assertEquals(current.celebratedMilestones, merged.celebratedMilestones)
        assertTrue(reminderSettingsChanged(settings, settings.copy(logReminder = false)))
        assertEquals(current, JsonCodec.settings(JsonCodec.settings(current)))
    }
}
