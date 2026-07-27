package com.ziv.reminders.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitSeedingTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun ensureHabitsSeeded_seedsGardenInstance_dueToday() = runTest {
        val db = newDb()
        val today = LocalDate.now()

        ensureHabitsSeeded(db.habitInstanceDao(), db.computedScheduleProgressDao(), db.intervalDueProgressDao())

        val instance = db.habitInstanceDao().getById(GARDEN_HABIT_INSTANCE_ID)
        assertEquals("Water the garden", instance?.name)
        assertEquals(HabitKind.INTERVAL_DUE.name, instance?.kind)

        val progress = db.intervalDueProgressDao().getByInstance(GARDEN_HABIT_INSTANCE_ID)
        assertEquals(today.toString(), progress?.nextDueDate)
        db.close()
    }

    @Test
    fun ensureHabitsSeeded_calledTwice_doesNotResetAnAlreadyAdvancedDueDate() = runTest {
        // Regression test (Eng review finding): re-running seeding on every app restart must
        // never reset a real, already-advanced due date back to "today".
        val db = newDb()
        ensureHabitsSeeded(db.habitInstanceDao(), db.computedScheduleProgressDao(), db.intervalDueProgressDao())
        db.intervalDueProgressDao().upsert(IntervalDueProgress(GARDEN_HABIT_INSTANCE_ID, nextDueDate = "2026-08-15"))

        ensureHabitsSeeded(db.habitInstanceDao(), db.computedScheduleProgressDao(), db.intervalDueProgressDao())

        val progress = db.intervalDueProgressDao().getByInstance(GARDEN_HABIT_INSTANCE_ID)
        assertEquals("2026-08-15", progress?.nextDueDate)
        db.close()
    }
}
