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
class ScheduleCursorDailyProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.scheduleCursorDailyProgressDao().getByDate(3L, "2026-07-16"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val progress = ScheduleCursorDailyProgress(habitInstanceId = 3L, date = "2026-07-16", entriesMarkedRead = 1, completed = true)
        db.scheduleCursorDailyProgressDao().upsert(progress)

        assertEquals(progress, db.scheduleCursorDailyProgressDao().getByDate(3L, "2026-07-16"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(3L, "2026-07-16", 1, true))
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(3L, "2026-07-16", 2, true))

        assertEquals(2, db.scheduleCursorDailyProgressDao().getByDate(3L, "2026-07-16")?.entriesMarkedRead)
        db.close()
    }

    @Test
    fun getCompletedDates_returnsOnlyCompletedRowsForThatInstance() = runTest {
        val db = newDb()
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(3L, "2026-07-14", 1, true))
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(3L, "2026-07-15", 0, false))
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(9L, "2026-07-14", 1, true)) // different instance

        assertEquals(listOf("2026-07-14"), db.scheduleCursorDailyProgressDao().getCompletedDates(3L))
        db.close()
    }
}
