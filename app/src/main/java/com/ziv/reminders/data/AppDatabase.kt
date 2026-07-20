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
    ],
    version = 6,
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
    }
}
