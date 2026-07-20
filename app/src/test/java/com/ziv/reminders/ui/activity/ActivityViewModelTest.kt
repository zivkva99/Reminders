package com.ziv.reminders.ui.activity

import com.ziv.reminders.data.ActivityDataSource
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.ReadingSessionLog
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.data.ScheduleCursorProgress
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.engine.HabitEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

private class FakeHabitInstanceDao(private val instances: Map<Long, HabitInstance>) : HabitInstanceDao {
    override suspend fun getById(id: Long) = instances[id]
    override suspend fun getAll() = instances.values.toList()
    override suspend fun insertIfAbsent(instance: HabitInstance) { /* unused in this test */ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    private val today = LocalDate.of(2026, 7, 19)
    private val exercise = HabitInstance(EXERCISE_HABIT_INSTANCE_ID, "COUNTER", "Exercise", 0b1111111, "t", "b", counterGoal = 5)
    private val reading = HabitInstance(READING_HABIT_INSTANCE_ID, "TIMER", "Reading", 0b0011111, "t", "b", counterGoal = null, timerTargetSeconds = 900)
    private val tanakh = HabitInstance(TANAKH_HABIT_INSTANCE_ID, "SCHEDULE_CURSOR", "Tanakh", 0b0011111, "t", "b", counterGoal = null)

    @Before
    fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun resetMainDispatcher() { Dispatchers.resetMain() }

    private fun dataSource(
        counterDao: com.ziv.reminders.data.CounterDailyProgressDao,
        timerDao: com.ziv.reminders.data.TimerDailyProgressDao,
        cursorProgressDao: com.ziv.reminders.data.ScheduleCursorProgressDao,
        cursorDailyDao: com.ziv.reminders.data.ScheduleCursorDailyProgressDao,
    ): ActivityDataSource {
        val counterRepo = CounterHabitRepository(counterDao)
        val timerRepo = TimerHabitRepository(timerDao, SystemClock)
        val cursorRepo = ScheduleCursorRepository(cursorProgressDao, cursorDailyDao, emptyList())
        val engine = HabitEngine(counterRepo, timerRepo, cursorRepo)
        val dao = FakeHabitInstanceDao(mapOf(EXERCISE_HABIT_INSTANCE_ID to exercise, READING_HABIT_INSTANCE_ID to reading, TANAKH_HABIT_INSTANCE_ID to tanakh))
        return object : ActivityDataSource {
            override val habitInstanceDao = dao
            override val counterHabitRepository = counterRepo
            override val timerHabitRepository = timerRepo
            override val scheduleCursorRepository = cursorRepo
            override val habitEngine = engine
        }
    }

    @Test
    fun refresh_populatesAllThreeSectionsAndComboStreak() = runTest {
        val counterDao = com.ziv.reminders.ui.exercise.FakeCounterDailyProgressDaoForActivityTest()
        counterDao.rows[EXERCISE_HABIT_INSTANCE_ID to today.toString()] = CounterDailyProgress(EXERCISE_HABIT_INSTANCE_ID, today.toString(), 5, true)
        val timerDao = com.ziv.reminders.ui.exercise.FakeTimerDailyProgressDaoForActivityTest()
        timerDao.rows[READING_HABIT_INSTANCE_ID to today.toString()] = com.ziv.reminders.data.TimerDailyProgress(READING_HABIT_INSTANCE_ID, today.toString(), 900, 0, true, 1L, null)
        val cursorProgressDao = com.ziv.reminders.ui.exercise.FakeScheduleCursorProgressDaoForActivityTest()
        val cursorDailyDao = com.ziv.reminders.ui.exercise.FakeScheduleCursorDailyProgressDaoForActivityTest()
        cursorDailyDao.rows[TANAKH_HABIT_INSTANCE_ID to today.toString()] = com.ziv.reminders.data.ScheduleCursorDailyProgress(TANAKH_HABIT_INSTANCE_ID, today.toString(), 1, true)

        val viewModel = ActivityViewModel(dataSource(counterDao, timerDao, cursorProgressDao, cursorDailyDao))
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(true, state.isLoaded)
        assertEquals(1, state.exercise.totalCount)
        assertEquals(1, state.reading.totalCount)
        assertEquals(1, state.tanakh.totalCount)
        assertEquals(1, state.comboStreakThisWeek)
    }
}
