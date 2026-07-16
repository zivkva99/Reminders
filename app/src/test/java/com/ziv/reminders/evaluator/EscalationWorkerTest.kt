package com.ziv.reminders.evaluator

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.CrossHabitEvaluator
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerDailyProgress
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EscalationWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exercise = HabitInstance(
        id = EXERCISE_HABIT_INSTANCE_ID, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val reading = HabitInstance(
        id = READING_HABIT_INSTANCE_ID, kind = "TIMER", name = "Reading", enabledDaysMask = 0b0011111,
        notificationTitle = "Reminders", notificationBody = "15 minutes of reading today?",
        counterGoal = null, timerTargetSeconds = 900,
    )

    @Before
    fun setUp() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun TestScopeDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)

    @Test
    fun doWork_conditionMet_postsEscalatedNotification_andReschedulesNextRun() = runTest {
        val db = TestScopeDb().setQueryCoroutineContext(StandardTestDispatcher(testScheduler)).build()
        db.habitInstanceDao().insertIfAbsent(exercise)
        db.habitInstanceDao().insertIfAbsent(reading)
        db.timerDailyProgressDao().upsert(TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null)) // yesterday completed -> streak 1

        val evaluator = CrossHabitEvaluator(
            db.habitInstanceDao(),
            HabitEngine(
                CounterHabitRepository(db.counterDailyProgressDao()),
                TimerHabitRepository(db.timerDailyProgressDao(), SystemClock),
                ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList()),
            ),
            db.evaluatorEscalationDao(),
        )

        val worker = TestListenableWorkerBuilder<EscalationWorker>(context).build()
        worker.habitInstanceDaoOverride = db.habitInstanceDao()
        worker.crossHabitEvaluatorOverride = evaluator
        worker.today = { LocalDate.of(2026, 7, 16) }

        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(reading) }
        assertNotNull(notification)
        assertEquals(HabitNotifications.escalatedChannelId(reading.id), notification.notification.channelId)

        // Confirm the chain re-enqueued itself.
        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(EscalationWorker.UNIQUE_WORK_NAME).get()
        assertEquals(false, workInfos.isEmpty())

        db.close()
    }

    @Test
    fun doWork_conditionNotMet_postsNothing() = runTest {
        val db = TestScopeDb().setQueryCoroutineContext(StandardTestDispatcher(testScheduler)).build()
        db.habitInstanceDao().insertIfAbsent(exercise)
        db.habitInstanceDao().insertIfAbsent(reading)
        // No completed Reading day at all -> streak 0, condition never met.

        val evaluator = CrossHabitEvaluator(
            db.habitInstanceDao(),
            HabitEngine(
                CounterHabitRepository(db.counterDailyProgressDao()),
                TimerHabitRepository(db.timerDailyProgressDao(), SystemClock),
                ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList()),
            ),
            db.evaluatorEscalationDao(),
        )

        val worker = TestListenableWorkerBuilder<EscalationWorker>(context).build()
        worker.habitInstanceDaoOverride = db.habitInstanceDao()
        worker.crossHabitEvaluatorOverride = evaluator
        worker.today = { LocalDate.of(2026, 7, 16) }

        worker.doWork()

        val manager = context.getSystemService(NotificationManager::class.java)
        assertNull(manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(reading) })

        db.close()
    }

    @Test
    fun ensureScheduled_enqueuesTheWorkChain() {
        EscalationWorker.ensureScheduled(context)

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(EscalationWorker.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
    }
}
