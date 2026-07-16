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

/** One channel per HabitInstance for its reminder notification (not per-kind or shared) — see
 * Global Constraints. The ongoing Timer foreground notification gets its own second, low-
 * importance, silent per-instance channel. The cross-habit evaluator's escalated notification
 * gets a THIRD per-instance channel, high-importance — Android ties notification importance to
 * the channel (not a per-notification field) on this app's minSdk, so raising priority for one
 * firing without permanently changing the normal reminder channel requires a separate channel. */
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
        return NotificationCompat.Builder(context, channelId(instance))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.notificationTitle)
            .setContentText(instance.notificationBody)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
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
}
