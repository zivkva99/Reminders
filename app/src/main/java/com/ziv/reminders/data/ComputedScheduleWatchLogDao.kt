package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Delete
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

    // Feeds ComputedScheduleRepository.undoMarkNextWatched — fetch-then-delete (via the plain
    // @Delete below), mirroring ReadingSessionLogDao's own delete(entity) precedent rather than a
    // raw subquery DELETE. Highest id wins ("most recent") since this table is an append-only log
    // with no other ordering column guaranteed monotonic (two taps in the same second would tie
    // on any timestamp column; autoincrement id can't).
    @Query("SELECT * FROM computed_schedule_watch_log WHERE habitInstanceId = :habitInstanceId AND date = :date ORDER BY id DESC LIMIT 1")
    suspend fun getMostRecentForDate(habitInstanceId: Long, date: String): ComputedScheduleWatchLog?

    @Delete
    suspend fun delete(entry: ComputedScheduleWatchLog)
}
