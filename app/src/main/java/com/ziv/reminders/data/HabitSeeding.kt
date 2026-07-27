package com.ziv.reminders.data

import android.util.Log
import java.time.LocalDate

const val EXERCISE_HABIT_INSTANCE_ID = 1L
const val READING_HABIT_INSTANCE_ID = 2L
const val TANAKH_HABIT_INSTANCE_ID = 3L
const val CPP_WEEKLY_HABIT_INSTANCE_ID = 4L
const val LEGO_KIT_HABIT_INSTANCE_ID = 5L
const val GARDEN_HABIT_INSTANCE_ID = 6L

/**
 * Idempotent — safe to call on every app startup (RemindersApp.onCreate). insertIfAbsent's
 * IGNORE conflict strategy means a row already present is left untouched, so this is how a
 * future habit instance gets added too: one more insertIfAbsent call here, no UI.
 *
 * The C++ Weekly (ComputedSchedule) instance needs a second write beyond its HabitInstance row:
 * computedScheduleProgressDao.insertIfAbsent seeds the starting nextItemNumber exactly once,
 * using the same IGNORE-on-conflict idempotency as every other insertIfAbsent call in this
 * function — a later reseed on subsequent app starts must never reset the user's tap progress
 * back to 543.
 */
suspend fun ensureHabitsSeeded(
    dao: HabitInstanceDao,
    computedScheduleProgressDao: ComputedScheduleProgressDao,
    intervalDueProgressDao: IntervalDueProgressDao,
) {
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
    // Isolated in its own try/catch (Final Approval Gate decision — flagged independently by
    // all 3 /autoplan review phases): anchorDate is a TODO() placeholder until a human fills in
    // episode 542's real release date, so this one instance's seeding can fail without taking
    // down Exercise/Reading/Tanakh above, which have nothing to do with this new row's config.
    // Catches Throwable, not Exception — TODO() throws NotImplementedError, which is an Error.
    try {
        dao.insertIfAbsent(
            HabitInstance(
                id = CPP_WEEKLY_HABIT_INSTANCE_ID,
                kind = HabitKind.COMPUTED_SCHEDULE.name,
                name = "C++ Weekly",
                enabledDaysMask = 0b1111111, // every day — a new episode can be watched any day of the week
                notificationTitle = "Reminders",
                notificationBody = "New C++ Weekly episode ready to watch?",
                counterGoal = null,
                anchorItemNumber = 542,
                anchorDate = "2026-07-21", // episode 542's real release date, confirmed by the user
                intervalDays = 7,
            )
        )
        computedScheduleProgressDao.insertIfAbsent(
            ComputedScheduleProgress(habitInstanceId = CPP_WEEKLY_HABIT_INSTANCE_ID, nextItemNumber = 543)
        )
    } catch (e: Throwable) {
        // No HabitInstance row means the dashboard simply won't render a 4th row at all — no
        // "broken row" UI needed, since DashboardViewModel only queries/renders instances that
        // actually exist. This log line is this function's only logging call in the whole app
        // (see CEO Section 8 — this codebase otherwise has zero logging infra); justified here
        // specifically because this catch's only real-world trigger is a developer forgetting to
        // fill in anchorDate, and a silent no-op with no trace at all would be worse than a
        // one-line signal in logcat.
        Log.e("HabitSeeding", "Failed to seed C++ Weekly instance — row unavailable until fixed", e)
    }
    dao.insertIfAbsent(
        HabitInstance(
            id = LEGO_KIT_HABIT_INSTANCE_ID,
            kind = HabitKind.COUNTER.name,
            name = "Lego Kit",
            enabledDaysMask = 0b0011111, // Sun-Thu, matching Reading's mask
            notificationTitle = "Reminders",
            notificationBody = "Add one Lego kit today?",
            counterGoal = 1,
        )
    )
    dao.insertIfAbsent(
        HabitInstance(
            id = GARDEN_HABIT_INSTANCE_ID,
            kind = HabitKind.INTERVAL_DUE.name,
            name = "Water the garden",
            enabledDaysMask = 0b1111111, // unused by this kind (no day-of-week concept), all-days for consistency with other always-visible rows
            notificationTitle = "Reminders",
            notificationBody = "🌱 Time to water the garden!",
            counterGoal = null,
        )
    )
    intervalDueProgressDao.insertIfAbsent(
        IntervalDueProgress(habitInstanceId = GARDEN_HABIT_INSTANCE_ID, nextDueDate = LocalDate.now().toString())
    )
}
