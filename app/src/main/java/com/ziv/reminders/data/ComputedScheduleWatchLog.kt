package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per watch event (one tap of "mark next watched") — the full per-episode watch-date log
 * this kind originally deferred (see the Scope Revision section below the CEO Phase 1 header for
 * why it's now in scope). Mirrors ScheduleCursorDailyProgress's per-date *role* (it's the streak
 * signal HabitStats.currentStreak consumes) but ReadingSessionLog's *shape* (autoincrementing
 * surrogate id, append-only), not a per-(habitInstanceId, date) upsert row: a single calendar day
 * can contain more than one watch event (catching up 2 backlog episodes in one sitting advances
 * nextItemNumber by 1 per tap — see ComputedScheduleRepository.markNextWatched — so 2 taps in one
 * day is a real, expected case, not an edge case to collapse away). Distinct from
 * ComputedScheduleProgress.nextItemNumber (the running position) — this table is purely an
 * append-only history for streak/heatmap purposes and is never read to derive `dueCount`/
 * `isDueToday`/`nextItemNumber` itself.
 *
 * Indexed on (habitInstanceId, date), same reasoning as ReadingSessionLog's own index: the only
 * real query this table serves (getWatchedDates) filters on habitInstanceId; the date column
 * being part of the same index costs nothing and keeps the shape consistent with
 * ReadingSessionLog's precedent. Declared here AND created by the matching SQL in MIGRATION_6_7 —
 * Room validates the two against each other at build time.
 */
@Entity(tableName = "computed_schedule_watch_log", indices = [Index(value = ["habitInstanceId", "date"])])
data class ComputedScheduleWatchLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitInstanceId: Long,
    val date: String,
    val episodeNumber: Int,
)
