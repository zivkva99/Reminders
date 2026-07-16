package com.ziv.reminders.scheduling

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fired by AlarmManager at each hourly reminder time. A no-op if today is already completed. */
class HabitReminderReceiver : BroadcastReceiver() {

    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitEngineOverride: HabitEngine? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        val pendingResult = goAsync()
        // Each override is checked via ?: BEFORE the RemindersApp cast, not a separate eager
        // `val container = (context.applicationContext as RemindersApp)...` line — that would
        // evaluate the cast unconditionally, throwing ClassCastException in every test even
        // when overrides are provided (Robolectric's application context isn't a RemindersApp
        // instance — see robolectric.properties from Task 3). Mirrors ReadBook's NudgeReceiver
        // exactly, which relies on this same short-circuiting for the same reason.
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val engine = habitEngineOverride
            ?: (context.applicationContext as RemindersApp).container.habitEngine
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val instance = dao.getById(habitInstanceId) ?: return@launch
                val status = engine.todayStatus(instance, today())
                val completed = when (status) {
                    is HabitStatus.CounterStatus -> status.completed
                    is HabitStatus.TimerStatus -> status.completed
                    is HabitStatus.ScheduleCursorStatus -> status.completed
                }
                if (!completed) {
                    HabitNotifications.createChannel(context, instance)
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.notify(HabitNotifications.notificationId(instance), HabitNotifications.buildReminderNotification(context, instance))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
