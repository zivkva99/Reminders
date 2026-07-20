package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReadingSessionLogDao {
    @Insert
    suspend fun insert(session: ReadingSessionLog): Long

    @Query("SELECT * FROM reading_session_log WHERE habitInstanceId = :habitInstanceId AND date = :date ORDER BY startedAt ASC")
    suspend fun getForDate(habitInstanceId: Long, date: String): List<ReadingSessionLog>

    @Delete
    suspend fun delete(session: ReadingSessionLog)

    // Feeds TimerHabitRepository.resetToday (Task 2) – deletes every session logged for a
    // given instance/date in one statement, rather than requiring the caller to fetch-then-
    // delete each row individually via the single-row delete() above.
    @Query("DELETE FROM reading_session_log WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun deleteForDate(habitInstanceId: Long, date: String)
}
