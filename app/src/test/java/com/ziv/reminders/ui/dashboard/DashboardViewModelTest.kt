package com.ziv.reminders.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardViewModelTest {

    @Test
    fun refresh_oneHabitNotYetDone_populatesOneRow() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(1, state.habits.size)
        assertEquals(HabitStatus.CounterStatus(current = 0, goal = 5, completed = false), state.habits[0].status)

        db.close()
    }

    @Test
    fun onIncrement_updatesStatusAndCompletion() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        repeat(5) {
            viewModel.onIncrement(1L)
            testScheduler.advanceUntilIdle()
        }

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.CounterStatus
        assertEquals(5, status.current)
        assertTrue(status.completed)

        db.close()
    }

    @Test
    fun refresh_habitDisabledToday_isExcludedFromRows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "COUNTER", "Never", 0, "t", "b", 5)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(1, state.habits.size)
        assertEquals("Exercise", state.habits[0].name)

        db.close()
    }

    @Test
    fun refresh_timerHabitNotYetStarted_populatesRowAtFullTarget() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "TIMER", "Reading", 0b1111111, "t", "b", null, timerTargetSeconds = 900)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertEquals(900, status.remainingSeconds)
        assertEquals(false, status.isRunning)

        db.close()
    }

    @Test
    fun onToggleTimer_notRunning_startsTheServiceAndOptimisticallyFlipsIsRunning() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "TIMER", "Reading", 0b1111111, "t", "b", null, timerTargetSeconds = 900)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        viewModel.onToggleTimer(2L, context)

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertTrue(status.isRunning)
        val startedService = org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .nextStartedService
        assertEquals(com.ziv.reminders.service.TimerService.ACTION_START, startedService?.action)
        assertEquals(2L, startedService?.getLongExtra(com.ziv.reminders.service.TimerService.EXTRA_HABIT_INSTANCE_ID, -1L))

        db.close()
    }
}
