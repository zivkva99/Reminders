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

    @Test
    fun scheduleSnooze_schedulesExactlyOneAlarm() {
        val scheduler = HabitScheduler(context)

        scheduler.scheduleSnooze(habitInstanceId = 2L)

        assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun scheduleWeeklySummary_schedulesExactlyOneAlarm() {
        val scheduler = HabitScheduler(context)

        scheduler.scheduleWeeklySummary(from = LocalDate.of(2026, 7, 19)) // a Sunday

        assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun scheduleWeeklySummary_calledTwice_updatesRatherThanDuplicates() {
        val scheduler = HabitScheduler(context)

        scheduler.scheduleWeeklySummary(from = LocalDate.of(2026, 7, 19))
        scheduler.scheduleWeeklySummary(from = LocalDate.of(2026, 7, 20)) // called again the next day, matching daily rollover re-invocation

        // FLAG_UPDATE_CURRENT + a fixed request code means the second call replaces the first
        // alarm's trigger time rather than adding a duplicate — mirrors scheduleRollover's own
        // idempotent daily re-scheduling.
        assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun scheduleWeeklySummary_fromIsAlreadyASundayInTheFuture_schedulesThatSameSunday_notAWeekLater() {
        val scheduler = HabitScheduler(context)
        // Constructed relative to LocalDate.now() rather than hardcoded, so this test doesn't
        // depend on which real date it happens to run on (HabitScheduler has no injectable
        // clock — same limitation scheduleRemindersForToday's own test documents). Landing a
        // full extra week past the nearest Sunday guarantees that Sunday's 9am is unambiguously
        // in the future no matter what hour "now" actually is.
        val today = LocalDate.now()
        val daysUntilSunday = (7 - today.dayOfWeek.value % 7) % 7
        val futureSunday = today.plusDays((daysUntilSunday + 7).toLong())

        // Regression guard for the bug this task fixed: calling scheduleWeeklySummary with a
        // Sunday as `from` (exactly what happens on Sunday's own midnight rollover self-heal)
        // must schedule THAT Sunday, not silently jump a week ahead and never fire on the
        // Sunday it was actually supposed to.
        scheduler.scheduleWeeklySummary(from = futureSunday)

        val scheduled = shadowOf(alarmManager).getScheduledAlarms().single()
        val expectedTriggerAt = futureSunday.atTime(9, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedTriggerAt, scheduled.triggerAtTime)
    }
}
