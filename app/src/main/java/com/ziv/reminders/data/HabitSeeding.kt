package com.ziv.reminders.data

const val EXERCISE_HABIT_INSTANCE_ID = 1L
const val READING_HABIT_INSTANCE_ID = 2L
const val TANAKH_HABIT_INSTANCE_ID = 3L
// Value only, no ensureHabitsSeeded() entry yet — that's Task 7 ("Seed the C++ Weekly habit
// instance"), which per the Scope Revision now runs after this task (Task 6). Added here because
// ComputedScheduleStatsViewModel (Task 6) needs the constant to compile; matches the 4L already
// used as this kind's habit instance id throughout Tasks 1-5's tests.
const val CPP_WEEKLY_HABIT_INSTANCE_ID = 4L

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
    dao.insertIfAbsent(
        HabitInstance(
            id = TANAKH_HABIT_INSTANCE_ID,
            kind = HabitKind.SCHEDULE_CURSOR.name,
            name = "Tanakh",
            enabledDaysMask = 0b0011111, // Sun-Thu, matching the schedule CSV's own cadence
            notificationTitle = "Reminders",
            notificationBody = "Time for today's Tanakh reading?",
            counterGoal = null,
        )
    )
}
