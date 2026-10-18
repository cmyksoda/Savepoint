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

fun nextEveningLog(settings: Settings, now: Long, zone: ZoneId): Invitation? {
    if (!settings.reminders || !settings.logReminder || settings.days.isEmpty()) return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    for (offset in 0..7) {
        val day = today.plusDays(offset.toLong())
        if (
            day.dayOfWeek.value !in settings.days ||
                day.toString() in setOf(settings.skipDate, settings.logSentDate)
        )
            continue
        val start = day.atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        val end = day.atTime(22, 0).atZone(zone).toInstant().toEpochMilli()
        if (now < end - 1000) return Invitation(maxOf(start, now + 1000), end, false)
    }
    return null
}

fun canDeliverEveningLog(
    settings: Settings,
    alarm: Invitation,
    dates: Set<LocalDate>,
    now: Long,
    zone: ZoneId,
): Boolean {
    val local = Instant.ofEpochMilli(now).atZone(zone)
    val day = local.toLocalDate()
    return settings.reminders &&
        settings.logReminder &&
        day.dayOfWeek.value in settings.days &&
        day.toString() != settings.skipDate &&
        day.toString() != settings.logSentDate &&
        day !in dates &&
        local.hour == 21 &&
        now >= alarm.at &&
        now < alarm.expires &&
        Instant.ofEpochMilli(alarm.at).atZone(zone).toLocalDate() == day
}

object LogReminders {
    const val CHANNEL = "play-log"
    const val NOTIFICATION = 2
    private const val REQUEST = 20

    fun schedule(context: Context, settings: Settings) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val base = Intent(context, LogReminderReceiver::class.java).setAction("LOG_PLAY")
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        manager.cancel(PendingIntent.getBroadcast(context, REQUEST, base, flags))
        val alarm =
            nextEveningLog(settings, System.currentTimeMillis(), ZoneId.systemDefault()) ?: return
        val pending =
            PendingIntent.getBroadcast(
                context,
                REQUEST,
                base.putExtra("at", alarm.at).putExtra("expires", alarm.expires),
                flags,
            )
        manager.setWindow(
            AlarmManager.RTC_WAKEUP,
            alarm.at,
            maxOf(600000, alarm.expires - alarm.at),
            pending,
        )
    }

    fun notification(context: Context, timeout: Long): Notification {
        val open =
            PendingIntent.getActivity(
                context,
                REQUEST,
                Intent(context, SavepointActivity::class.java)
                    .putExtra("log-play", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle("What did you play today?")
            .setContentText("A quick entry is plenty. You can fill in another day, too.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(timeout)
            .addAction(Notification.Action.Builder(null, "Log play", open).build())
            .build()
    }

    fun clear(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
}

class LogReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "LOG_PLAY") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = SettingsStore(context)
                val now = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                val day = LocalDate.now(zone).toString()
                val alarm =
                    Invitation(
                        intent.getLongExtra("at", Long.MAX_VALUE),
                        intent.getLongExtra("expires", 0),
                        false,
                    )
                val dates =
                    Library(context).use { library ->
                        library.load()
                        playedDates(library.games.value)
                    }
                val manager = context.getSystemService(NotificationManager::class.java)
                val permitted =
                    Build.VERSION.SDK_INT < 33 ||
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                if (permitted && manager.areNotificationsEnabled()) {
                    var claimed = false
                    store.change { current ->
                        if (canDeliverEveningLog(current, alarm, dates, now, zone)) {
                            claimed = true
                            current.copy(logSentDate = day)
                        } else current
                    }
                    if (claimed) {
                        manager.createNotificationChannel(
                            NotificationChannel(
                                LogReminders.CHANNEL,
                                "Evening play log",
                                NotificationManager.IMPORTANCE_DEFAULT,
                            )
                        )
                        manager.notify(
                            LogReminders.NOTIFICATION,
                            LogReminders.notification(context, alarm.expires - now),
                        )
                    }
                }
                LogReminders.schedule(context, store.read())
            } finally {
                pending.finish()
            }
        }
    }
}
