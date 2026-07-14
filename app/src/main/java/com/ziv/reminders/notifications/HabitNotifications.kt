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

/** One channel per HabitInstance (not per-kind or shared) — see Global Constraints. */
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
}
