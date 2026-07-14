package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.isEnabledDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Wraps AlarmManager, generalized to take a habitInstanceId so one receiver serves every
 * habit instance regardless of kind. Always inexact (setWindow, never setExact...) — see
 * Global Constraints.
 */
class HabitScheduler(private val context: Context) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun scheduleRemindersForToday(date: LocalDate, instance: HabitInstance) {
        if (!isEnabledDay(date, instance.enabledDaysMask)) return
        for (hour in REMINDER_HOURS) {
            val triggerAt = epochMillisAt(date, hour)
            if (triggerAt <= System.currentTimeMillis()) continue
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_LENGTH_MS, reminderPendingIntent(instance.id, hour)
            )
        }
    }

    fun scheduleRollover(from: LocalDate) {
        val nextMidnight = epochMillisAt(from.plusDays(1), hour = 0, minute = 1)
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, nextMidnight, WINDOW_LENGTH_MS, rolloverPendingIntent())
    }

    private fun epochMillisAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun reminderPendingIntent(habitInstanceId: Long, hour: Int): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        // Unique request code per (instance, hour) so different instances/hours never collide.
        val requestCode = (habitInstanceId * 100 + hour).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun rolloverPendingIntent(): PendingIntent {
        val intent = Intent(context, RolloverReceiver::class.java).setAction(ACTION_ROLLOVER)
        return PendingIntent.getBroadcast(
            context, ROLLOVER_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        val REMINDER_HOURS = listOf(9, 10, 11, 12, 13)
        const val WINDOW_LENGTH_MS = 15 * 60 * 1000L
        // Negative, guaranteed never to collide with a (habitInstanceId * 100 + hour) request code.
        const val ROLLOVER_REQUEST_CODE = -1
        const val ACTION_REMINDER = "com.ziv.reminders.action.REMINDER"
        const val ACTION_ROLLOVER = "com.ziv.reminders.action.ROLLOVER"
        const val EXTRA_HABIT_INSTANCE_ID = "com.ziv.reminders.extra.HABIT_INSTANCE_ID"
    }
}
