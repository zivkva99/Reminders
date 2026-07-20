package com.ziv.reminders.ui.exercise

import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.EXERCISE_KEY_PUSHUP
import com.ziv.reminders.data.EXERCISE_KEY_SITUP
import com.ziv.reminders.data.EXERCISE_SUB_COUNTER_DEFAULT
import com.ziv.reminders.data.ExerciseDetailDataSource
import com.ziv.reminders.data.ExerciseSubCounterProgress
import com.ziv.reminders.data.ExerciseSubCounterProgressDao
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.ScheduleCursorDailyProgress
import com.ziv.reminders.data.ScheduleCursorDailyProgressDao
import com.ziv.reminders.data.ScheduleCursorProgress
import com.ziv.reminders.data.ScheduleCursorProgressDao
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.SubCounterRepository
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerDailyProgress
import com.ziv.reminders.data.TimerDailyProgressDao
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.engine.HabitEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeHabitInstanceDao : HabitInstanceDao {
    val rows = mutableMapOf<Long, HabitInstance>()
    override suspend fun getAll() = rows.values.toList()
    override suspend fun getById(id: Long) = rows[id]
    override suspend fun insertIfAbsent(instance: HabitInstance) { rows.putIfAbsent(instance.id, instance) }
}

private class FakeCounterDailyProgressDao : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: CounterDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

private class FakeExerciseSubCounterProgressDao : ExerciseSubCounterProgressDao {
    val rows = mutableMapOf<Pair<String, String>, ExerciseSubCounterProgress>()
    override suspend fun getByDate(exerciseKey: String, date: String) = rows[exerciseKey to date]
    override suspend fun upsert(progress: ExerciseSubCounterProgress) { rows[progress.exerciseKey to progress.date] = progress }
    override suspend fun getAllForDate(date: String) = rows.values.filter { it.date == date }
}

private class FakeTimerDailyProgressDao : TimerDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, TimerDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: TimerDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
    override suspend fun getActiveSessions() = rows.values.filter { it.activeSessionStartedAt != null }
}

private class FakeScheduleCursorProgressDao : ScheduleCursorProgressDao {
    override suspend fun getByInstance(habitInstanceId: Long): ScheduleCursorProgress? = null
    override suspend fun upsert(progress: ScheduleCursorProgress) {}
}

private class FakeScheduleCursorDailyProgressDao : ScheduleCursorDailyProgressDao {
    override suspend fun getByDate(habitInstanceId: Long, date: String): ScheduleCursorDailyProgress? = null
    override suspend fun upsert(progress: ScheduleCursorDailyProgress) {}
    override suspend fun getCompletedDates(habitInstanceId: Long) = emptyList<String>()
}

private class FakeExerciseDetailDataSource(
    private val instanceDao: FakeHabitInstanceDao,
    counterDao: FakeCounterDailyProgressDao,
    private val subCounterDao: FakeExerciseSubCounterProgressDao,
) : ExerciseDetailDataSource {
    override val habitInstanceDao = instanceDao
    override val counterHabitRepository = CounterHabitRepository(counterDao)
    override val habitEngine = HabitEngine(
        counterHabitRepository,
        TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
        ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), emptyList()),
    )
    override val subCounterRepository = SubCounterRepository(subCounterDao)
}

class ExerciseViewModelTest {

    // ExerciseViewModel's refresh()/increment()/adjustSubCounter() run on viewModelScope,
    // which lazily resolves Dispatchers.Main.immediate — unavailable on a plain JVM unit
    // test unless explicitly provided. UnconfinedTestDispatcher (not StandardTestDispatcher)
    // is required here specifically because this test's assertions read uiState.value
    // immediately after calling refresh()/increment()/etc., with no advanceUntilIdle() in
    // between: Unconfined runs the launched coroutine body eagerly, in-place, up to its
    // first real suspension point, which these fakes (no real IO/delay) never hit.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val exercise = HabitInstance(
        id = EXERCISE_HABIT_INSTANCE_ID, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    // Real LocalDate.now(), not a hardcoded literal — ExerciseViewModel.refresh() internally
    // calls LocalDate.now() itself (no injectable clock), so a fixed literal here would silently
    // mismatch the seeded fake DB rows the day real wall-clock time moves past it.
    private val today = LocalDate.now()

    private fun newViewModel(
        instanceDao: FakeHabitInstanceDao = FakeHabitInstanceDao().apply { rows[exercise.id] = exercise },
        counterDao: FakeCounterDailyProgressDao = FakeCounterDailyProgressDao(),
        subCounterDao: FakeExerciseSubCounterProgressDao = FakeExerciseSubCounterProgressDao(),
    ): ExerciseViewModel = ExerciseViewModel(FakeExerciseDetailDataSource(instanceDao, counterDao, subCounterDao))

    @Test
    fun refresh_populatesStateFromCurrentData() = runTest {
        val counterDao = FakeCounterDailyProgressDao()
        counterDao.rows[exercise.id to today.toString()] = CounterDailyProgress(exercise.id, today.toString(), 3, false)
        val viewModel = newViewModel(counterDao = counterDao)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(3, state.current)
        assertEquals(5, state.goal)
        assertTrue(!state.completed)
        assertTrue(state.isLoaded)
    }

    @Test
    fun increment_incrementsCountAndReloads() = runTest {
        val viewModel = newViewModel()
        viewModel.refresh()

        viewModel.increment()

        assertEquals(1, viewModel.uiState.value.current)
    }

    @Test
    fun increment_reachingGoal_setsCompletedTrue() = runTest {
        val counterDao = FakeCounterDailyProgressDao()
        counterDao.rows[exercise.id to today.toString()] = CounterDailyProgress(exercise.id, today.toString(), 4, false)
        val viewModel = newViewModel(counterDao = counterDao)
        viewModel.refresh()

        viewModel.increment()

        assertTrue(viewModel.uiState.value.completed)
    }

    @Test
    fun adjustSubCounter_oneKey_doesNotAffectAnother() = runTest {
        val viewModel = newViewModel()
        viewModel.refresh()

        viewModel.adjustSubCounter(EXERCISE_KEY_PUSHUP, +2)

        val state = viewModel.uiState.value
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT + 2, state.subCounters[EXERCISE_KEY_PUSHUP])
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT, state.subCounters[EXERCISE_KEY_SITUP])
    }

    @Test
    fun subCounterValuesForDate_pastDateNoData_returnsAllNull() = runTest {
        val viewModel = newViewModel()
        viewModel.refresh()

        val values = viewModel.subCounterValuesForDate(today.minusDays(30))

        assertTrue(values.values.all { it == null })
    }

    @Test
    fun subCounterValuesForDate_pastDateWithData_returnsStoredValues() = runTest {
        val subCounterDao = FakeExerciseSubCounterProgressDao()
        val pastDate = today.minusDays(3)
        subCounterDao.rows[EXERCISE_KEY_PUSHUP to pastDate.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, pastDate.toString(), 10)
        val viewModel = newViewModel(subCounterDao = subCounterDao)
        viewModel.refresh()

        val values = viewModel.subCounterValuesForDate(pastDate)

        assertEquals(10, values[EXERCISE_KEY_PUSHUP])
        assertNull(values[EXERCISE_KEY_SITUP])
    }

    @Test
    fun adjustSubCounterForDate_pastDate_adjustsThatDatesValue_notToday() = runTest {
        val viewModel = newViewModel()
        val pastDate = LocalDate.now().minusDays(3)

        viewModel.adjustSubCounterForDate(EXERCISE_KEY_PUSHUP, pastDate, +2)

        val values = viewModel.subCounterValuesForDate(pastDate)
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT + 2, values[EXERCISE_KEY_PUSHUP])
    }

    @Test
    fun refresh_populatesTotalCount() = runTest {
        val viewModel = newViewModel()
        viewModel.refresh()
        assertEquals(viewModel.uiState.value.completedDates.size, viewModel.uiState.value.totalCount)
    }
}
