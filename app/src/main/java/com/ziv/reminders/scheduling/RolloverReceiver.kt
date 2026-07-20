package com.ziv.reminders.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.HabitInstanceDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fires nightly at 00:01: schedules the new day's reminders for every habit (if enabled) and
 * reschedules itself. */
class RolloverReceiver : BroadcastReceiver() {

    internal var newDay: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitSchedulerOverride: HabitScheduler? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        // Lazy — each override is checked via ?: before the RemindersApp cast, not a shared
        // eager `val container = ...` line, for the same ClassCastException reason documented
        // in HabitReminderReceiver (Task 7).
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val scheduler = habitSchedulerOverride
            ?: (context.applicationContext as RemindersApp).container.habitScheduler
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val today = newDay()
                for (instance in dao.getAll()) {
                    scheduler.scheduleRemindersForToday(today, instance)
                }
                scheduler.scheduleRollover(from = today)
                scheduler.scheduleWeeklySummary(from = today)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
