package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only completion log — one row per watering event, autoincrement surrogate key (mirrors
 * ReadingSessionLog/ComputedScheduleWatchLog's shape, not a per-day upsert table like
 * CounterDailyProgress/TimerDailyProgress). date is always the date markDone actually ran, never
 * the due date that triggered it (design doc's overdue-logging requirement) — always today's
 * date at the moment of the write, since markDone is only ever called with today() (see
 * IntervalDueRepository). Multiple rows for the same (habitInstanceId, date) are legal — this is
 * a log, not a uniqueness-enforcing table.
 *
 * indices must match MIGRATION_7_8's manually-created index (name and columns) exactly, or
 * Room's schema validation fails at runtime with a migration-mismatch exception — same lesson
 * ReadingSessionLog's own migration plan already documented.
 */
@Entity(tableName = "interval_due_log", indices = [Index(value = ["habitInstanceId", "date"])])
data class IntervalDueLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitInstanceId: Long,
    val date: String,
)
