package com.ziv.reminders.data

import java.time.LocalDate

/**
 * todayValue carries a missing row forward from the most recent earlier day that has one
 * (so today starts where yesterday's session left off, instead of silently resetting).
 * Only when there is no prior logged value at all — e.g. the very first time an exercise
 * is used — does it fall back to EXERCISE_SUB_COUNTER_DEFAULT (matches Shape's
 * live-session default of 5). valuesForDate (used for past dates — e.g. the
 * heatmap-day-tap detail view) omits missing keys entirely rather than defaulting them:
 * a past day with no logged value means "no data," never a fabricated default.
 */
class SubCounterRepository(private val dao: ExerciseSubCounterProgressDao) {

    suspend fun todayValue(exerciseKey: String, today: LocalDate): Int {
        val todayDate = today.toString()
        dao.getByDate(exerciseKey, todayDate)?.let { return it.count }
        return dao.getLatestBefore(exerciseKey, todayDate)?.count ?: EXERCISE_SUB_COUNTER_DEFAULT
    }

    suspend fun adjust(exerciseKey: String, today: LocalDate, delta: Int) {
        val current = todayValue(exerciseKey, today)
        val newValue = (current + delta).coerceIn(0, 99)
        dao.upsert(ExerciseSubCounterProgress(exerciseKey, today.toString(), newValue))
    }

    suspend fun valuesForDate(date: LocalDate): Map<String, Int> =
        dao.getAllForDate(date.toString()).associate { it.exerciseKey to it.count }
}
