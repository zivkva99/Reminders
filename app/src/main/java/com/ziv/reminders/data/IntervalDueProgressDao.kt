package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface IntervalDueProgressDao {
    @Query("SELECT * FROM interval_due_progress WHERE habitInstanceId = :habitInstanceId")
    suspend fun getByInstance(habitInstanceId: Long): IntervalDueProgress?

    @Upsert
    suspend fun upsert(progress: IntervalDueProgress)

    // Used only at seed time (Task 5's ensureHabitsSeeded) to write the initial due-today row —
    // IGNORE on conflict means re-running seeding on every app restart never resets a real,
    // already-advanced due date back to "today." Mirrors ComputedScheduleProgressDao's identical
    // insertIfAbsent/upsert pair exactly (Eng review finding — see Interfaces note above).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: IntervalDueProgress)
}
