package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.HabitInstance
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Before
    fun clearAnyPreExistingAlarms() {
        shadowOf(alarmManager).getScheduledAlarms().forEach { it.operation?.let(alarmManager::cancel) }
    }

    private val instance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )

    @Test
    fun scheduleRemindersForToday_enabledDay_schedulesFiveAlarms() {
        val scheduler = HabitScheduler(context)

        // A date far enough in the past that every reminder hour is still "in the future"
        // relative to itself is impossible to construct without a clock seam here (unlike the
        // receivers, HabitScheduler has no injectable clock — it always compares against real
        // "now"), so this test uses tomorrow, which guarantees every hour is in the future.
        val tomorrow = LocalDate.now().plusDays(1)
        scheduler.scheduleRemindersForToday(tomorrow, instance)

        assertEquals(5, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun scheduleRemindersForToday_nonEnabledDay_schedulesNothing() {
        val scheduler = HabitScheduler(context)
        val neverEnabled = instance.copy(enabledDaysMask = 0)

        scheduler.scheduleRemindersForToday(LocalDate.now().plusDays(1), neverEnabled)

        assertTrue(shadowOf(alarmManager).getScheduledAlarms().isEmpty())
    }

    @Test
    fun scheduleRollover_schedulesOneAlarm() {
        val scheduler = HabitScheduler(context)

        scheduler.scheduleRollover(from = LocalDate.now())

        assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size)
    }
}
