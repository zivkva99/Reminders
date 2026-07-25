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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComputedScheduleWatchLogDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getWatchedDates_noRows_returnsEmpty() = runTest {
        val db = newDb()
        assertEquals(emptyList(), db.computedScheduleWatchLogDao().getWatchedDates(4L))
        db.close()
    }

    @Test
    fun insert_thenGetWatchedDates_returnsTheDate() = runTest {
        val db = newDb()
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = "2026-07-21", episodeNumber = 543))

        assertEquals(listOf("2026-07-21"), db.computedScheduleWatchLogDao().getWatchedDates(4L))
        db.close()
    }

    @Test
    fun insert_twoEventsSameDay_getWatchedDates_returnsDateOnlyOnce() = runTest {
        // Same reasoning as ReadingSessionLog: a session log has no natural composite business
        // key (multiple watch events CAN share a date — e.g. catching up 2 backlog episodes in
        // one sitting), so this is an autoincrement-id append-only log, not a per-date upsert
        // table. getWatchedDates must still de-duplicate to one distinct date, or HabitStats'
        // streak math (which operates over a Set<LocalDate>, one entry per calendar day) would
        // silently be fed a date twice — harmless for a Set, but the DISTINCT belongs at the SQL
        // layer so every future caller of this DAO gets the same de-duplicated contract, not just
        // whichever caller happens to funnel the list through a Set first.
        val db = newDb()
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = "2026-08-04", episodeNumber = 544))
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = "2026-08-04", episodeNumber = 545))

        assertEquals(listOf("2026-08-04"), db.computedScheduleWatchLogDao().getWatchedDates(4L))
        db.close()
    }

    @Test
    fun getWatchedDates_onlyReturnsRowsForTheRequestedInstance() = runTest {
        val db = newDb()
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = "2026-07-21", episodeNumber = 543))
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 99L, date = "2026-07-22", episodeNumber = 1))

        assertEquals(listOf("2026-07-21"), db.computedScheduleWatchLogDao().getWatchedDates(4L))
        db.close()
    }
}
