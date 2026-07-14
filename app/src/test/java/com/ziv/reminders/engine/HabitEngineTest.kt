package com.ziv.reminders.engine

import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStatus
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

class HabitEngineTest {

    private val counterInstance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun todayStatus_counterKind_dispatchesToCounterRepository() = runTest {
        val engine = HabitEngine(CounterHabitRepository(FakeCounterDailyProgressDao()))

        val status = engine.todayStatus(counterInstance, today)

        assertEquals(HabitStatus.CounterStatus(current = 0, goal = 5, completed = false), status)
    }

    @Test
    fun currentStreak_counterKind_dispatchesToCounterRepository() = runTest {
        val dao = FakeCounterDailyProgressDao()
        dao.rows[1L to "2026-07-13"] = CounterDailyProgress(1L, "2026-07-13", 5, true)
        val engine = HabitEngine(CounterHabitRepository(dao))

        assertEquals(1, engine.currentStreak(counterInstance, today))
    }

    @Test
    fun todayStatus_unknownKind_throws() = runTest {
        val engine = HabitEngine(CounterHabitRepository(FakeCounterDailyProgressDao()))
        val unknown = counterInstance.copy(kind = "SOMETHING_ELSE")

        assertFailsWith<IllegalArgumentException> { engine.todayStatus(unknown, today) }
    }
}
