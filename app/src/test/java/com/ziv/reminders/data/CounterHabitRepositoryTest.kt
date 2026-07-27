package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeCounterDailyProgressDao : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()

    override suspend fun getByDate(habitInstanceId: Long, date: String): CounterDailyProgress? =
        rows[habitInstanceId to date]

    override suspend fun upsert(progress: CounterDailyProgress) {
        rows[progress.habitInstanceId to progress.date] = progress
    }

    override suspend fun getCompletedDates(habitInstanceId: Long): List<String> =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

class CounterHabitRepositoryTest {

    private val instance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun todayStatus_noRowYet_isZeroOfGoal_notCompleted() = runTest {
        val repo = CounterHabitRepository(FakeCounterDailyProgressDao())

        val status = repo.todayStatus(instance, today)

        assertEquals(0, status.current)
        assertEquals(5, status.goal)
        assertFalse(status.completed)
    }

    @Test
    fun increment_fourTimes_belowGoal_notCompleted() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)

        repeat(4) { repo.increment(instance, today) }
        val status = repo.todayStatus(instance, today)

        assertEquals(4, status.current)
        assertFalse(status.completed)
    }

    @Test
    fun increment_reachingGoal_marksCompleted() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)

        repeat(5) { repo.increment(instance, today) }
        val status = repo.todayStatus(instance, today)

        assertEquals(5, status.current)
        assertTrue(status.completed)
    }

    @Test
    fun undoIncrement_decrementsByOne() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)
        repeat(3) { repo.increment(instance, today) }

        repo.undoIncrement(instance, today)

        val status = repo.todayStatus(instance, today)
        assertEquals(2, status.current)
    }

    @Test
    fun undoIncrement_atZero_isANoOp_neverGoesNegative() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)

        repo.undoIncrement(instance, today)

        val status = repo.todayStatus(instance, today)
        assertEquals(0, status.current)
    }

    @Test
    fun undoIncrement_droppingBelowGoal_unsetsCompleted() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)
        repeat(5) { repo.increment(instance, today) } // reaches goal (5), completed = true

        repo.undoIncrement(instance, today)

        val status = repo.todayStatus(instance, today)
        assertEquals(4, status.current)
        assertFalse(status.completed)
    }

    @Test
    fun currentStreak_todayNotDoneYet_countsThroughYesterday() = runTest {
        val dao = FakeCounterDailyProgressDao()
        dao.rows[1L to "2026-07-12"] = CounterDailyProgress(1L, "2026-07-12", 5, true)
        dao.rows[1L to "2026-07-13"] = CounterDailyProgress(1L, "2026-07-13", 5, true)
        val repo = CounterHabitRepository(dao)

        assertEquals(2, repo.currentStreak(instance, today))
    }

    @Test
    fun currentStreak_gapBreaksIt() = runTest {
        val dao = FakeCounterDailyProgressDao()
        dao.rows[1L to "2026-07-10"] = CounterDailyProgress(1L, "2026-07-10", 5, true)
        // 07-11 missing — gap
        dao.rows[1L to "2026-07-13"] = CounterDailyProgress(1L, "2026-07-13", 5, true)
        val repo = CounterHabitRepository(dao)

        assertEquals(1, repo.currentStreak(instance, today)) // only 07-13 counts back from today
    }

    // Eng review finding (CRITICAL): currentStreak previously delegated straight to
    // HabitStats.currentStreak, a naive consecutive-calendar-day walk with no enabledDaysMask
    // awareness — safe only because Exercise (the sole COUNTER instance until Lego Kit) uses an
    // all-days mask. A second COUNTER instance with a Sun-Thu mask exposes the gap: without this
    // fix, a streak spanning a Friday/Saturday would incorrectly reset. Fixed dates (not
    // LocalDate.now()) for hermeticity: 2026-01-01 is a Thursday, 2026-01-04 is the following
    // Sunday — Friday/Saturday in between are disabled days that must be skipped, not counted as
    // misses.
    @Test
    fun currentStreak_skipsDisabledDays_forASunThuMaskedInstance() = runTest {
        val legoKitInstance = HabitInstance(
            id = 5L, kind = "COUNTER", name = "Lego Kit", enabledDaysMask = 0b0011111,
            notificationTitle = "t", notificationBody = "b", counterGoal = 1,
        )
        val dao = FakeCounterDailyProgressDao()
        dao.rows[5L to "2026-01-01"] = CounterDailyProgress(5L, "2026-01-01", 1, true) // Thursday
        dao.rows[5L to "2026-01-04"] = CounterDailyProgress(5L, "2026-01-04", 1, true) // Sunday
        val repo = CounterHabitRepository(dao)

        // A naive consecutive-calendar-day walk (the bug) sees Sat 01-03/Fri 01-02 as misses and
        // returns 1. The correct, mask-aware answer skips the two disabled days and returns 2.
        assertEquals(2, repo.currentStreak(legoKitInstance, today = LocalDate.of(2026, 1, 4)))
    }
}
