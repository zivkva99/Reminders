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

/** Alarms don't survive reboots — re-establish today's reminders and the rollover chain.
 * Wrapped so a boot-time exception never silently bricks the day's reminders; worst case,
 * nothing gets (re)scheduled here and the next app-open self-heal covers it. */
class BootReceiver : BroadcastReceiver() {

    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitSchedulerOverride: HabitScheduler? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        try {
            // Lazy — same reason as HabitReminderReceiver (Task 7) and RolloverReceiver above.
            val dao = habitInstanceDaoOverride
                ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
            val scheduler = habitSchedulerOverride
                ?: (context.applicationContext as RemindersApp).container.habitScheduler
            val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope.launch {
                try {
                    val date = today()
                    for (instance in dao.getAll()) {
                        scheduler.scheduleRemindersForToday(date, instance)
                    }
                    scheduler.scheduleRollover(from = date)
                    scheduler.scheduleWeeklySummary(from = date)
                } catch (e: Exception) {
                    // Never let a boot-time failure crash the receiver — next app-open self-heals.
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (e: Exception) {
            // Same resilience as above, but for failures before the coroutine even starts (e.g.
            // the applicationContext cast). Robolectric also auto-dispatches BOOT_COMPLETED to a
            // manifest-declared default instance of this receiver (no test overrides) alongside
            // the test's own registered instance, which would otherwise hit this cast and crash
            // the broadcast dispatch synchronously, since robolectric.properties (Task 3) pins
            // the test Application to plain android.app.Application, not RemindersApp.
            pendingResult.finish()
        }
    }
}
