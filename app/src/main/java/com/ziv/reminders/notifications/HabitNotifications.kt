package com.ziv.reminders.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ziv.reminders.MainActivity
import com.ziv.reminders.R
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitKind
import com.ziv.reminders.data.WeeklyHabitCount
import com.ziv.reminders.scheduling.HabitReminderReceiver
import com.ziv.reminders.scheduling.HabitScheduler

/** One channel per HabitInstance for its reminder notification (not per-kind or shared) — see
 * Global Constraints. The ongoing Timer foreground notification gets its own second, low-
 * importance, silent per-instance channel. The cross-habit evaluator's escalated notification
 * gets a THIRD per-instance channel, high-importance. The weekly cross-habit summary gets one
 * more, app-wide (not per-instance, since it covers all three habits at once).
 *
 * This file references HabitReminderReceiver/HabitScheduler (the scheduling package) to build
 * the Start/Snooze action PendingIntents — a deliberate mutual reference, mirroring
 * HabitReminderReceiver's own existing import of this file the other direction; this app's
 * "no framework needed at this size" philosophy accepts this over introducing a third
 * mediating class for two small, already-coupled packages.
 */
object HabitNotifications {
    fun channelId(instance: HabitInstance): String = "habit_${instance.id}"
    fun notificationId(instance: HabitInstance): Int = instance.id.toInt()

    fun createChannel(context: Context, instance: HabitInstance) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId(instance), instance.name, NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun buildReminderNotification(context: Context, instance: HabitInstance): Notification {
        // CLEAR_TOP + SINGLE_TOP so tapping resumes an existing MainActivity instead of
        // stacking a duplicate one, matching ReadBook's precedent for notification-tap intents.
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, instance.id.toInt(), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channelId(instance))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.notificationTitle)
            .setContentText(instance.notificationBody)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        // Actionable Start/Snooze buttons are Reading (Timer-kind)-specific — ReadBook's own
        // nudge notification never had these for its Bible-reading habit either.
        if (instance.kind == HabitKind.TIMER.name) {
            builder
                .addAction(0, "Start", startReadingPendingIntent(context, instance.id))
                .addAction(0, "Snooze 15m", snoozeReadingPendingIntent(context, instance.id))
        }
        return builder.build()
    }

    private fun startReadingPendingIntent(context: Context, habitInstanceId: Long): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java)
            .setAction(HabitScheduler.ACTION_START_READING)
            .putExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        return PendingIntent.getBroadcast(
            context, (habitInstanceId * 10 + 1).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozeReadingPendingIntent(context: Context, habitInstanceId: Long): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java)
            .setAction(HabitScheduler.ACTION_SNOOZE_READING)
            .putExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        return PendingIntent.getBroadcast(
            context, (habitInstanceId * 10 + 2).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun timerChannelId(habitInstanceId: Long): String = "habit_${habitInstanceId}_timer"

    // Offset from notificationId(instance) (which uses the plain instance id) so the ongoing
    // timer notification and the hourly reminder notification never collide on the same id.
    fun timerNotificationId(habitInstanceId: Long): Int = (habitInstanceId * 1000).toInt()

    fun createTimerChannel(context: Context, habitInstanceId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(timerChannelId(habitInstanceId), "Timer", NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) }
        )
    }

    private fun timerContentIntent(context: Context, habitInstanceId: Long): PendingIntent {
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, timerNotificationId(habitInstanceId), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // startForeground() must be called synchronously, before the instance can be loaded from
    // Room (a suspend call) — this placeholder needs only the id, corrected via
    // buildTimerNotification() within milliseconds once the instance is known (mirrors
    // ReadBook's ReadingTimerService "0 remaining" placeholder pattern).
    fun buildTimerPlaceholderNotification(context: Context, habitInstanceId: Long): Notification =
        NotificationCompat.Builder(context, timerChannelId(habitInstanceId))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText("Timer running…")
            .setOngoing(true)
            .setContentIntent(timerContentIntent(context, habitInstanceId))
            .build()

    fun buildTimerNotification(context: Context, instance: HabitInstance, remainingSeconds: Int): Notification {
        val minutes = remainingSeconds / 60
        return NotificationCompat.Builder(context, timerChannelId(instance.id))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.name)
            .setContentText("$minutes min left")
            .setOngoing(true)
            .setContentIntent(timerContentIntent(context, instance.id))
            .build()
    }

    fun escalatedChannelId(habitInstanceId: Long): String = "habit_${habitInstanceId}_escalated"

    fun createEscalatedChannel(context: Context, habitInstanceId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(escalatedChannelId(habitInstanceId), "Escalated reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** Posted under the SAME notificationId(instance) as the normal reminder — this replaces
     * that notification rather than duplicating it, satisfying the "never race ahead of or
     * duplicate a habit's own alarm" rule even though it uses a different (higher-importance)
     * channel. */
    fun buildEscalatedReminderNotification(context: Context, instance: HabitInstance): Notification {
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, notificationId(instance), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, escalatedChannelId(instance.id))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.notificationTitle)
            .setContentText("Don't lose your reading streak — and Exercise is still waiting too!")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    const val WEEKLY_SUMMARY_CHANNEL_ID = "weekly_summary"
    // Far outside any per-instance id (1,2,3) or timer id (1000,2000,3000) range.
    const val WEEKLY_SUMMARY_NOTIFICATION_ID = -100

    fun createWeeklySummaryChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(WEEKLY_SUMMARY_CHANNEL_ID, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun buildWeeklySummaryNotification(context: Context, summary: WeeklyHabitCount): Notification {
        // Plain tap-to-open — no action buttons, unlike the Reading reminder above (see this
        // feature's design doc, "the weekly summary is a plain notification, not an
        // action-dispatch case").
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, WEEKLY_SUMMARY_NOTIFICATION_ID, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // "/7" per-habit is a literal 7-calendar-day window count and stays accurate regardless
        // of enabledDaysMask. The combo-streak clause deliberately drops any "/N" denominator —
        // see ActivityScreen's matching correction for why 7 is not an achievable ceiling here.
        val text = "Exercise ${summary.exerciseDays}/7 · Reading ${summary.readingDays}/7 · Tanakh ${summary.tanakhDays}/7" +
            if (summary.comboStreak > 0) " · All three ${summary.comboStreak} day${if (summary.comboStreak == 1) "" else "s"}!" else ""
        return NotificationCompat.Builder(context, WEEKLY_SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Your week in Reminders")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
