package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TimerDailyProgressDao {
    @Query("SELECT * FROM timer_daily_progress WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun getByDate(habitInstanceId: Long, date: String): TimerDailyProgress?

    @Upsert
    suspend fun upsert(progress: TimerDailyProgress)

    @Query("SELECT date FROM timer_daily_progress WHERE habitInstanceId = :habitInstanceId AND completed = 1")
    suspend fun getCompletedDates(habitInstanceId: Long): List<String>

    // No habitInstanceId filter — RemindersApp's startup self-heal reconciles every instance's
    // dangling session in one pass, not one call per instance (see TimerHabitRepository, Task 3).
    @Query("SELECT * FROM timer_daily_progress WHERE activeSessionStartedAt IS NOT NULL")
    suspend fun getActiveSessions(): List<TimerDailyProgress>
}
