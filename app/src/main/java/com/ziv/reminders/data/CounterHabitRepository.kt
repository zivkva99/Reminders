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

    // Mirrors Shape's TrainingStats.currentStreak anchor logic: if today isn't done yet, the
    // day isn't over — the streak counts through yesterday and isn't broken until midnight
    // passes without today being hit.
    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val completedDates = dao.getCompletedDates(instance.id)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        val anchor = if (today in completedDates) today else today.minusDays(1)
        var streak = 0
        var cursor = anchor
        while (cursor in completedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
