package com.ziv.reminders.data

import com.ziv.reminders.engine.HabitEngine
import java.time.LocalDate

/**
 * The one cross-habit rule this app runs: if Exercise isn't done, Reading isn't done,
 * and Reading has a live streak, escalate Reading's reminder. A single hardcoded
 * condition, not a generic rule engine — see the evaluator design doc's Global
 * Constraints for why. Has no Context/Android dependency — EscalationWorker (the only
 * caller) owns the actual notification posting once this returns true.
 */
class CrossHabitEvaluator(
    private val habitInstanceDao: HabitInstanceDao,
    private val habitEngine: HabitEngine,
    private val escalationDao: EvaluatorEscalationDao,
) {

    /** Returns true only if this call just escalated Reading (and wrote the flag).
     * Returns false if the condition wasn't met, or if today was already escalated by
     * an earlier run this day — in the latter case, nothing is re-evaluated or re-written. */
    suspend fun evaluate(today: LocalDate): Boolean {
        val key = today.toString()
        if (escalationDao.getByDate(READING_HABIT_INSTANCE_ID, key)?.escalated == true) return false

        val exercise = habitInstanceDao.getById(EXERCISE_HABIT_INSTANCE_ID) ?: return false
        val reading = habitInstanceDao.getById(READING_HABIT_INSTANCE_ID) ?: return false

        val exerciseStatus = habitEngine.todayStatus(exercise, today) as? HabitStatus.CounterStatus ?: return false
        val readingStatus = habitEngine.todayStatus(reading, today) as? HabitStatus.TimerStatus ?: return false
        val readingStreak = habitEngine.currentStreak(reading, today)

        val shouldEscalate = !exerciseStatus.completed && !readingStatus.completed && readingStreak > 0
        if (shouldEscalate) {
            escalationDao.upsert(EvaluatorEscalation(READING_HABIT_INSTANCE_ID, key, escalated = true))
        }
        return shouldEscalate
    }
}
