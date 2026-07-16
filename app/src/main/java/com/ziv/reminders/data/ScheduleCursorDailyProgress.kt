package com.ziv.reminders.data

import androidx.room.Entity

/**
 * Tracks whether at least one schedule entry was marked read on a given calendar day — this is
 * the streak signal (see the design doc: "counts consecutive calendar days with at least one
 * chapter marked read... falling behind doesn't itself break the streak"), distinct from
 * cursorIndex's running position through the whole schedule. Mirrors counter_daily_progress's
 * per-day shape.
 */
@Entity(tableName = "schedule_cursor_daily_progress", primaryKeys = ["habitInstanceId", "date"])
data class ScheduleCursorDailyProgress(
    val habitInstanceId: Long,
    val date: String,
    val entriesMarkedRead: Int,
    val completed: Boolean,
)
