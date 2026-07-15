package com.ziv.reminders.data

const val EXERCISE_HABIT_INSTANCE_ID = 1L
const val READING_HABIT_INSTANCE_ID = 2L

/**
 * Idempotent — safe to call on every app startup (RemindersApp.onCreate). insertIfAbsent's
 * IGNORE conflict strategy means a row already present is left untouched, so this is how a
 * future habit instance gets added too: one more insertIfAbsent call here, no UI.
 */
suspend fun ensureHabitsSeeded(dao: HabitInstanceDao) {
    dao.insertIfAbsent(
        HabitInstance(
            id = EXERCISE_HABIT_INSTANCE_ID,
            kind = HabitKind.COUNTER.name,
            name = "Exercise",
            enabledDaysMask = 0b1111111,
            notificationTitle = "Reminders",
            notificationBody = "Don't forget your exercises today!",
            counterGoal = 5,
        )
    )
    dao.insertIfAbsent(
        HabitInstance(
            id = READING_HABIT_INSTANCE_ID,
            kind = HabitKind.TIMER.name,
            name = "Reading",
            enabledDaysMask = 0b0011111, // Sun-Thu, matching ReadBook's actual default
            notificationTitle = "Reminders",
            notificationBody = "15 minutes of reading today?",
            counterGoal = null,
            timerTargetSeconds = 900, // 15 minutes, matching ReadBook's actual default
        )
    )
}
