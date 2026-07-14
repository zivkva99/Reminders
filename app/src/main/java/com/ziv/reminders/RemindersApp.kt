package com.ziv.reminders

import android.app.Application
import com.ziv.reminders.data.AppContainer
import com.ziv.reminders.data.ensureHabitsSeeded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class RemindersApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Self-heal on every app open: seeds the known habit instances on first launch, then
        // ensures today's reminders and the rollover chain are scheduled even if the
        // midnight/boot jobs never got to run (OEM battery killers, a missed boot receiver,
        // etc.) — not solely reliant on any single scheduling path.
        appScope.launch {
            try {
                ensureHabitsSeeded(container.habitInstanceDao)
                val today = LocalDate.now()
                for (instance in container.habitInstanceDao.getAll()) {
                    container.habitScheduler.scheduleRemindersForToday(today, instance)
                }
                container.habitScheduler.scheduleRollover(from = today)
            } catch (e: Exception) {
                // Never let a startup self-heal failure crash the app — same resilience as
                // BootReceiver's structurally identical self-heal logic.
            }
        }
    }
}
