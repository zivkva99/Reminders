package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * kind is stored as a plain String (HabitKind.name), not a Room-mapped enum column — so a
 * future kind's migration only needs a data INSERT, never a schema change to this column.
 * counterGoal/timerTargetSeconds/anchorItemNumber/anchorDate/intervalDays are nullable per-kind
 * config columns; each new kind adds its own nullable trailing column the same way (a defaulted
 * trailing param, so every existing positional HabitInstance(...) call site keeps compiling
 * unmodified).
 *
 * anchorItemNumber/anchorDate/intervalDays are ComputedSchedule's per-instance anchor
 * configuration (e.g. "episode 542 released on this date, every 7 days thereafter") — fixed at
 * seed time, distinct from ComputedScheduleProgress.nextItemNumber (the one thing that actually
 * changes as the user taps through episodes). anchorDate is stored as ISO-8601 text
 * ("yyyy-MM-dd", LocalDate.toString()'s default format), matching every other date-as-TEXT
 * column in this codebase (e.g. CounterDailyProgress.date).
 */
@Entity(tableName = "habit_instance")
data class HabitInstance(
    @PrimaryKey val id: Long,
    val kind: String,
    val name: String,
    val enabledDaysMask: Int,
    val notificationTitle: String,
    val notificationBody: String,
    val counterGoal: Int?,
    val timerTargetSeconds: Int? = null,
    val anchorItemNumber: Int? = null,
    val anchorDate: String? = null,
    val intervalDays: Int? = null,
)
