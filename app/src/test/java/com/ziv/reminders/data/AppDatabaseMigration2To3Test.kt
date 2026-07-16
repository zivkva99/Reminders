package com.ziv.reminders.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigration2To3Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate2To3_preservesExistingRows_andAddsScheduleCursorTables() {
        // Seed a v2 database with real Exercise + Reading data, exactly as an already-installed
        // app (post-Plan-2) would have.
        helper.createDatabase(TEST_DB_NAME, 2).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (1, 'COUNTER', 'Exercise', 127, 't', 'b', 5, NULL)"
            )
            execSQL(
                "INSERT INTO timer_daily_progress (habitInstanceId, date, targetSeconds, remainingSeconds, completed, completedAt, activeSessionStartedAt) " +
                    "VALUES (2, '2026-07-15', 900, 0, 1, 123, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, AppDatabase.MIGRATION_2_3)

        migrated.query("SELECT name FROM habit_instance WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Exercise", cursor.getString(0))
        }
        migrated.query("SELECT completed FROM timer_daily_progress WHERE habitInstanceId = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM schedule_cursor_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM schedule_cursor_daily_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
