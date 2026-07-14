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
class CounterDailyProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.counterDailyProgressDao().getByDate(1L, "2026-07-14"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val progress = CounterDailyProgress(habitInstanceId = 1L, date = "2026-07-14", count = 3, completed = false)
        db.counterDailyProgressDao().upsert(progress)

        assertEquals(progress, db.counterDailyProgressDao().getByDate(1L, "2026-07-14"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.counterDailyProgressDao().upsert(CounterDailyProgress(1L, "2026-07-14", 1, false))
        db.counterDailyProgressDao().upsert(CounterDailyProgress(1L, "2026-07-14", 5, true))

        val loaded = db.counterDailyProgressDao().getByDate(1L, "2026-07-14")
        assertEquals(5, loaded?.count)
        assertEquals(true, loaded?.completed)
        db.close()
    }

    @Test
    fun getCompletedDates_returnsOnlyCompletedRowsForThatInstance() = runTest {
        val db = newDb()
        db.counterDailyProgressDao().upsert(CounterDailyProgress(1L, "2026-07-12", 5, true))
        db.counterDailyProgressDao().upsert(CounterDailyProgress(1L, "2026-07-13", 2, false))
        db.counterDailyProgressDao().upsert(CounterDailyProgress(2L, "2026-07-12", 5, true)) // different instance

        assertEquals(listOf("2026-07-12"), db.counterDailyProgressDao().getCompletedDates(1L))
        db.close()
    }
}
