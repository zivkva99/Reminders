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
class AppDatabaseMigration7To8Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate7To8_preservesExistingRows_andAddsIntervalDueTables() {
        helper.createDatabase(TEST_DB_NAME, 7).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal) " +
                    "VALUES (1, 'COUNTER', 'Exercise', 127, 't', 'b', 5)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 8, true, AppDatabase.MIGRATION_7_8)

        migrated.query("SELECT name FROM habit_instance WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Exercise", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM interval_due_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM interval_due_log").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
