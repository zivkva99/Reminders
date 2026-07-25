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
class ComputedScheduleProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByInstance_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.computedScheduleProgressDao().getByInstance(4L))
        db.close()
    }

    @Test
    fun insertIfAbsent_thenGetByInstance_returnsTheSeededRow() = runTest {
        val db = newDb()
        db.computedScheduleProgressDao().insertIfAbsent(ComputedScheduleProgress(habitInstanceId = 4L, nextItemNumber = 543))

        assertEquals(ComputedScheduleProgress(4L, 543), db.computedScheduleProgressDao().getByInstance(4L))
        db.close()
    }

    @Test
    fun insertIfAbsent_rowAlreadyExists_leavesItUntouched() = runTest {
        val db = newDb()
        db.computedScheduleProgressDao().insertIfAbsent(ComputedScheduleProgress(4L, nextItemNumber = 543))
        db.computedScheduleProgressDao().upsert(ComputedScheduleProgress(4L, nextItemNumber = 550)) // simulate several taps

        db.computedScheduleProgressDao().insertIfAbsent(ComputedScheduleProgress(4L, nextItemNumber = 543)) // re-seed attempt

        assertEquals(550, db.computedScheduleProgressDao().getByInstance(4L)?.nextItemNumber)
        db.close()
    }

    @Test
    fun upsert_sameInstance_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.computedScheduleProgressDao().upsert(ComputedScheduleProgress(4L, 543))
        db.computedScheduleProgressDao().upsert(ComputedScheduleProgress(4L, 544))

        assertEquals(544, db.computedScheduleProgressDao().getByInstance(4L)?.nextItemNumber)
        db.close()
    }
}
