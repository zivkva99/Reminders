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

    // Delegates to HabitStats.currentStreak (same anchor logic: if today isn't done yet,
    // the day isn't over — the streak counts through yesterday and isn't broken until
    // midnight passes without today being hit) so this app has exactly one streak-anchor
    // implementation, not two independently maintained copies that could silently diverge.
    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        HabitStats.currentStreak(HabitStats.parseDates(dao.getCompletedDates(instance.id)), today)

    // Feeds HabitStats' month/best-month/record functions (ExerciseViewModel, Task 5),
    // which need the raw completed-date rows, not just the derived streak count.
    suspend fun completedDates(instance: HabitInstance): List<String> = dao.getCompletedDates(instance.id)
}
