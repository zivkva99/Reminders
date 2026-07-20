package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.HabitInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RolloverReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Before
    fun clearAnyPreExistingAlarms() {
        shadowOf(alarmManager).getScheduledAlarms().forEach { it.operation?.let(alarmManager::cancel) }
    }

    @Test
    fun onReceive_schedulesTodaysRemindersForEveryInstance_andReschedulesItself() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )

        val receiver = RolloverReceiver()
        receiver.newDay = { LocalDate.now().plusDays(1) } // guarantee every reminder hour is in the future
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitSchedulerOverride = HabitScheduler(context)
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_ROLLOVER), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(HabitScheduler.ACTION_ROLLOVER))
        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        // 5 reminder alarms for the one instance + 1 rollover self-reschedule + 1 weekly-summary
        // self-heal (added Task 7) = 7.
        val scheduled = shadowOf(alarmManager).getScheduledAlarms()
        assertEquals(7, scheduled.size)
        val weeklySummaryAlarms = scheduled.filter {
            shadowOf(it.operation).savedIntent.action == HabitScheduler.ACTION_WEEKLY_SUMMARY
        }
        assertEquals(1, weeklySummaryAlarms.size)

        db.close()
    }
}
