package com.ziv.reminders.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitInstanceDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getById_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.habitInstanceDao().getById(1L))
        db.close()
    }

    @Test
    fun insertIfAbsent_thenGetById_returnsTheRow() = runTest {
        val db = newDb()
        val instance = HabitInstance(
            id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
            notificationTitle = "Reminders", notificationBody = "Don't forget your exercises today!",
            counterGoal = 5,
        )
        db.habitInstanceDao().insertIfAbsent(instance)

        val loaded = db.habitInstanceDao().getById(1L)
        assertEquals(instance, loaded)
        db.close()
    }

    @Test
    fun insertIfAbsent_rowAlreadyExists_doesNotOverwrite() = runTest {
        val db = newDb()
        val original = HabitInstance(
            id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
            notificationTitle = "Reminders", notificationBody = "original", counterGoal = 5,
        )
        db.habitInstanceDao().insertIfAbsent(original)
        db.habitInstanceDao().insertIfAbsent(original.copy(notificationBody = "changed"))

        assertEquals("original", db.habitInstanceDao().getById(1L)?.notificationBody)
        db.close()
    }

    @Test
    fun getAll_returnsEveryInsertedInstance() = runTest {
        val db = newDb()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        assertEquals(1, db.habitInstanceDao().getAll().size)
        db.close()
    }
}
