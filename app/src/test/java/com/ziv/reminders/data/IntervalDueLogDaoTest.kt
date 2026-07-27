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
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IntervalDueLogDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.intervalDueLogDao().getByDate(6L, "2026-07-27"))
        db.close()
    }

    @Test
    fun insert_thenGetByDate_returnsARow() = runTest {
        val db = newDb()
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-27"))

        assertNotNull(db.intervalDueLogDao().getByDate(6L, "2026-07-27"))
        db.close()
    }

    @Test
    fun insert_sameInstanceAndDateTwice_keepsBothRows() = runTest {
        // Append-only log, not an upsert table — watering twice in one day isn't precluded by
        // the spec (see design doc Architecture), so both inserts must survive as separate rows.
        val db = newDb()
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-27"))
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-27"))

        assertEquals(2, db.intervalDueLogDao().getAllForInstance(6L).size)
        db.close()
    }

    @Test
    fun getAllForInstance_returnsOnlyThatInstancesRows_mostRecentDateFirst() = runTest {
        val db = newDb()
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-20"))
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-27"))
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 9L, date = "2026-07-27")) // different instance

        val dates = db.intervalDueLogDao().getAllForInstance(6L).map { it.date }
        assertEquals(listOf("2026-07-27", "2026-07-20"), dates)
        db.close()
    }
}
