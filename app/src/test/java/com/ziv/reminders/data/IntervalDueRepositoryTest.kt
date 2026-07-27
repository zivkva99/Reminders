package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeIntervalDueProgressDao : IntervalDueProgressDao {
    val rows = mutableMapOf<Long, IntervalDueProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun upsert(progress: IntervalDueProgress) { rows[progress.habitInstanceId] = progress }
    override suspend fun insertIfAbsent(progress: IntervalDueProgress) {
        if (!rows.containsKey(progress.habitInstanceId)) rows[progress.habitInstanceId] = progress
    }
}

private class FakeIntervalDueLogDao : IntervalDueLogDao {
    val rows = mutableListOf<IntervalDueLog>()
    override suspend fun insert(log: IntervalDueLog) { rows.add(log.copy(id = rows.size + 1L)) }
    override suspend fun getByDate(habitInstanceId: Long, date: String) =
        rows.firstOrNull { it.habitInstanceId == habitInstanceId && it.date == date }
    override suspend fun getAllForInstance(habitInstanceId: Long) =
        rows.filter { it.habitInstanceId == habitInstanceId }.sortedWith(compareByDescending<IntervalDueLog> { it.date }.thenByDescending { it.id })
}

class IntervalDueRepositoryTest {

    private val instance = HabitInstance(
        id = 6L, kind = "INTERVAL_DUE", name = "Water the garden", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
    )

    @Test
    fun todayStatus_dueDateIsToday_isDueTrue() = runTest {
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-27")
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 27))

        assertTrue(status.isDue)
        assertEquals(LocalDate.of(2026, 7, 27), status.dueDate)
    }

    @Test
    fun todayStatus_dueDateInThePast_isDueTrue() = runTest {
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-20")
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())

        assertTrue(repo.todayStatus(instance, today = LocalDate.of(2026, 7, 27)).isDue)
    }

    @Test
    fun todayStatus_dueDateTomorrow_isDueFalse() = runTest {
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-28")
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())

        assertFalse(repo.todayStatus(instance, today = LocalDate.of(2026, 7, 27)).isDue)
    }

    @Test
    fun markDone_setsNextDueDateToTodayPlusIntervalDays_regardlessOfHowOverdue() = runTest {
        // The overdue-logging edge case: due date is 10 days in the past, intervalDays=3 — the
        // new due date must be today+3, never (old due date)+3 (which would still be in the past).
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-17")
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())
        val today = LocalDate.of(2026, 7, 27)

        repo.markDone(instance, intervalDays = 3, today)

        assertEquals("2026-07-30", progressDao.rows[6L]?.nextDueDate)
    }

    @Test
    fun markDone_logsTodaysDate_neverTheOriginalDueDate() = runTest {
        val logDao = FakeIntervalDueLogDao()
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), logDao)
        val today = LocalDate.of(2026, 7, 27)

        repo.markDone(instance, intervalDays = 5, today)

        assertEquals(listOf("2026-07-27"), logDao.rows.map { it.date })
    }

    @Test
    fun markDone_rejectsIntervalDaysLessThanOne() = runTest {
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao())

        assertFailsWith<IllegalArgumentException> {
            repo.markDone(instance, intervalDays = 0, today = LocalDate.of(2026, 7, 27))
        }
    }

    @Test
    fun rescheduleOnly_updatesDueDate_withoutLoggingACompletion() = runTest {
        val logDao = FakeIntervalDueLogDao()
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), logDao)
        val today = LocalDate.of(2026, 7, 27)

        repo.rescheduleOnly(instance, intervalDays = 4, today)

        assertTrue(logDao.rows.isEmpty())
    }

    @Test
    fun rescheduleOnly_worksEvenWhenNotCurrentlyDue() = runTest {
        // Long-press reschedule-only is available regardless of due state (design doc Architecture).
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-08-15") // far future, not due
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())

        repo.rescheduleOnly(instance, intervalDays = 2, today = LocalDate.of(2026, 7, 27))

        assertEquals("2026-07-29", progressDao.rows[6L]?.nextDueDate)
    }

    @Test
    fun rescheduleOnly_rejectsIntervalDaysLessThanOne() = runTest {
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao())

        assertFailsWith<IllegalArgumentException> {
            repo.rescheduleOnly(instance, intervalDays = -1, today = LocalDate.of(2026, 7, 27))
        }
    }

    @Test
    fun todayStatus_completedTodayReflectsWhetherALogRowExistsForToday() = runTest {
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao())
        val today = LocalDate.of(2026, 7, 27)

        repo.markDone(instance, intervalDays = 5, today)
        val status = repo.todayStatus(instance, today)

        assertTrue(status.completedToday)
        // markDone always requires intervalDays >= 1, so nextDueDate is always in the future
        // immediately after — isDue and completedToday never disagree same-day.
        assertFalse(status.isDue)
    }

    @Test
    fun history_returnsLoggedDatesMostRecentFirst() = runTest {
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao())

        repo.markDone(instance, intervalDays = 5, today = LocalDate.of(2026, 7, 10))
        repo.markDone(instance, intervalDays = 5, today = LocalDate.of(2026, 7, 20))

        assertEquals(listOf(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 10)), repo.history(instance))
    }
}
