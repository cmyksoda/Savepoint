package com.cmyksoda.savepoint

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun reminderSettingsChanged(previous: Settings, updated: Settings): Boolean =
    previous.reminders != updated.reminders ||
        previous.eveningCheckIn != updated.eveningCheckIn ||
        previous.days != updated.days ||
        previous.startMinute != updated.startMinute ||
        previous.endMinute != updated.endMinute ||
        previous.skipDate != updated.skipDate ||
        previous.sentDate != updated.sentDate ||
        previous.requestedDate != updated.requestedDate ||
        previous.laterAt != updated.laterAt

data class Invitation(val at: Long, val expires: Long, val requested: Boolean)

fun canDeliver(settings: Settings, invitation: Invitation, now: Long, zone: ZoneId): Boolean {
    val local = Instant.ofEpochMilli(now).atZone(zone)
    val date = local.toLocalDate().toString()
    if (
        !settings.reminders ||
            settings.skipDate == date ||
            now < invitation.at ||
            now >= invitation.expires
    )
        return false
    if (invitation.requested)
        return settings.requestedDate != date && settings.laterAt == invitation.at
    val minute = local.hour * 60 + local.minute
    return settings.sentDate != date &&
        local.dayOfWeek.value in settings.days &&
        minute >= settings.startMinute &&
        minute < settings.endMinute
}

fun nextInvitation(settings: Settings, now: Long, zone: ZoneId): Invitation? {
    if (!settings.reminders || settings.days.isEmpty()) return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    if (settings.skipDate == today.toString()) return nextDayInvitation(settings, now, zone, 1)
    if (settings.laterAt > now && settings.requestedDate != today.toString()) {
        val date = Instant.ofEpochMilli(settings.laterAt).atZone(zone).toLocalDate()
        if (date == today)
            return Invitation(
                settings.laterAt,
                minOf(
                    settings.laterAt + 3600000,
                    today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                ),
                true,
            )
    }
    return nextDayInvitation(settings, now, zone, 0)
}

private fun nextDayInvitation(s: Settings, now: Long, zone: ZoneId, offset: Int): Invitation? {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    for (day in offset..7) {
        val date = today.plusDays(day.toLong())
        if (
            date.dayOfWeek.value !in s.days ||
                date.toString() == s.skipDate ||
                date.toString() == s.sentDate
        )
            continue
        val start =
            date.atStartOfDay(zone).plusMinutes(s.startMinute.toLong()).toInstant().toEpochMilli()
        val end =
            date.atStartOfDay(zone).plusMinutes(s.endMinute.toLong()).toInstant().toEpochMilli()
        if (start > now) return Invitation(start, end, false)
    }
    return null
}

object Reminders {
    const val CHANNEL = "rest"
    const val NOTIFICATION = 1

    fun schedule(context: Context, settings: Settings) {
        LogReminders.schedule(context, settings)
        val manager = context.getSystemService(AlarmManager::class.java)
        val base = Intent(context, ReminderReceiver::class.java).setAction("INVITE")
        val pending =
            PendingIntent.getBroadcast(
                context,
                0,
                base,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        manager.cancel(pending)
        val invitation =
            nextInvitation(settings, System.currentTimeMillis(), ZoneId.systemDefault()) ?: return
        base
            .putExtra("at", invitation.at)
            .putExtra("expires", invitation.expires)
            // The extra keeps its old name for alarms set before an update.
            .putExtra("followup", invitation.requested)
        val updated =
            PendingIntent.getBroadcast(
                context,
                0,
                base,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        manager.setWindow(
            AlarmManager.RTC_WAKEUP,
            invitation.at,
            maxOf(600000, invitation.expires - invitation.at),
            updated,
        )
    }

    fun invitationNotification(context: Context, timeout: Long, allowLater: Boolean): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open =
            PendingIntent.getActivity(
                context,
                1,
                Intent(context, SavepointActivity::class.java),
                flags,
            )
        fun action(name: String, code: Int) =
            PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, ReminderReceiver::class.java).setAction(name),
                flags,
            )
        val builder =
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.icon_notification)
                .setContentTitle("A little time for yourself?")
                .setContentText("Your games are here whenever you feel like playing.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(timeout)
        if (allowLater) {
            val later =
                PendingIntent.getActivity(
                    context,
                    2,
                    Intent(context, SavepointActivity::class.java).putExtra("later", true),
                    flags,
                )
            builder.addAction(Notification.Action.Builder(null, "Later", later).build())
        }
        builder.addAction(
            Notification.Action.Builder(null, "Skip today", action("SKIP", 3)).build()
        )
        builder.addAction(Notification.Action.Builder(null, "Pause", action("PAUSE", 4)).build())
        return builder.build()
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in
                setOf(
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                )
        )
            return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.schedule(context, SettingsStore(context).read())
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = SettingsStore(context)
                val today = LocalDate.now().toString()
                if (intent.action == "SKIP" || intent.action == "PAUSE") {
                    val changed =
                        store.change { s ->
                            if (intent.action == "PAUSE") s.copy(reminders = false, laterAt = 0)
                            else s.copy(skipDate = today, laterAt = 0)
                        }
                    Reminders.clear(context)
                    if (intent.action == "SKIP") LogReminders.clear(context)
                    Reminders.schedule(context, changed)
                    return@launch
                }

                val now = System.currentTimeMillis()
                val s = store.read()
                val requested = intent.getBooleanExtra("followup", false)
                val invitation =
                    Invitation(
                        intent.getLongExtra("at", Long.MAX_VALUE),
                        intent.getLongExtra("expires", 0),
                        requested,
                    )
                val valid = canDeliver(s, invitation, now, ZoneId.systemDefault())
                val notifications = context.getSystemService(NotificationManager::class.java)
                val permitted =
                    Build.VERSION.SDK_INT < 33 ||
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                if (valid && permitted && notifications.areNotificationsEnabled()) {
                    var didClaim = false
                    val claimed =
                        store.change { current ->
                            if (!canDeliver(current, invitation, now, ZoneId.systemDefault()))
                                current
                            else {
                                didClaim = true
                                if (requested) current.copy(requestedDate = today, laterAt = 0)
                                else current.copy(sentDate = today)
                            }
                        }

                    if (!didClaim) {
                        Reminders.schedule(context, claimed)
                        return@launch
                    }
                    notifications.createNotificationChannel(
                        NotificationChannel(
                            Reminders.CHANNEL,
                            "Time for yourself",
                            NotificationManager.IMPORTANCE_DEFAULT,
                        )
                    )
                    notifications.notify(
                        Reminders.NOTIFICATION,
                        Reminders.invitationNotification(
                            context,
                            intent.getLongExtra("expires", now) - now,
                            allowLater = !requested && claimed.requestedDate != today,
                        ),
                    )
                }

                Reminders.schedule(context, store.read())
            } finally {
                pending.finish()
            }
        }
    }
}
