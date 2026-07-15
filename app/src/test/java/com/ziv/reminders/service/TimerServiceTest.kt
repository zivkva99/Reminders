package com.ziv.reminders.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.Clock
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeClock(var millis: Long = 0L) : Clock {
    override fun nowMillis(): Long = millis
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instance = HabitInstance(
        id = 2L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )

    // See CounterHabitRepositoryTest/DashboardViewModelTest precedent — the DB must be built
    // inside the enclosing runTest block, pinned to that block's own testScheduler.
    private fun TestScope.buildTestDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()

    private fun setUpService(db: AppDatabase, clock: Clock, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        Robolectric.buildService(TimerService::class.java).create().also { controller ->
            val service = controller.get()
            service.habitInstanceDao = db.habitInstanceDao()
            service.timerHabitRepository = TimerHabitRepository(db.timerDailyProgressDao(), clock)
            service.clock = clock
            service.scope = CoroutineScope(StandardTestDispatcher(scheduler))
            service.today = { java.time.LocalDate.of(2026, 7, 15) }
        }

    private fun startIntent() = Intent(TimerService.ACTION_START).putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, instance.id)
    private fun stopIntent() = Intent(TimerService.ACTION_STOP).putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, instance.id)

    @Test
    fun actionStart_startsForeground_andStartsARepositorySession() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        db.habitInstanceDao().insertIfAbsent(instance)
        val controller = setUpService(db, clock, testScheduler)
        val service = controller.get()

        controller.withIntent(startIntent()).startCommand(0, 1)
        testScheduler.runCurrent()

        val shadowService = shadowOf(service)
        assertTrue(!shadowService.isForegroundStopped())
        val row = db.timerDailyProgressDao().getByDate(2L, "2026-07-15")
        assertNotNull(row)
        assertEquals(1_000_000L, row.activeSessionStartedAt)

        controller.withIntent(stopIntent()).startCommand(0, 2)
        testScheduler.runCurrent()
        db.close()
    }

    @Test
    fun actionStart_updatesNotificationWithTheInstancesNameAndRemainingTime() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        db.habitInstanceDao().insertIfAbsent(instance)
        val controller = setUpService(db, clock, testScheduler)

        controller.withIntent(startIntent()).startCommand(0, 1)
        testScheduler.runCurrent()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).getNotification(HabitNotifications.timerNotificationId(2L))
        assertNotNull(notification)
        assertEquals(HabitNotifications.timerChannelId(2L), notification.channelId)
        assertEquals("15 min left", notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())

        controller.withIntent(stopIntent()).startCommand(0, 2)
        testScheduler.runCurrent()
        db.close()
    }

    @Test
    fun actionStop_stopsForeground_andStopsTheRunningSession() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        db.habitInstanceDao().insertIfAbsent(instance)
        val controller = setUpService(db, clock, testScheduler)
        val service = controller.get()

        controller.withIntent(startIntent()).startCommand(0, 1)
        testScheduler.runCurrent()
        clock.millis += 60_000L

        controller.withIntent(stopIntent()).startCommand(0, 2)
        testScheduler.runCurrent()

        val shadowService = shadowOf(service)
        assertTrue(shadowService.isForegroundStopped())
        assertTrue(shadowService.isStoppedBySelf())
        val row = db.timerDailyProgressDao().getByDate(2L, "2026-07-15")
        assertEquals(null, row?.activeSessionStartedAt)
        assertEquals(840, row?.remainingSeconds)

        db.close()
    }

    @Test
    fun runningSession_autoCompletesWhenCountdownNaturallyElapses_withoutAnExplicitStop() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        db.habitInstanceDao().insertIfAbsent(instance)
        val controller = setUpService(db, clock, testScheduler)
        val service = controller.get()

        controller.withIntent(startIntent()).startCommand(0, 1)
        testScheduler.runCurrent()

        val elapsedMs = 901 * 1000L
        clock.millis += elapsedMs
        testScheduler.advanceTimeBy(elapsedMs)
        testScheduler.advanceUntilIdle()

        val row = db.timerDailyProgressDao().getByDate(2L, "2026-07-15")
        assertEquals(true, row?.completed)
        assertEquals(null, row?.activeSessionStartedAt)
        val shadowService = shadowOf(service)
        assertTrue(shadowService.isStoppedBySelf())
        assertTrue(shadowService.isForegroundStopped())

        db.close()
    }

    @Test
    fun actionStart_unknownHabitInstanceId_doesNotCrash_andStopsSelf() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        // Deliberately not inserted — habitInstanceDao().getById(99L) returns null.
        val controller = setUpService(db, clock, testScheduler)
        val service = controller.get()

        controller.withIntent(
            Intent(TimerService.ACTION_START).putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, 99L)
        ).startCommand(0, 1)
        testScheduler.runCurrent()

        val shadowService = shadowOf(service)
        assertTrue(shadowService.isStoppedBySelf())

        db.close()
    }
}
