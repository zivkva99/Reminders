package com.ziv.reminders.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IntervalDueProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByInstance_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.intervalDueProgressDao().getByInstance(6L))
        db.close()
    }

    @Test
    fun upsert_thenGetByInstance_returnsTheRow() = runTest {
        val db = newDb()
        db.intervalDueProgressDao().upsert(IntervalDueProgress(habitInstanceId = 6L, nextDueDate = "2026-07-27"))

        assertEquals(IntervalDueProgress(6L, "2026-07-27"), db.intervalDueProgressDao().getByInstance(6L))
        db.close()
    }

    @Test
    fun upsert_sameInstance_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.intervalDueProgressDao().upsert(IntervalDueProgress(6L, nextDueDate = "2026-07-27"))
        db.intervalDueProgressDao().upsert(IntervalDueProgress(6L, nextDueDate = "2026-08-01"))

        assertEquals("2026-08-01", db.intervalDueProgressDao().getByInstance(6L)?.nextDueDate)
        db.close()
    }

    @Test
    fun insertIfAbsent_noRow_insertsIt() = runTest {
        val db = newDb()
        db.intervalDueProgressDao().insertIfAbsent(IntervalDueProgress(6L, nextDueDate = "2026-07-27"))

        assertEquals("2026-07-27", db.intervalDueProgressDao().getByInstance(6L)?.nextDueDate)
        db.close()
    }

    @Test
    fun insertIfAbsent_rowAlreadyExists_leavesItUntouched() = runTest {
        // Eng review finding: this is the regression insertIfAbsent exists to prevent — re-running
        // seeding (every app restart, via RemindersApp.onCreate) must never reset a real,
        // already-advanced due date back to "today." Mirrors
        // ComputedScheduleProgressDaoTest.insertIfAbsent_rowAlreadyExists_leavesItUntouched exactly.
        val db = newDb()
        db.intervalDueProgressDao().upsert(IntervalDueProgress(6L, nextDueDate = "2026-08-15"))

        db.intervalDueProgressDao().insertIfAbsent(IntervalDueProgress(6L, nextDueDate = "2026-07-27"))

        assertEquals("2026-08-15", db.intervalDueProgressDao().getByInstance(6L)?.nextDueDate)
        db.close()
    }
}
