package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeExerciseSubCounterProgressDao : ExerciseSubCounterProgressDao {
    val rows = mutableMapOf<Pair<String, String>, ExerciseSubCounterProgress>()
    override suspend fun getByDate(exerciseKey: String, date: String) = rows[exerciseKey to date]
    override suspend fun upsert(progress: ExerciseSubCounterProgress) { rows[progress.exerciseKey to progress.date] = progress }
    override suspend fun getAllForDate(date: String) = rows.values.filter { it.date == date }
    override suspend fun getLatestBefore(exerciseKey: String, date: String) = rows.values
        .filter { it.exerciseKey == exerciseKey && it.date < date }
        .maxByOrNull { it.date }
}

class SubCounterRepositoryTest {

    private val today = LocalDate.of(2026, 7, 19)

    @Test
    fun todayValue_noRow_defaultsToFive() = runTest {
        val repo = SubCounterRepository(FakeExerciseSubCounterProgressDao())
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun todayValue_existingRow_returnsStoredValue() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.toString(), 12)
        val repo = SubCounterRepository(dao)

        assertEquals(12, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun todayValue_noRowToday_carriesForwardMostRecentPriorDay() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.minusDays(1).toString()] =
            ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.minusDays(1).toString(), 2)
        val repo = SubCounterRepository(dao)

        assertEquals(2, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun todayValue_noRowToday_carriesForwardFromNearestPriorDay_notJustYesterday() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.minusDays(5).toString()] =
            ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.minusDays(5).toString(), 16)
        val repo = SubCounterRepository(dao)

        assertEquals(16, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun adjust_noRowToday_appliesDeltaOnTopOfCarriedForwardValue() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.minusDays(1).toString()] =
            ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.minusDays(1).toString(), 2)
        val repo = SubCounterRepository(dao)

        repo.adjust(EXERCISE_KEY_PUSHUP, today, +1)

        assertEquals(3, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
        // Yesterday's own row is untouched by today's adjustment.
        assertEquals(2, dao.rows[EXERCISE_KEY_PUSHUP to today.minusDays(1).toString()]?.count)
    }

    @Test
    fun valuesForDate_pastDateNoRows_returnsEmptyMap_neverDefaultsToFive() = runTest {
        val repo = SubCounterRepository(FakeExerciseSubCounterProgressDao())
        assertEquals(emptyMap(), repo.valuesForDate(today.minusDays(30)))
    }

    @Test
    fun adjust_incrementsFromDefault_andClampsAtNinetyNine() = runTest {
        val repo = SubCounterRepository(FakeExerciseSubCounterProgressDao())
        repo.adjust(EXERCISE_KEY_PUSHUP, today, +1)
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT + 1, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun adjust_clampsAtZero_neverGoesNegative() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.toString(), 0)
        val repo = SubCounterRepository(dao)

        repo.adjust(EXERCISE_KEY_PUSHUP, today, -1)
        assertEquals(0, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun adjust_oneExerciseKey_doesNotAffectAnother() = runTest {
        val repo = SubCounterRepository(FakeExerciseSubCounterProgressDao())
        repo.adjust(EXERCISE_KEY_PUSHUP, today, +3)

        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT + 3, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT, repo.todayValue(EXERCISE_KEY_SITUP, today))
    }

    @Test
    fun valuesForDate_returnsMapKeyedByExerciseKey() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.toString(), 8)
        dao.rows[EXERCISE_KEY_SITUP to today.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_SITUP, today.toString(), 15)
        val repo = SubCounterRepository(dao)

        val values = repo.valuesForDate(today)
        assertEquals(mapOf(EXERCISE_KEY_PUSHUP to 8, EXERCISE_KEY_SITUP to 15), values)
    }
}
