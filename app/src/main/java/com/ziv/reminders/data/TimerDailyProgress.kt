package com.ziv.reminders.data

import androidx.room.Entity

/**
 * Mirrors ReadBook's DailyProgress: a non-null activeSessionStartedAt with no matching Stop by
 * next app launch means the process died mid-session — RemindersApp's startup self-heal calls
 * TimerHabitRepository.reconcileCrashedSessions() to close these out (see Task 8).
 */
@Entity(tableName = "timer_daily_progress", primaryKeys = ["habitInstanceId", "date"])
data class TimerDailyProgress(
    val habitInstanceId: Long,
    val date: String,
    val targetSeconds: Int,
    val remainingSeconds: Int,
    val completed: Boolean,
    val completedAt: Long?,
    val activeSessionStartedAt: Long?,
)
