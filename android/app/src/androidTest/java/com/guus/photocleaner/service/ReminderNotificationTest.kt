package com.guus.photocleaner.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.guus.photocleaner.R
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The reminder notification and its alarm on a real device (spec AC 17). */
@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {

    @get:Rule
    val permission: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @After
    fun tearDown() {
        notifications.cancelAll()
        ReminderScheduler.apply(context, ReminderSettings(enabled = false))
    }

    @Test
    fun postedNotification_isTheOnThisDayReminder() {
        ReminderScheduler.postNotification(context)

        // notify() is processed asynchronously by the system; wait briefly for it to show up.
        var waited = 0
        while (notifications.activeNotifications.isEmpty() && waited < 2_000) {
            Thread.sleep(50)
            waited += 50
        }
        val active = notifications.activeNotifications.single()
        assertEquals(context.getString(R.string.reminder_title), active.notification.extras.getString("android.title"))
        assertTrue(active.notification.contentIntent != null)
    }

    @Test
    fun enabling_schedulesTheNextAlarm_disablingCancelsIt() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()

        ReminderScheduler.apply(context, ReminderSettings(enabled = true, hour = 9, minute = 0), now)
        assertTrue(alarms.nextAlarmClock == null) // inexact alarm, not an alarm clock
        assertTrue(ReminderScheduler.isScheduled(context))

        ReminderScheduler.apply(context, ReminderSettings(enabled = false), now)
        assertTrue(!ReminderScheduler.isScheduled(context))
    }
}
