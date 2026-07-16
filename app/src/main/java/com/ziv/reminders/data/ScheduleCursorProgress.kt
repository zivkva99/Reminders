package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single row per habit instance. cursorIndex is the index of the next unread entry in the
 * bundled schedule asset (see ScheduleEntry.kt) — mirrors ReadBook's BibleReadingProgress. The
 * schedule's dates never move; falling behind means catching up one entry at a time, never
 * skipping ahead to "today's" entry.
 */
@Entity(tableName = "schedule_cursor_progress")
data class ScheduleCursorProgress(
    @PrimaryKey val habitInstanceId: Long,
    val cursorIndex: Int,
)
