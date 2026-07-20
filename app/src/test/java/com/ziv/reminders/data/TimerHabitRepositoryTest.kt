package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeClock(var millis: Long = 0L) : Clock {
    override fun nowMillis(): Long = millis
}

private class FakeTimerDailyProgressDao : TimerDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, TimerDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: TimerDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
    override suspend fun getActiveSessions() = rows.values.filter { it.activeSessionStartedAt != null }
}

private class FakeReadingSessionLogDao : ReadingSessionLogDao {
    val logged = mutableListOf<ReadingSessionLog>()
    private var nextId = 1L
    override suspend fun insert(session: ReadingSessionLog): Long {
        val withId = session.copy(id = nextId++)
        logged += withId
        return withId.id
    }
    override suspend fun getForDate(habitInstanceId: Long, date: String) =
        logged.filter { it.habitInstanceId == habitInstanceId && it.date == date }.sortedBy { it.startedAt }
    override suspend fun delete(session: ReadingSessionLog) { logged.removeAll { it.id == session.id } }
    override suspend fun deleteForDate(habitInstanceId: Long, date: String) {
        logged.removeAll { it.habitInstanceId == habitInstanceId && it.date == date }
    }
}

class TimerHabitRepositoryTest {

    private val instance = HabitInstance(
        id = 1L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b0011111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )
    private val today = LocalDate.of(2026, 7, 12) // a Sunday, enabled under Sun-Thu

    @Test
    fun todayStatus_noRowYet_isFullTargetNotRunningNotCompleted() = runTest {
        val repo = TimerHabitRepository(FakeTimerDailyProgressDao(), FakeClock())

        val status = repo.todayStatus(instance, today)

        assertEquals(HabitStatus.TimerStatus(remainingSeconds = 900, targetSeconds = 900, isRunning = false, completed = false), status)
    }

    @Test
    fun start_onAFreshDay_createsRowWithFullTargetAndActiveSession() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)

        val row = repo.start(instance, today)

        assertEquals(900, row.remainingSeconds)
        assertEquals(1_000_000L, row.activeSessionStartedAt)
        assertFalse(row.completed)
    }

    @Test
    fun start_resumingAPausedDay_keepsRemainingSecondsFromBefore() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 60_000L // 60s elapse
        repo.stop(instance, today) // pauses with remainingSeconds = 840

        clock.millis = 2_000_000L
        val resumed = repo.start(instance, today)

        assertEquals(840, resumed.remainingSeconds)
        assertEquals(2_000_000L, resumed.activeSessionStartedAt)
    }

    @Test
    fun start_calledTwiceWhileAlreadyRunning_doesNotResetTheOriginalStartTimestamp() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        val first = repo.start(instance, today)
        clock.millis += 60_000L // 60s elapse while still running, no stop() called

        val second = repo.start(instance, today) // called again while already running

        assertEquals(first.activeSessionStartedAt, second.activeSessionStartedAt)
        assertEquals(1_000_000L, second.activeSessionStartedAt)
    }

    @Test
    fun start_whenTodayAlreadyCompleted_isANoOp() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 900_000L // full target elapses
        val completed = repo.stop(instance, today)
        assertTrue(completed?.completed == true)

        clock.millis += 500_000L
        val result = repo.start(instance, today)

        assertEquals(completed, result)
        assertNull(dao.getByDate(1L, today.toString())?.activeSessionStartedAt)
    }

    @Test
    fun stop_reducesRemainingByElapsedTime_andClearsActiveSession() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 120_000L // 120s elapse

        val row = repo.stop(instance, today)

        assertEquals(780, row?.remainingSeconds)
        assertNull(row?.activeSessionStartedAt)
        assertFalse(row?.completed == true)
    }

    @Test
    fun stop_whenElapsedReachesTarget_marksCompleted() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 900_000L // exactly the full target

        val row = repo.stop(instance, today)

        assertEquals(0, row?.remainingSeconds)
        assertTrue(row?.completed == true)
        assertEquals(clock.millis, row?.completedAt)
    }

    @Test
    fun stop_whenNoActiveSession_isANoOp() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 60_000L
        repo.stop(instance, today)
        val before = dao.getByDate(1L, today.toString())

        val result = repo.stop(instance, today) // already stopped — second call

        assertEquals(before, result)
    }

    @Test
    fun todayStatus_whileRunning_computesRemainingFromWallClockAtCallTime() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 200_000L // 200s pass while still running, no stop() called

        val status = repo.todayStatus(instance, today)

        assertEquals(700, status.remainingSeconds)
        assertTrue(status.isRunning)
    }

    @Test
    fun reconcileCrashedSessions_finishesEveryDanglingSessionAndClearsActiveFlag() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        val yesterday = today.minusDays(1)
        repo.start(instance, yesterday) // simulate a session never stopped (crash)
        clock.millis += 60_000L // detected later, on today's app launch

        val reconciled = repo.reconcileCrashedSessions()

        assertEquals(1, reconciled.size)
        assertEquals(yesterday.toString(), reconciled[0].date)
        assertNull(reconciled[0].activeSessionStartedAt)
        assertEquals(840, reconciled[0].remainingSeconds)
    }

    @Test
    fun reconcileCrashedSessions_elapsedExceedsTarget_reducesRemainingToZero_butDoesNotMarkCompleted() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today) // simulate a session never stopped (crash)
        clock.millis += 900_000L + 500_000L // well past the 900s target, detected later

        val reconciled = repo.reconcileCrashedSessions()

        assertEquals(1, reconciled.size)
        assertEquals(0, reconciled[0].remainingSeconds)
        assertFalse(reconciled[0].completed)
        assertNull(reconciled[0].completedAt)
    }

    @Test
    fun currentStreak_delegatesToStreakCalculatorWithTheInstancesEnabledDaysMask() = runTest {
        val dao = FakeTimerDailyProgressDao()
        val thursday = today.plusDays(4) // Thursday, still within Sun-Thu
        dao.rows[1L to today.toString()] = TimerDailyProgress(1L, today.toString(), 900, 0, true, 1L, null)
        val repo = TimerHabitRepository(dao, FakeClock())

        // Sunday completed, Mon-Wed have no rows at all (never happened, not just "missed"),
        // so a plain consecutive walk from Thursday would immediately return 0 — proving this
        // delegates to StreakCalculator's enabled-day-aware walk, not a naive one.
        assertEquals(0, repo.currentStreak(instance, thursday))
    }

    @Test
    fun stop_logsACompletedSessionWithStartAndEndTimestamps() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val sessionLogDao = FakeReadingSessionLogDao()
        val repo = TimerHabitRepository(dao, clock, sessionLogDao)
        repo.start(instance, today)
        clock.millis += 120_000L // 120s elapse

        repo.stop(instance, today)

        val sessions = repo.sessionsForDate(instance, today)
        assertEquals(1, sessions.size)
        assertEquals(1_000_000L, sessions[0].startedAt)
        assertEquals(1_120_000L, sessions[0].endedAt)
        assertEquals(120, sessions[0].durationSeconds)
    }

    @Test
    fun stop_withNoSessionLogDaoProvided_stillCompletesNormally_neverCrashes() = runTest {
        // Regression guard: TimerHabitRepository's 8 pre-existing call sites (test files
        // predating this feature) construct it with only (dao, clock) — sessionLogDao must
        // default to null and finishSession must tolerate that, never crash for lack of
        // session logging.
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 60_000L

        val result = repo.stop(instance, today)

        assertEquals(840, result?.remainingSeconds)
    }

    @Test
    fun deleteSession_removesItFromSessionsForDate() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val sessionLogDao = FakeReadingSessionLogDao()
        val repo = TimerHabitRepository(dao, clock, sessionLogDao)
        repo.start(instance, today)
        clock.millis += 60_000L
        repo.stop(instance, today)
        val logged = repo.sessionsForDate(instance, today).single()

        repo.deleteSession(logged)

        assertEquals(emptyList(), repo.sessionsForDate(instance, today))
    }

    @Test
    fun completedDates_returnsOnlyDatesWithCompletedTrue() = runTest {
        val dao = FakeTimerDailyProgressDao()
        dao.rows[1L to "2026-07-17"] = TimerDailyProgress(1L, "2026-07-17", 900, 0, true, 1L, null)
        dao.rows[1L to "2026-07-18"] = TimerDailyProgress(1L, "2026-07-18", 900, 400, false, null, null)
        val repo = TimerHabitRepository(dao, FakeClock())

        assertEquals(listOf("2026-07-17"), repo.completedDates(instance))
    }
}
