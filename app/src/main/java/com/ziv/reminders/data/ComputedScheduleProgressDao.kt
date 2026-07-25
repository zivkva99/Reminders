package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ComputedScheduleProgressDao {
    @Query("SELECT * FROM computed_schedule_progress WHERE habitInstanceId = :habitInstanceId")
    suspend fun getByInstance(habitInstanceId: Long): ComputedScheduleProgress?

    // IGNORE on conflict — used only at seed time (HabitSeeding.ensureHabitsSeeded) to write the
    // starting position exactly once. Unlike ScheduleCursorProgress (which safely defaults to
    // cursorIndex 0 when no row exists yet), this kind's starting nextItemNumber is real business
    // data (episode 543, not a universal default like 0), so it must be persisted explicitly and
    // must never be silently overwritten by a later app-startup reseed — hence a separate
    // insertIfAbsent from the mutable upsert below, mirroring HabitInstanceDao's own
    // insertIfAbsent/no-update-path precedent.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: ComputedScheduleProgress)

    @Upsert
    suspend fun upsert(progress: ComputedScheduleProgress)
}
