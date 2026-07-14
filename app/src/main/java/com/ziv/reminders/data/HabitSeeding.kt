package com.ziv.reminders.data

const val EXERCISE_HABIT_INSTANCE_ID = 1L

/**
 * Idempotent — safe to call on every app startup (RemindersApp.onCreate). insertIfAbsent's
 * IGNORE conflict strategy means a row already present is left untouched, so this is how a
 * fourth HabitInstance gets added in the future too: one more insertIfAbsent call here, no UI.
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
}
