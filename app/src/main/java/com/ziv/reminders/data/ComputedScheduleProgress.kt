package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single row per habit instance — the mutable running position, analogous to
 * ScheduleCursorProgress.cursorIndex. Distinct from the instance's own anchorItemNumber/
 * anchorDate/intervalDays columns on HabitInstance (fixed config, set once at seed time) —
 * nextItemNumber is the only thing that changes as the user taps through episodes.
 */
@Entity(tableName = "computed_schedule_progress")
data class ComputedScheduleProgress(
    @PrimaryKey val habitInstanceId: Long,
    val nextItemNumber: Int,
)
