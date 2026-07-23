package com.ziv.reminders.engine

import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.ScheduleCursorDailyProgress
import com.ziv.reminders.data.ScheduleCursorDailyProgressDao
import com.ziv.reminders.data.ScheduleCursorProgress
import com.ziv.reminders.data.ScheduleCursorProgressDao
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.ScheduleEntry
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerDailyProgress
import com.ziv.reminders.data.TimerDailyProgressDao
import com.ziv.reminders.data.TimerHabitRepository
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeCounterDailyProgressDao : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: CounterDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

private class FakeTimerDailyProgressDao : TimerDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, TimerDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: TimerDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
    override suspend fun getActiveSessions() = rows.values.filter { it.activeSessionStartedAt != null }
}

private class FakeScheduleCursorProgressDao : ScheduleCursorProgressDao {
    val rows = mutableMapOf<Long, ScheduleCursorProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun upsert(progress: ScheduleCursorProgress) { rows[progress.habitInstanceId] = progress }
}

private class FakeScheduleCursorDailyProgressDao : ScheduleCursorDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, ScheduleCursorDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: ScheduleCursorDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

class HabitEngineTest {

    private val counterInstance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val timerInstance = HabitInstance(
        id = 2L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )
    private val scheduleCursorInstance = HabitInstance(
        id = 3L, kind = "SCHEDULE_CURSOR", name = "Tanakh", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
    )
    private val schedule = listOf(ScheduleEntry("א", "א׳", LocalDate.of(2026, 7, 14)))
    private val today = LocalDate.of(2026, 7, 14)

    private fun newEngine(): HabitEngine = HabitEngine(
        CounterHabitRepository(FakeCounterDailyProgressDao()),
        TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
        ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
    )

    @Test
    fun todayStatus_counterKind_dispatchesToCounterRepository() = runTest {
        val status = newEngine().todayStatus(counterInstance, today)

        assertEquals(HabitStatus.CounterStatus(current = 0, goal = 5, completed = false), status)
    }

    @Test
    fun currentStreak_counterKind_dispatchesToCounterRepository() = runTest {
        val counterDao = FakeCounterDailyProgressDao()
        counterDao.rows[1L to "2026-07-13"] = CounterDailyProgress(1L, "2026-07-13", 5, true)
        val engine = HabitEngine(
            CounterHabitRepository(counterDao),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
        )

        assertEquals(1, engine.currentStreak(counterInstance, today))
    }

    @Test
    fun todayStatus_timerKind_dispatchesToTimerRepository() = runTest {
        val status = newEngine().todayStatus(timerInstance, today)

        assertEquals(HabitStatus.TimerStatus(remainingSeconds = 900, targetSeconds = 900, isRunning = false, completed = false), status)
    }

    @Test
    fun currentStreak_timerKind_dispatchesToTimerRepository() = runTest {
        val timerDao = FakeTimerDailyProgressDao()
        timerDao.rows[2L to "2026-07-13"] = TimerDailyProgress(2L, "2026-07-13", 900, 0, true, 1L, null)
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(timerDao, SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
        )

        assertEquals(1, engine.currentStreak(timerInstance, today))
    }

    @Test
    fun todayStatus_scheduleCursorKind_dispatchesToScheduleCursorRepository() = runTest {
        val status = newEngine().todayStatus(scheduleCursorInstance, today)

        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false, isDueToday = true), status)
    }

    @Test
    fun currentStreak_scheduleCursorKind_dispatchesToScheduleCursorRepository() = runTest {
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        dailyDao.rows[3L to "2026-07-13"] = ScheduleCursorDailyProgress(3L, "2026-07-13", 1, true)
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), dailyDao, schedule),
        )

        assertEquals(1, engine.currentStreak(scheduleCursorInstance, today))
    }

    @Test
    fun todayStatus_unknownKind_throws() = runTest {
        val unknown = counterInstance.copy(kind = "SOMETHING_ELSE")

        assertFailsWith<IllegalArgumentException> { newEngine().todayStatus(unknown, today) }
    }
}
