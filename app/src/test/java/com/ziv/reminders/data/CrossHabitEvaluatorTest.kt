package com.ziv.reminders.data

import com.ziv.reminders.engine.HabitEngine
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeHabitInstanceDao : HabitInstanceDao {
    val rows = mutableMapOf<Long, HabitInstance>()
    override suspend fun getAll() = rows.values.toList()
    override suspend fun getById(id: Long) = rows[id]
    override suspend fun insertIfAbsent(instance: HabitInstance) { rows.putIfAbsent(instance.id, instance) }
}

private class FakeCounterDailyProgressDaoForCrossHabit : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: CounterDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

private class FakeTimerDailyProgressDaoForCrossHabit : TimerDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, TimerDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: TimerDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
    override suspend fun getActiveSessions() = rows.values.filter { it.activeSessionStartedAt != null }
}

private class FakeScheduleCursorProgressDaoForCrossHabit : ScheduleCursorProgressDao {
    override suspend fun getByInstance(habitInstanceId: Long): ScheduleCursorProgress? = null
    override suspend fun upsert(progress: ScheduleCursorProgress) {}
}

private class FakeScheduleCursorDailyProgressDaoForCrossHabit : ScheduleCursorDailyProgressDao {
    override suspend fun getByDate(habitInstanceId: Long, date: String): ScheduleCursorDailyProgress? = null
    override suspend fun upsert(progress: ScheduleCursorDailyProgress) {}
    override suspend fun getCompletedDates(habitInstanceId: Long) = emptyList<String>()
}

private class FakeEvaluatorEscalationDao : EvaluatorEscalationDao {
    val rows = mutableMapOf<Pair<Long, String>, EvaluatorEscalation>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(escalation: EvaluatorEscalation) { rows[escalation.habitInstanceId to escalation.date] = escalation }
}

class CrossHabitEvaluatorTest {

    private val exercise = HabitInstance(
        id = EXERCISE_HABIT_INSTANCE_ID, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val reading = HabitInstance(
        id = READING_HABIT_INSTANCE_ID, kind = "TIMER", name = "Reading", enabledDaysMask = 0b0011111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )
    private val today = LocalDate.of(2026, 7, 16) // a Thursday, enabled for Reading

    private fun newEvaluator(
        instanceDao: FakeHabitInstanceDao,
        counterDao: FakeCounterDailyProgressDaoForCrossHabit = FakeCounterDailyProgressDaoForCrossHabit(),
        timerDao: FakeTimerDailyProgressDaoForCrossHabit = FakeTimerDailyProgressDaoForCrossHabit(),
        escalationDao: FakeEvaluatorEscalationDao = FakeEvaluatorEscalationDao(),
    ): CrossHabitEvaluator {
        val engine = HabitEngine(
            CounterHabitRepository(counterDao),
            TimerHabitRepository(timerDao, SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDaoForCrossHabit(), FakeScheduleCursorDailyProgressDaoForCrossHabit(), emptyList()),
        )
        return CrossHabitEvaluator(instanceDao, engine, escalationDao)
    }

    private fun instanceDaoWithBoth(): FakeHabitInstanceDao {
        val dao = FakeHabitInstanceDao()
        dao.rows[exercise.id] = exercise
        dao.rows[reading.id] = reading
        return dao
    }

    @Test
    fun evaluate_allConditionsMet_escalatesAndWritesFlag() = runTest {
        val timerDao = FakeTimerDailyProgressDaoForCrossHabit()
        timerDao.rows[reading.id to "2026-07-15"] = TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null) // yesterday completed -> streak 1
        val escalationDao = FakeEvaluatorEscalationDao()
        val evaluator = newEvaluator(instanceDaoWithBoth(), timerDao = timerDao, escalationDao = escalationDao)

        val result = evaluator.evaluate(today)

        assertTrue(result)
        assertEquals(true, escalationDao.rows[reading.id to today.toString()]?.escalated)
    }

    @Test
    fun evaluate_exerciseCompleted_doesNotEscalate() = runTest {
        val counterDao = FakeCounterDailyProgressDaoForCrossHabit()
        counterDao.rows[exercise.id to today.toString()] = CounterDailyProgress(exercise.id, today.toString(), 5, true)
        val timerDao = FakeTimerDailyProgressDaoForCrossHabit()
        timerDao.rows[reading.id to "2026-07-15"] = TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null)
        val evaluator = newEvaluator(instanceDaoWithBoth(), counterDao = counterDao, timerDao = timerDao)

        assertFalse(evaluator.evaluate(today))
    }

    @Test
    fun evaluate_readingCompleted_doesNotEscalate() = runTest {
        val timerDao = FakeTimerDailyProgressDaoForCrossHabit()
        timerDao.rows[reading.id to "2026-07-15"] = TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null)
        timerDao.rows[reading.id to today.toString()] = TimerDailyProgress(reading.id, today.toString(), 900, 0, true, System.currentTimeMillis(), null)
        val evaluator = newEvaluator(instanceDaoWithBoth(), timerDao = timerDao)

        assertFalse(evaluator.evaluate(today))
    }

    @Test
    fun evaluate_readingStreakZero_doesNotEscalate() = runTest {
        // No completed days at all for Reading -> streak is 0.
        val evaluator = newEvaluator(instanceDaoWithBoth())

        assertFalse(evaluator.evaluate(today))
    }

    @Test
    fun evaluate_alreadyEscalatedToday_isANoOp_returnsFalse() = runTest {
        val timerDao = FakeTimerDailyProgressDaoForCrossHabit()
        timerDao.rows[reading.id to "2026-07-15"] = TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null)
        val escalationDao = FakeEvaluatorEscalationDao()
        escalationDao.rows[reading.id to today.toString()] = EvaluatorEscalation(reading.id, today.toString(), escalated = true)
        val evaluator = newEvaluator(instanceDaoWithBoth(), timerDao = timerDao, escalationDao = escalationDao)

        assertFalse(evaluator.evaluate(today))
    }
}
