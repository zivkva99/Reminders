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

    /** Fires the normal reminder path (ACTION_REMINDER) 15 minutes from now — reuses
     * reminderPendingIntent with a request-code-only "hour" slot (SNOOZE_REQUEST_HOUR_SLOT,
     * outside the real 9-13 range) purely to keep this alarm's request code distinct from the
     * hourly ones for the same instance; the receiver's handling doesn't depend on which hour
     * value was used to build the request code. */
    fun scheduleSnooze(habitInstanceId: Long) {
        val triggerAt = System.currentTimeMillis() + SNOOZE_DELAY_MS
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_LENGTH_MS,
            reminderPendingIntent(habitInstanceId, hour = SNOOZE_REQUEST_HOUR_SLOT),
        )
    }

    /** Schedules the upcoming Sunday 09:00 alarm — called daily from the same self-heal sites as
     * scheduleRollover (RemindersApp.onCreate, BootReceiver, RolloverReceiver), so it recomputes
     * the target Sunday every day and idempotently re-arms the same alarm (FLAG_UPDATE_CURRENT +
     * fixed request code) until that Sunday passes, then automatically advances — same pattern
     * scheduleRemindersForToday/scheduleRollover already use.
     *
     * candidateSunday is `from` itself when `from` is already a Sunday — critical, because this
     * runs on Sunday's own 00:01 rollover too. An earlier version always jumped to `from + 7`
     * whenever `from` was a Sunday (treating "0 days until Sunday" as "no, next week"), which
     * meant Sunday's own midnight rollover self-heal would silently overwrite Saturday
     * rollover's correctly-scheduled *today* 9am alarm with next week's date every single week —
     * the notification would never actually fire. The `<=` now-check below is what actually
     * decides "today" vs. "next week", not the day-of-week arithmetic alone. */
    fun scheduleWeeklySummary(from: LocalDate) {
        val daysUntilSunday = (7 - from.dayOfWeek.value % 7) % 7
        val candidateSunday = from.plusDays(daysUntilSunday.toLong())
        val candidateTriggerAt = epochMillisAt(candidateSunday, hour = 9)
        val triggerAt = if (candidateTriggerAt <= System.currentTimeMillis()) {
            epochMillisAt(candidateSunday.plusDays(7), hour = 9)
        } else {
            candidateTriggerAt
        }
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_LENGTH_MS, weeklySummaryPendingIntent())
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

    private fun weeklySummaryPendingIntent(): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).setAction(ACTION_WEEKLY_SUMMARY)
        return PendingIntent.getBroadcast(
            context, WEEKLY_SUMMARY_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        val REMINDER_HOURS = listOf(9, 10, 11, 12, 13)
        const val WINDOW_LENGTH_MS = 15 * 60 * 1000L
        const val SNOOZE_DELAY_MS = 15 * 60 * 1000L
        // Outside the real 9-13 reminder-hour range — used only for request-code uniqueness.
        const val SNOOZE_REQUEST_HOUR_SLOT = 99
        // Negative, guaranteed never to collide with a (habitInstanceId * 100 + hour) request code.
        const val ROLLOVER_REQUEST_CODE = -1
        const val WEEKLY_SUMMARY_REQUEST_CODE = -2
        const val ACTION_REMINDER = "com.ziv.reminders.action.REMINDER"
        const val ACTION_ROLLOVER = "com.ziv.reminders.action.ROLLOVER"
        const val ACTION_START_READING = "com.ziv.reminders.action.START_READING"
        const val ACTION_SNOOZE_READING = "com.ziv.reminders.action.SNOOZE_READING"
        const val ACTION_WEEKLY_SUMMARY = "com.ziv.reminders.action.WEEKLY_SUMMARY"
        const val EXTRA_HABIT_INSTANCE_ID = "com.ziv.reminders.extra.HABIT_INSTANCE_ID"
    }
}
