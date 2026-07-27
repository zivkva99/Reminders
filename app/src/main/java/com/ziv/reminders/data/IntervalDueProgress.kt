package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single row per habit instance. nextDueDate is ISO-8601 text (LocalDate.toString()'s default
 * format), matching every other date-as-TEXT column in this codebase. Mirrors
 * ScheduleCursorProgress/ComputedScheduleProgress's single-row-per-instance shape, but stores a
 * due date instead of a cursor index/item number.
 */
@Entity(tableName = "interval_due_progress")
data class IntervalDueProgress(
    @PrimaryKey val habitInstanceId: Long,
    val nextDueDate: String,
)
