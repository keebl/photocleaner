package com.guus.photocleaner.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.guus.photocleaner.MainActivity
import com.guus.photocleaner.R
import java.time.ZonedDateTime

/**
 * Daily "Op deze dag" reminder. Port of iOS `NotificationManager`: one notification per day
 * at the chosen local time; tapping it opens the deck for today.
 *
 * Uses an inexact, idle-allowed alarm (no exact-alarm permission) that re-arms itself when it
 * fires, and is re-armed after a reboot by [ReminderBootReceiver]. Safe to call on every start.
 */
object ReminderScheduler {

    const val EXTRA_OPEN_ON_THIS_DAY = "com.guus.photocleaner.OPEN_ON_THIS_DAY"
    private const val CHANNEL_ID = "on_this_day"
    private const val NOTIFICATION_ID = 1
    private const val REQUEST_ALARM = 1
    private const val REQUEST_OPEN = 2

    fun apply(context: Context, settings: ReminderSettings, now: ZonedDateTime = ZonedDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        pending.cancel()
        if (!settings.enabled) return
        val trigger = ReminderSchedule.nextTrigger(now, settings.hour, settings.minute)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), alarmIntent(context))
    }

    /** Whether a reminder alarm is currently armed. */
    fun isScheduled(context: Context): Boolean = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
    ) != null

    /** Whether the app may post notifications (Android 13+ runtime permission, or not blocked). */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    internal fun postNotification(context: Context) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_ON_THIS_DAY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_photo_library)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Fires at the reminder time: posts the notification and arms tomorrow's alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = AppPreferences(context).reminder
        if (!settings.enabled) return
        ReminderScheduler.postNotification(context)
        ReminderScheduler.apply(context, settings)
    }
}

/** Alarms do not survive a reboot; re-arm the reminder afterwards. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderScheduler.apply(context, AppPreferences(context).reminder)
    }
}
