package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CounterDailyProgressDao {
    @Query("SELECT * FROM counter_daily_progress WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun getByDate(habitInstanceId: Long, date: String): CounterDailyProgress?

    @Upsert
    suspend fun upsert(progress: CounterDailyProgress)

    @Query("SELECT date FROM counter_daily_progress WHERE habitInstanceId = :habitInstanceId AND completed = 1")
    suspend fun getCompletedDates(habitInstanceId: Long): List<String>
}
