package com.ziv.reminders.scheduling

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitReminderReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "Reminders", notificationBody = "Don't forget your exercises today!",
        counterGoal = 5,
    )

    // goAsync() only works via the real broadcast mechanism — register + sendBroadcast + idle,
    // not receiver.onReceive() directly (mirrors ReadBook's NudgeReceiverTest precedent).
    private fun dispatch(receiver: HabitReminderReceiver, habitInstanceId: Long) {
        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_REMINDER), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(
            Intent(HabitScheduler.ACTION_REMINDER).putExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun onReceive_todayNotCompleted_postsAReminderNotification() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(instance)

        val receiver = HabitReminderReceiver()
        receiver.today = { LocalDate.of(2026, 7, 14) }
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitEngineOverride = HabitEngine(
            CounterHabitRepository(db.counterDailyProgressDao()),
            com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock),
            com.ziv.reminders.data.ScheduleCursorRepository(
                db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList(),
            ),
        )
        receiver.evaluatorEscalationDaoOverride = db.evaluatorEscalationDao()
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        dispatch(receiver, habitInstanceId = 1L)
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(instance) }
        assertEquals(HabitNotifications.channelId(instance), notification?.notification?.channelId)

        db.close()
    }

    @Test
    fun onReceive_todayAlreadyCompleted_postsNothing() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(instance)
        repeat(5) {
            db.counterDailyProgressDao().upsert(
                com.ziv.reminders.data.CounterDailyProgress(1L, "2026-07-14", it + 1, it + 1 >= 5)
            )
        }

        val receiver = HabitReminderReceiver()
        receiver.today = { LocalDate.of(2026, 7, 14) }
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitEngineOverride = HabitEngine(
            CounterHabitRepository(db.counterDailyProgressDao()),
            com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock),
            com.ziv.reminders.data.ScheduleCursorRepository(
                db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList(),
            ),
        )
        receiver.evaluatorEscalationDaoOverride = db.evaluatorEscalationDao()
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        dispatch(receiver, habitInstanceId = 1L)
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(instance) }
        assertNull(notification)

        db.close()
    }

    @Test
    fun onReceive_todayAlreadyEscalated_postsNothing() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(instance)
        db.evaluatorEscalationDao().upsert(
            com.ziv.reminders.data.EvaluatorEscalation(1L, "2026-07-14", escalated = true)
        )

        val receiver = HabitReminderReceiver()
        receiver.today = { LocalDate.of(2026, 7, 14) }
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitEngineOverride = HabitEngine(
            CounterHabitRepository(db.counterDailyProgressDao()),
            com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock),
            com.ziv.reminders.data.ScheduleCursorRepository(
                db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList(),
            ),
        )
        receiver.evaluatorEscalationDaoOverride = db.evaluatorEscalationDao()
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        dispatch(receiver, habitInstanceId = 1L)
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(instance) }
        assertNull(notification)

        db.close()
    }

    @Test
    fun onReceive_actionStartReading_startsTimerServiceViaForegroundServiceStart() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(id = 2L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b0011111, notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900)
        )

        val receiver = HabitReminderReceiver()
        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_START_READING), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(
            Intent(HabitScheduler.ACTION_START_READING).putExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, 2L)
        )
        shadowOf(Looper.getMainLooper()).idle()

        val nextService = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedService
        assertEquals(com.ziv.reminders.service.TimerService.ACTION_START, nextService?.action)
        assertEquals(2L, nextService?.getLongExtra(com.ziv.reminders.service.TimerService.EXTRA_HABIT_INSTANCE_ID, -1L))
    }

    @Test
    fun onReceive_actionWeeklySummary_someActivityThisWeek_postsAWeeklySummaryNotification() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        // At least one completed day this week — otherwise the all-zero suppression guard
        // (added per /autoplan CEO review) would make this test indistinguishable from the
        // suppression test below.
        // The HabitInstance row itself must exist too — handleWeeklySummary looks up the
        // instance via dao.getById(EXERCISE_HABIT_INSTANCE_ID) before it will read any
        // completed-dates for it; without this row present, exerciseDates stays empty and the
        // all-zero suppression guard fires, which is not what this test is checking.
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(id = EXERCISE_HABIT_INSTANCE_ID, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111, notificationTitle = "t", notificationBody = "b", counterGoal = 5)
        )
        db.counterDailyProgressDao().upsert(
            com.ziv.reminders.data.CounterDailyProgress(EXERCISE_HABIT_INSTANCE_ID, LocalDate.now().toString(), 5, true)
        )

        val receiver = HabitReminderReceiver()
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.counterHabitRepositoryOverride = CounterHabitRepository(db.counterDailyProgressDao())
        receiver.timerHabitRepositoryOverride = com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock)
        receiver.scheduleCursorRepositoryOverride = com.ziv.reminders.data.ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList())
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_WEEKLY_SUMMARY), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(HabitScheduler.ACTION_WEEKLY_SUMMARY))
        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())
    }

    @Test
    fun onReceive_actionWeeklySummary_noActivityAtAll_suppressesTheNotification() = runTest {
        // Regression test for the /autoplan CEO cherry-pick: a fresh install (empty DB) must
        // never post "Exercise 0/7 · Reading 0/7 · Tanakh 0/7" — a useless nag.
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()

        val receiver = HabitReminderReceiver()
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.counterHabitRepositoryOverride = CounterHabitRepository(db.counterDailyProgressDao())
        receiver.timerHabitRepositoryOverride = com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock)
        receiver.scheduleCursorRepositoryOverride = com.ziv.reminders.data.ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList())
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_WEEKLY_SUMMARY), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(HabitScheduler.ACTION_WEEKLY_SUMMARY))
        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).size())
    }
}
