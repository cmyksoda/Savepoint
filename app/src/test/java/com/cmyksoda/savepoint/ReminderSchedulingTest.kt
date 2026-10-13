package com.cmyksoda.savepoint

import org.junit.Assert.*
import org.junit.Test

class ReminderSchedulingTest {
    @Test
    fun appearanceAndLibraryChangesLeaveTheInvitationPlanAlone() {
        val current = Settings(reminders = true)
        assertFalse(
            reminderSettingsChanged(
                current,
                current.copy(
                    theme = "dark",
                    catalogUrl = "https://example.org",
                    gridViews = setOf("backlog"),
                ),
            )
        )
    }

    @Test
    fun timeWindowDaysAndLaterActionsUpdateTheInvitationPlan() {
        val current = Settings(reminders = true)
        assertTrue(reminderSettingsChanged(current, current.copy(startMinute = 600)))
        assertTrue(reminderSettingsChanged(current, current.copy(endMinute = 1380)))
        assertTrue(reminderSettingsChanged(current, current.copy(days = setOf(1, 3))))
        assertTrue(reminderSettingsChanged(current, current.copy(laterAt = 60000)))
        assertTrue(reminderSettingsChanged(current, current.copy(reminders = false)))
    }
}
