package com.ziv.reminders.data

import androidx.room.Entity

/**
 * One row per (exerciseKey, date) — NOT one row per date with 4 columns. A single row
 * covering all 4 sub-counters would let a partial @Upsert on one counter silently clobber
 * the other 3 (Room replaces the whole row on a primary-key conflict); keying by
 * exerciseKey instead mirrors Shape's own independent-key model (KEY_EX1..KEY_EX4 in
 * Shape's CounterDataStore.kt) so each +/- tap upserts exactly one independent row.
 */
@Entity(tableName = "exercise_sub_counter_progress", primaryKeys = ["exerciseKey", "date"])
data class ExerciseSubCounterProgress(
    val exerciseKey: String,
    val date: String,
    val count: Int,
)

const val EXERCISE_KEY_LATERAL_RAISE = "lateral_raise"
const val EXERCISE_KEY_ARM_ROTATION = "arm_rotation"
const val EXERCISE_KEY_SITUP = "situp"
const val EXERCISE_KEY_PUSHUP = "pushup"

val ALL_EXERCISE_KEYS = listOf(
    EXERCISE_KEY_LATERAL_RAISE,
    EXERCISE_KEY_ARM_ROTATION,
    EXERCISE_KEY_SITUP,
    EXERCISE_KEY_PUSHUP,
)

// Matches Shape's own sub-counter default (CounterViewModel.kt: `prefs[it] ?: 5`).
const val EXERCISE_SUB_COUNTER_DEFAULT = 5
