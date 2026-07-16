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
class AppDatabaseMigration3To4Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate3To4_preservesExistingRows_andAddsEvaluatorEscalationTable() {
        helper.createDatabase(TEST_DB_NAME, 3).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (2, 'TIMER', 'Reading', 31, 't', 'b', NULL, 900)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 4, true, AppDatabase.MIGRATION_3_4)

        migrated.query("SELECT name FROM habit_instance WHERE id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Reading", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM evaluator_escalation").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
