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
class AppDatabaseMigration5To6Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate5To6_preservesExistingRows_andAddsReadingSessionLogTable() {
        helper.createDatabase(TEST_DB_NAME, 5).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (2, 'TIMER', 'Reading', 31, 't', 'b', NULL, 900)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 6, true, AppDatabase.MIGRATION_5_6)

        migrated.query("SELECT name FROM habit_instance WHERE id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Reading", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM reading_session_log").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_reading_session_log_habitInstanceId_date'").use { cursor ->
            assertTrue(cursor.moveToFirst(), "expected index_reading_session_log_habitInstanceId_date to exist after migration")
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test-5-6"
    }
}
