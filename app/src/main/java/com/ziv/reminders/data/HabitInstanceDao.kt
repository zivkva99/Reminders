package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HabitInstanceDao {
    @Query("SELECT * FROM habit_instance")
    suspend fun getAll(): List<HabitInstance>

    @Query("SELECT * FROM habit_instance WHERE id = :id")
    suspend fun getById(id: Long): HabitInstance?

    // IGNORE on primary-key conflict: calling this every app startup is idempotent seeding,
    // not an update path — see ensureHabitsSeeded in HabitSeeding.kt.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(instance: HabitInstance)
}
