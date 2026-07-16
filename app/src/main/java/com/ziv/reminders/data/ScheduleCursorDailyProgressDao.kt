package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ScheduleCursorDailyProgressDao {
    @Query("SELECT * FROM schedule_cursor_daily_progress WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun getByDate(habitInstanceId: Long, date: String): ScheduleCursorDailyProgress?

    @Upsert
    suspend fun upsert(progress: ScheduleCursorDailyProgress)

    @Query("SELECT date FROM schedule_cursor_daily_progress WHERE habitInstanceId = :habitInstanceId AND completed = 1")
    suspend fun getCompletedDates(habitInstanceId: Long): List<String>
}
