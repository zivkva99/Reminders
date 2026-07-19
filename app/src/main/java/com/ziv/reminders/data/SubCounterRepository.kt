package com.ziv.reminders.data

import java.time.LocalDate

/**
 * todayValue defaults a missing row to EXERCISE_SUB_COUNTER_DEFAULT (matches Shape's
 * live-session default of 5). valuesForDate (used for past dates — e.g. the
 * heatmap-day-tap detail view) omits missing keys entirely rather than defaulting them:
 * a past day with no logged value means "no data," never a fabricated default.
 */
class SubCounterRepository(private val dao: ExerciseSubCounterProgressDao) {

    suspend fun todayValue(exerciseKey: String, today: LocalDate): Int =
        dao.getByDate(exerciseKey, today.toString())?.count ?: EXERCISE_SUB_COUNTER_DEFAULT

    suspend fun adjust(exerciseKey: String, today: LocalDate, delta: Int) {
        val current = todayValue(exerciseKey, today)
        val newValue = (current + delta).coerceIn(0, 99)
        dao.upsert(ExerciseSubCounterProgress(exerciseKey, today.toString(), newValue))
    }

    suspend fun valuesForDate(date: LocalDate): Map<String, Int> =
        dao.getAllForDate(date.toString()).associate { it.exerciseKey to it.count }
}
