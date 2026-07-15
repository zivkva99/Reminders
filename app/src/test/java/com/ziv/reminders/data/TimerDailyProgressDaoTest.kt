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
class TimerDailyProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.timerDailyProgressDao().getByDate(1L, "2026-07-15"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val progress = TimerDailyProgress(
            habitInstanceId = 1L, date = "2026-07-15", targetSeconds = 900,
            remainingSeconds = 900, completed = false, completedAt = null,
            activeSessionStartedAt = null,
        )
        db.timerDailyProgressDao().upsert(progress)

        assertEquals(progress, db.timerDailyProgressDao().getByDate(1L, "2026-07-15"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.timerDailyProgressDao().upsert(
            TimerDailyProgress(1L, "2026-07-15", 900, 900, false, null, null)
        )
        db.timerDailyProgressDao().upsert(
            TimerDailyProgress(1L, "2026-07-15", 900, 0, true, 123L, null)
        )

        val loaded = db.timerDailyProgressDao().getByDate(1L, "2026-07-15")
        assertEquals(0, loaded?.remainingSeconds)
        assertEquals(true, loaded?.completed)
        db.close()
    }

    @Test
    fun getCompletedDates_returnsOnlyCompletedRowsForThatInstance() = runTest {
        val db = newDb()
        db.timerDailyProgressDao().upsert(TimerDailyProgress(1L, "2026-07-13", 900, 0, true, 1L, null))
        db.timerDailyProgressDao().upsert(TimerDailyProgress(1L, "2026-07-14", 900, 300, false, null, null))
        db.timerDailyProgressDao().upsert(TimerDailyProgress(2L, "2026-07-13", 900, 0, true, 1L, null)) // different instance

        assertEquals(listOf("2026-07-13"), db.timerDailyProgressDao().getCompletedDates(1L))
        db.close()
    }

    @Test
    fun getActiveSessions_returnsRowsWithANonNullActiveSessionStartedAt_acrossAllInstances() = runTest {
        val db = newDb()
        db.timerDailyProgressDao().upsert(TimerDailyProgress(1L, "2026-07-15", 900, 600, false, null, 1_000L))
        db.timerDailyProgressDao().upsert(TimerDailyProgress(2L, "2026-07-14", 900, 900, false, null, null)) // not active
        db.timerDailyProgressDao().upsert(TimerDailyProgress(3L, "2026-07-15", 900, 300, false, null, 2_000L))

        val active = db.timerDailyProgressDao().getActiveSessions()
        assertEquals(setOf(1L, 3L), active.map { it.habitInstanceId }.toSet())
        db.close()
    }
}
