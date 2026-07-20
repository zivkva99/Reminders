package com.ziv.reminders.data

import java.time.LocalDate

data class WeeklyHabitCount(
    val exerciseDays: Int,
    val readingDays: Int,
    val tanakhDays: Int,
    // Corrected during /autoplan CEO review — "Streak" here does NOT mean what it means
    // everywhere else in this codebase. HabitStats.currentStreak/StreakCalculator both anchor
    // on today-or-yesterday and require unbroken consecutive days; comboStreak just counts how
    // many of the last 7 days (independently, no contiguity required) all three habits were
    // hit. Kept as-is rather than renamed (would touch ~8 call sites across Task 4/5/6/7 for a
    // naming-only fix) — this doc comment is the disambiguation.
    val comboStreak: Int,
)

/**
 * New aggregation logic written alongside CrossHabitEvaluator, not through it —
 * CrossHabitEvaluator is a single hardcoded Exercise+Reading, single-day escalation condition
 * with no Tanakh wiring and no historical window (see its own doc comment: "a single hardcoded
 * condition, not a generic rule engine"). This is a genuinely different computation: a 7-day
 * window across all three habits, feeding both the Activity screen's combo-streak display and
 * the weekly-summary notification (Task 5/7).
 */
object WeeklySummary {

    fun compute(
        exerciseDates: Set<LocalDate>,
        readingDates: Set<LocalDate>,
        tanakhDates: Set<LocalDate>,
        today: LocalDate,
    ): WeeklyHabitCount {
        val window = (0..6).map { today.minusDays(it.toLong()) }.toSet()
        val comboStreak = window.count { it in exerciseDates && it in readingDates && it in tanakhDates }
        return WeeklyHabitCount(
            exerciseDays = (exerciseDates intersect window).size,
            readingDays = (readingDates intersect window).size,
            tanakhDays = (tanakhDates intersect window).size,
            comboStreak = comboStreak,
        )
    }
}
