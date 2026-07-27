package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface IntervalDueLogDao {
    @Insert
    suspend fun insert(log: IntervalDueLog)

    @Query("SELECT * FROM interval_due_log WHERE habitInstanceId = :habitInstanceId AND date = :date LIMIT 1")
    suspend fun getByDate(habitInstanceId: Long, date: String): IntervalDueLog?

    @Query("SELECT * FROM interval_due_log WHERE habitInstanceId = :habitInstanceId ORDER BY date DESC, id DESC")
    suspend fun getAllForInstance(habitInstanceId: Long): List<IntervalDueLog>
}
