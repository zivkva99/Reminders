package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per Reading start/stop segment — the session log ReadBook's own ReadingSession table
 * kept and Reminders' timestamp-delta TimerDailyProgress never needed for daily state, but
 * which the Activity screen's Reading section requires. Written once per completed segment
 * (TimerHabitRepository.finishSession), read/deleted only via an explicit user action from the
 * Activity screen's day-edit dialog (Task 6). Keyed by an autoincrementing surrogate id (the
 * first entity in this codebase to use one) since, unlike every other table here, a session
 * log has no natural composite business key — multiple sessions can share the same
 * (habitInstanceId, date).
 *
 * Indexed on (habitInstanceId, date) — every real query against this table (getForDate) filters
 * on exactly that pair, never the surrogate id alone; without this index each lookup is a full
 * table scan. The index must be declared here AND created by the matching SQL in MIGRATION_5_6
 * below — Room validates the migrated schema against this annotation at build time and throws
 * if the two ever drift apart.
 */
@Entity(tableName = "reading_session_log", indices = [Index(value = ["habitInstanceId", "date"])])
data class ReadingSessionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitInstanceId: Long,
    val date: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
)
