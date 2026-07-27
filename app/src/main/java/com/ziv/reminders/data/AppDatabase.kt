package com.ziv.reminders.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HabitInstance::class, CounterDailyProgress::class, TimerDailyProgress::class,
        ScheduleCursorProgress::class, ScheduleCursorDailyProgress::class,
        EvaluatorEscalation::class, ExerciseSubCounterProgress::class, ReadingSessionLog::class,
        ComputedScheduleProgress::class, ComputedScheduleWatchLog::class,
        IntervalDueProgress::class, IntervalDueLog::class,
    ],
    version = 8,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitInstanceDao(): HabitInstanceDao
    abstract fun counterDailyProgressDao(): CounterDailyProgressDao
    abstract fun timerDailyProgressDao(): TimerDailyProgressDao
    abstract fun scheduleCursorProgressDao(): ScheduleCursorProgressDao
    abstract fun scheduleCursorDailyProgressDao(): ScheduleCursorDailyProgressDao
    abstract fun evaluatorEscalationDao(): EvaluatorEscalationDao
    abstract fun exerciseSubCounterProgressDao(): ExerciseSubCounterProgressDao
    abstract fun readingSessionLogDao(): ReadingSessionLogDao
    abstract fun computedScheduleProgressDao(): ComputedScheduleProgressDao
    abstract fun computedScheduleWatchLogDao(): ComputedScheduleWatchLogDao
    abstract fun intervalDueProgressDao(): IntervalDueProgressDao
    abstract fun intervalDueLogDao(): IntervalDueLogDao

    companion object {
        /** Adds Timer-with-duration kind support — see Plan 2. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_instance ADD COLUMN timerTargetSeconds INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timer_daily_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                        "`targetSeconds` INTEGER NOT NULL, `remainingSeconds` INTEGER NOT NULL, " +
                        "`completed` INTEGER NOT NULL, `completedAt` INTEGER, " +
                        "`activeSessionStartedAt` INTEGER, PRIMARY KEY(`habitInstanceId`, `date`))"
                )
            }
        }

        /** Adds Schedule-cursor kind support: a per-instance running cursor position table, plus
         * its own daily-progress table for streak tracking (mirrors timer_daily_progress's
         * shape). No new habit_instance column — unlike Counter/Timer, this kind's only "config"
         * is the shared bundled schedule asset, not per-instance data. Never
         * fallbackToDestructiveMigration() — see Global Constraints. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_cursor_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `cursorIndex` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitInstanceId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_cursor_daily_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                        "`entriesMarkedRead` INTEGER NOT NULL, `completed` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitInstanceId`, `date`))"
                )
            }
        }

        /** Adds the cross-habit evaluator's escalation-tracking table — a per-day flag,
         * no new habit_instance column. Never fallbackToDestructiveMigration() — see
         * Global Constraints. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `evaluator_escalation` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                        "`escalated` INTEGER NOT NULL, PRIMARY KEY(`habitInstanceId`, `date`))"
                )
            }
        }

        /** Adds the Exercise sub-counter tracking table — one row per (exerciseKey,
         * date), never one row per date with multiple columns (see this file's
         * ExerciseSubCounterProgress doc comment for why). Never
         * fallbackToDestructiveMigration() — see Global Constraints. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_sub_counter_progress` (" +
                        "`exerciseKey` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                        "`count` INTEGER NOT NULL, PRIMARY KEY(`exerciseKey`, `date`))"
                )
            }
        }

        /** Adds the Reading per-session log table — one row per start/stop segment, the first
         * table in this codebase with an autoincrement surrogate key instead of a composite
         * business key (see ReadingSessionLog's doc comment for why). Never
         * fallbackToDestructiveMigration() — see Global Constraints. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_session_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitInstanceId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL)"
                )
                // Must match ReadingSessionLog's `indices = [Index(value = ["habitInstanceId", "date"])]`
                // exactly (including Room's default index-name convention) or schema validation
                // fails at app startup with a migration-mismatch exception.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reading_session_log_habitInstanceId_date` " +
                        "ON `reading_session_log` (`habitInstanceId`, `date`)"
                )
            }
        }

        /** Adds ComputedSchedule kind support: 3 new nullable anchor-config columns on
         * habit_instance (mirrors how timerTargetSeconds was added to the same table in
         * MIGRATION_1_2 — no separate config table needed for scalar per-instance values), a
         * new single-row-per-instance position table (mirrors schedule_cursor_progress's
         * shape), and a new append-only per-watch-event log table (mirrors reading_session_log's
         * shape — added per the Scope Revision below the CEO Phase 1 header; this table did not
         * exist in the plan's original draft of this migration, but is folded into this same
         * MIGRATION_6_7 rather than a new MIGRATION_7_8 since v7 was never shipped). Never
         * fallbackToDestructiveMigration() — see Global Constraints. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_instance ADD COLUMN anchorItemNumber INTEGER")
                db.execSQL("ALTER TABLE habit_instance ADD COLUMN anchorDate TEXT")
                db.execSQL("ALTER TABLE habit_instance ADD COLUMN intervalDays INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `computed_schedule_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `nextItemNumber` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitInstanceId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `computed_schedule_watch_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitInstanceId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, `episodeNumber` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_computed_schedule_watch_log_habitInstanceId_date` " +
                        "ON `computed_schedule_watch_log` (`habitInstanceId`, `date`)"
                )
            }
        }

        /** Adds INTERVAL_DUE kind support: a per-instance running due-date table (mirrors
         * schedule_cursor_progress/computed_schedule_progress's single-row shape) plus an
         * append-only completion log (mirrors reading_session_log/computed_schedule_watch_log's
         * autoincrement shape). No new habit_instance column — this kind's only "config" is the
         * seeded initial due date, written directly into interval_due_progress at seed time (see
         * Global Constraints — do not confuse with the existing intervalDays column, which is
         * ComputedSchedule's unrelated fixed cadence). Never fallbackToDestructiveMigration(). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interval_due_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `nextDueDate` TEXT NOT NULL, " +
                        "PRIMARY KEY(`habitInstanceId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interval_due_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitInstanceId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL)"
                )
                // Matches IntervalDueLogDao's WHERE habitInstanceId = ... AND date = ... access
                // pattern — same index shape as reading_session_log/computed_schedule_watch_log.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_interval_due_log_habitInstanceId_date` " +
                        "ON `interval_due_log` (`habitInstanceId`, `date`)"
                )
            }
        }
    }
}
