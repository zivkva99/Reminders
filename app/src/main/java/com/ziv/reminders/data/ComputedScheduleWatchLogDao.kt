package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ComputedScheduleWatchLogDao {
    @Insert
    suspend fun insert(entry: ComputedScheduleWatchLog): Long

    // DISTINCT at the SQL layer, not left to the caller — see ComputedScheduleWatchLogDaoTest's
    // insert_twoEventsSameDay test for why: HabitStats.currentStreak/parseDates operate over a
    // Set<LocalDate> (one entry per calendar day), so every caller of this DAO must get the same
    // de-duplicated contract, matching ScheduleCursorDailyProgressDao.getCompletedDates and
    // CounterDailyProgressDao.getCompletedDates's own one-row-per-day shape (those tables enforce
    // it via a composite primary key instead, since they're upsert tables, not an append-only
    // log — DISTINCT is this table's equivalent guarantee for an append-only shape).
    @Query("SELECT DISTINCT date FROM computed_schedule_watch_log WHERE habitInstanceId = :habitInstanceId")
    suspend fun getWatchedDates(habitInstanceId: Long): List<String>
}
