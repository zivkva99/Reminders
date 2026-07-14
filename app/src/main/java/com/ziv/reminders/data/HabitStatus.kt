package com.ziv.reminders.data

/**
 * The one type unified across every kind — see HabitEngine (engine/HabitEngine.kt) for why
 * only the read path (todayStatus/currentStreak) is generic; each kind's own progress-marking
 * action stays a method on that kind's own repository.
 */
sealed interface HabitStatus {
    data class CounterStatus(val current: Int, val goal: Int, val completed: Boolean) : HabitStatus
}
