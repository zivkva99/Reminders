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
class ScheduleCursorProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByInstance_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.scheduleCursorProgressDao().getByInstance(3L))
        db.close()
    }

    @Test
    fun upsert_thenGetByInstance_returnsTheRow() = runTest {
        val db = newDb()
        db.scheduleCursorProgressDao().upsert(ScheduleCursorProgress(habitInstanceId = 3L, cursorIndex = 5))

        assertEquals(ScheduleCursorProgress(3L, 5), db.scheduleCursorProgressDao().getByInstance(3L))
        db.close()
    }

    @Test
    fun upsert_sameInstance_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.scheduleCursorProgressDao().upsert(ScheduleCursorProgress(3L, cursorIndex = 5))
        db.scheduleCursorProgressDao().upsert(ScheduleCursorProgress(3L, cursorIndex = 6))

        assertEquals(6, db.scheduleCursorProgressDao().getByInstance(3L)?.cursorIndex)
        db.close()
    }
}
