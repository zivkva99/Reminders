package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ExerciseSubCounterProgressDao {
    @Query("SELECT * FROM exercise_sub_counter_progress WHERE exerciseKey = :exerciseKey AND date = :date")
    suspend fun getByDate(exerciseKey: String, date: String): ExerciseSubCounterProgress?

    @Upsert
    suspend fun upsert(progress: ExerciseSubCounterProgress)

    @Query("SELECT * FROM exercise_sub_counter_progress WHERE date = :date")
    suspend fun getAllForDate(date: String): List<ExerciseSubCounterProgress>
}
