package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ScheduleCursorProgressDao {
    @Query("SELECT * FROM schedule_cursor_progress WHERE habitInstanceId = :habitInstanceId")
    suspend fun getByInstance(habitInstanceId: Long): ScheduleCursorProgress?

    @Upsert
    suspend fun upsert(progress: ScheduleCursorProgress)
}
