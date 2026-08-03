package com.ziv.reminders.engine

import com.ziv.reminders.data.ComputedScheduleProgress
import com.ziv.reminders.data.ComputedScheduleProgressDao
import com.ziv.reminders.data.ComputedScheduleRepository
import com.ziv.reminders.data.ComputedScheduleWatchLog
import com.ziv.reminders.data.ComputedScheduleWatchLogDao
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.IntervalDueLog
import com.ziv.reminders.data.IntervalDueLogDao
import com.ziv.reminders.data.IntervalDueProgress
import com.ziv.reminders.data.IntervalDueProgressDao
import com.ziv.reminders.data.IntervalDueRepository
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

private class FakeComputedScheduleProgressDao : ComputedScheduleProgressDao {
    val rows = mutableMapOf<Long, ComputedScheduleProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun insertIfAbsent(progress: ComputedScheduleProgress) { rows.putIfAbsent(progress.habitInstanceId, progress) }
    override suspend fun upsert(progress: ComputedScheduleProgress) { rows[progress.habitInstanceId] = progress }
}

// Added per the Scope Revision (see the section below the CEO Phase 1 header) — didn't exist in
// the plan's original draft of this task.
private class FakeComputedScheduleWatchLogDao : ComputedScheduleWatchLogDao {
    val rows = mutableListOf<ComputedScheduleWatchLog>()
    override suspend fun insert(entry: ComputedScheduleWatchLog): Long { rows += entry; return rows.size.toLong() }
    override suspend fun getWatchedDates(habitInstanceId: Long): List<String> =
        rows.filter { it.habitInstanceId == habitInstanceId }.map { it.date }.distinct()
}

private class FakeIntervalDueProgressDao : IntervalDueProgressDao {
    val rows = mutableMapOf<Long, IntervalDueProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun upsert(progress: IntervalDueProgress) { rows[progress.habitInstanceId] = progress }
    override suspend fun insertIfAbsent(progress: IntervalDueProgress) { rows.putIfAbsent(progress.habitInstanceId, progress) }
}

private class FakeIntervalDueLogDao : IntervalDueLogDao {
    val rows = mutableListOf<IntervalDueLog>()
    override suspend fun insert(log: IntervalDueLog) { rows.add(log.copy(id = rows.size + 1L)) }
    override suspend fun getByDate(habitInstanceId: Long, date: String) =
        rows.firstOrNull { it.habitInstanceId == habitInstanceId && it.date == date }
    override suspend fun getAllForInstance(habitInstanceId: Long) =
        rows.filter { it.habitInstanceId == habitInstanceId }.sortedWith(compareByDescending<IntervalDueLog> { it.date }.thenByDescending { it.id })
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
    private val computedScheduleInstance = HabitInstance(
        id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
        anchorItemNumber = 542, anchorDate = "2026-07-14", intervalDays = 7,
    )
    private val gardenInstance = HabitInstance(
        id = 6L, kind = "INTERVAL_DUE", name = "Water the garden", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
    )
    private val schedule = listOf(ScheduleEntry("א", "א׳", LocalDate.of(2026, 7, 14)))
    private val today = LocalDate.of(2026, 7, 14)

    private fun newEngine(): HabitEngine = HabitEngine(
        CounterHabitRepository(FakeCounterDailyProgressDao()),
        TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
        ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
        ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
        IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao()),
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
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
            IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao()),
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
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
            IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao()),
        )

        assertEquals(1, engine.currentStreak(timerInstance, today))
    }

    @Test
    fun todayStatus_scheduleCursorKind_dispatchesToScheduleCursorRepository() = runTest {
        val status = newEngine().todayStatus(scheduleCursorInstance, today)

        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false, isDueToday = true, entriesReadToday = 0), status)
    }

    @Test
    fun currentStreak_scheduleCursorKind_dispatchesToScheduleCursorRepository() = runTest {
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        dailyDao.rows[3L to "2026-07-13"] = ScheduleCursorDailyProgress(3L, "2026-07-13", 1, true)
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), dailyDao, schedule),
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
            IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao()),
        )

        assertEquals(1, engine.currentStreak(scheduleCursorInstance, today))
    }

    @Test
    fun todayStatus_computedScheduleKind_dispatchesToComputedScheduleRepository() = runTest {
        // item 543's release date: 2026-07-14 + (543-542)*7 = 2026-07-21 — after `today`.
        val status = newEngine().todayStatus(computedScheduleInstance, today)

        assertEquals(HabitStatus.ComputedScheduleStatus(nextItemNumber = 543, dueCount = 0, isDueToday = false), status)
    }

    @Test
    fun currentStreak_computedScheduleKind_dispatchesToComputedScheduleRepository() = runTest {
        // Renamed and rewritten per the Scope Revision (see the section below the CEO Phase 1
        // header) — this test used to assert currentStreak() was hardcoded to always return 0.
        // It now genuinely dispatches into ComputedScheduleRepository's real HabitStats-backed
        // computation; with an empty watch log (newEngine()'s fixture) that computation legitimately
        // answers 0, so this still exercises the dispatch path correctly, just no longer implies
        // the answer can never be anything else.
        assertEquals(0, newEngine().currentStreak(computedScheduleInstance, today))
    }

    @Test
    fun currentStreak_computedScheduleKind_withWatchLogHistory_dispatchesToRealValue() = runTest {
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        watchLogDao.rows += ComputedScheduleWatchLog(1L, 4L, "2026-07-13", 542)
        watchLogDao.rows += ComputedScheduleWatchLog(2L, 4L, "2026-07-14", 543)
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), watchLogDao),
            IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao()),
        )

        assertEquals(2, engine.currentStreak(computedScheduleInstance, today))
    }

    @Test
    fun todayStatus_unknownKind_throws() = runTest {
        val unknown = counterInstance.copy(kind = "SOMETHING_ELSE")

        assertFailsWith<IllegalArgumentException> { newEngine().todayStatus(unknown, today) }
    }

    @Test
    fun todayStatus_intervalDueKind_dispatchesToIntervalDueRepository() = runTest {
        // Guards the whole dashboard refresh path, not just this row (Eng review finding) — a
        // regression here throws inside DashboardViewModel.refresh()'s per-instance map, before
        // _uiState.value is ever assigned, silently breaking every habit row's display.
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-14")
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
            IntervalDueRepository(progressDao, FakeIntervalDueLogDao()),
        )

        val status = engine.todayStatus(gardenInstance, today)

        assertIs<HabitStatus.IntervalDueStatus>(status)
        assertTrue((status as HabitStatus.IntervalDueStatus).isDue)
    }

    @Test
    fun currentStreak_intervalDueKind_dispatchesToIntervalDueRepositoryHistorySize() = runTest {
        val logDao = FakeIntervalDueLogDao()
        logDao.rows += IntervalDueLog(1L, 6L, "2026-07-10")
        logDao.rows += IntervalDueLog(2L, 6L, "2026-07-13")
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
            IntervalDueRepository(FakeIntervalDueProgressDao(), logDao),
        )

        assertEquals(2, engine.currentStreak(gardenInstance, today))
    }
}
