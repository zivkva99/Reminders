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
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExerciseSubCounterProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_PUSHUP, "2026-07-19"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val row = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 8)
        db.exerciseSubCounterProgressDao().upsert(row)

        assertEquals(row, db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_PUSHUP, "2026-07-19"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 5))
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 9))

        assertEquals(9, db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_PUSHUP, "2026-07-19")?.count)
        db.close()
    }

    @Test
    fun upsert_differentExerciseKey_doesNotAffectAnotherKeysRowForTheSameDate() = runTest {
        val db = newDb()
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 5))
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_SITUP, "2026-07-19", 12))

        // This is the regression test for the clobber bug the design doc identifies:
        // upserting one exerciseKey's row must never touch another exerciseKey's row for
        // the same date. One row per (exerciseKey, date) — not one row per date with
        // multiple columns — makes this true by construction.
        assertEquals(5, db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_PUSHUP, "2026-07-19")?.count)
        assertEquals(12, db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_SITUP, "2026-07-19")?.count)
        db.close()
    }

    @Test
    fun getAllForDate_returnsOnlyRowsForThatDate() = runTest {
        val db = newDb()
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 5))
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_SITUP, "2026-07-19", 12))
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-18", 3))

        val rows = db.exerciseSubCounterProgressDao().getAllForDate("2026-07-19")
        assertEquals(2, rows.size)
        db.close()
    }
}
