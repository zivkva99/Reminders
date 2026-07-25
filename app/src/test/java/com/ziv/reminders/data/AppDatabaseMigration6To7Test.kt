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
class AppDatabaseMigration6To7Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate6To7_preservesExistingRows_addsAnchorColumnsAndComputedScheduleTable() {
        // Seed a v6 database with a real pre-existing Tanakh row, exactly as an already-installed
        // app (post-Plan "reading-session-log") would have.
        helper.createDatabase(TEST_DB_NAME, 6).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (3, 'SCHEDULE_CURSOR', 'Tanakh', 31, 't', 'b', NULL, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, AppDatabase.MIGRATION_6_7)

        migrated.query("SELECT name FROM habit_instance WHERE id = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Tanakh", cursor.getString(0))
        }
        migrated.query("SELECT anchorItemNumber, anchorDate, intervalDays FROM habit_instance WHERE id = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0)) // anchorItemNumber — NULL for a pre-existing row
            assertTrue(cursor.isNull(1)) // anchorDate
            assertTrue(cursor.isNull(2)) // intervalDays
        }
        migrated.query("SELECT COUNT(*) FROM computed_schedule_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        // Added per the Scope Revision (see the section below the CEO Phase 1 header) — the
        // watch-log table did not exist in this migration's original draft.
        migrated.query("SELECT COUNT(*) FROM computed_schedule_watch_log").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test-6-7"
    }
}
