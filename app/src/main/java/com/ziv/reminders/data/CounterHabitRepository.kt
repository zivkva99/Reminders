package com.ziv.reminders.data

import java.time.LocalDate

class CounterHabitRepository(private val dao: CounterDailyProgressDao) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.CounterStatus {
        val goal = requireNotNull(instance.counterGoal) { "Counter habit ${instance.id} has no counterGoal" }
        val current = dao.getByDate(instance.id, today.toString())?.count ?: 0
        return HabitStatus.CounterStatus(current = current, goal = goal, completed = current >= goal)
    }

    suspend fun increment(instance: HabitInstance, today: LocalDate) {
        val goal = requireNotNull(instance.counterGoal) { "Counter habit ${instance.id} has no counterGoal" }
        val current = dao.getByDate(instance.id, today.toString())?.count ?: 0
        val newCount = current + 1
        dao.upsert(
            CounterDailyProgress(
                habitInstanceId = instance.id,
                date = today.toString(),
                count = newCount,
                completed = newCount >= goal,
            )
        )
    }

    // Floored at 0 (never negative), same guard shape as ScheduleCursorRepository.undoMarkRead —
    // reverses exactly one increment, recomputing completed from the new count so undoing below
    // goal correctly un-sets it.
    suspend fun undoIncrement(instance: HabitInstance, today: LocalDate) {
        val goal = requireNotNull(instance.counterGoal) { "Counter habit ${instance.id} has no counterGoal" }
        val current = dao.getByDate(instance.id, today.toString())?.count ?: 0
        val newCount = (current - 1).coerceAtLeast(0)
        dao.upsert(
            CounterDailyProgress(
                habitInstanceId = instance.id,
                date = today.toString(),
                count = newCount,
                completed = newCount >= goal,
            )
        )
    }

    // Routes through StreakCalculator (mask-aware — skips disabled days rather than treating
    // them as misses), same as Timer/ScheduleCursor. Previously delegated straight to
    // HabitStats.currentStreak, which has no enabledDaysMask awareness at all — safe only while
    // Exercise (the sole COUNTER instance) used an all-days mask; a second COUNTER instance with
    // a non-all-days mask (Lego Kit, Sun-Thu) would have had its streak silently reset every
    // disabled day. Behaviorally identical to the old implementation for an all-days mask (see
    // CounterHabitRepositoryTest's pre-existing all-days streak tests, unchanged by this fix).
    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        StreakCalculator.calculate(HabitStats.parseDates(dao.getCompletedDates(instance.id)), instance.enabledDaysMask, today)

    // Feeds HabitStats' month/best-month/record functions (ExerciseViewModel, Task 5),
    // which need the raw completed-date rows, not just the derived streak count.
    suspend fun completedDates(instance: HabitInstance): List<String> = dao.getCompletedDates(instance.id)
}
