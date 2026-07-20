package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingSessionLogDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getForDate_noRows_returnsEmptyList() = runTest {
        val db = newDb()
        assertTrue(db.readingSessionLogDao().getForDate(2L, "2026-07-19").isEmpty())
        db.close()
    }

    @Test
    fun insert_thenGetForDate_returnsIt() = runTest {
        val db = newDb()
        val id = db.readingSessionLogDao().insert(
            ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 1000L, endedAt = 1900L, durationSeconds = 900)
        )

        val rows = db.readingSessionLogDao().getForDate(2L, "2026-07-19")
        assertEquals(1, rows.size)
        assertEquals(id, rows[0].id)
        assertEquals(900, rows[0].durationSeconds)
        db.close()
    }

    @Test
    fun insert_twoSessionsSameDate_returnsBothOrderedByStartTime() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 5000L, endedAt = 5500L, durationSeconds = 500))
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))

        val rows = db.readingSessionLogDao().getForDate(2L, "2026-07-19")
        assertEquals(listOf(1000L, 5000L), rows.map { it.startedAt })
        db.close()
    }

    @Test
    fun getForDate_differentDate_isExcluded() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-18", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))

        assertTrue(db.readingSessionLogDao().getForDate(2L, "2026-07-19").isEmpty())
        db.close()
    }

    @Test
    fun delete_removesTheRow() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))
        val row = db.readingSessionLogDao().getForDate(2L, "2026-07-19").single()

        db.readingSessionLogDao().delete(row)

        assertTrue(db.readingSessionLogDao().getForDate(2L, "2026-07-19").isEmpty())
        db.close()
    }

    @Test
    fun deleteForDate_removesAllRowsForThatInstanceAndDate() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 5000L, endedAt = 5500L, durationSeconds = 500))

        db.readingSessionLogDao().deleteForDate(2L, "2026-07-19")

        assertTrue(db.readingSessionLogDao().getForDate(2L, "2026-07-19").isEmpty())
        db.close()
    }

    @Test
    fun deleteForDate_doesNotAffectOtherDatesOrInstances() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-18", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 5L, date = "2026-07-19", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))

        db.readingSessionLogDao().deleteForDate(2L, "2026-07-19")

        assertEquals(1, db.readingSessionLogDao().getForDate(2L, "2026-07-18").size)
        assertEquals(1, db.readingSessionLogDao().getForDate(5L, "2026-07-19").size)
        db.close()
    }
}
