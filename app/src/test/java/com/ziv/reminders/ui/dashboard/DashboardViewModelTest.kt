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
import kotlin.test.assertFalse
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
    fun onUndoIncrement_reversesTheMostRecentIncrement() = runTest {
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
        viewModel.onIncrement(1L)
        testScheduler.advanceUntilIdle()

        viewModel.onUndoIncrement(1L)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.CounterStatus
        assertEquals(0, status.current)

        db.close()
    }

    @Test
    fun refresh_habitDisabledToday_isStillIncludedInRows() = runTest {
        // enabledDaysMask still governs notification scheduling (HabitScheduler) and
        // streak/escalation math (StreakCalculator, CrossHabitEvaluator) — it no longer hides a
        // row from the dashboard on the habit's off-days.
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
        assertEquals(2, state.habits.size)
        assertEquals(setOf("Exercise", "Never"), state.habits.map { it.name }.toSet())

        db.close()
    }

    @Test
    fun refresh_populatesEnabledDaysMaskFromTheInstance() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(5L, "COUNTER", "Lego Kit", 0b0011111, "t", "b", counterGoal = 1)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(0b0011111, viewModel.uiState.value.habits[0].enabledDaysMask)

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

        viewModel.onToggleTimer(2L, context, displayedRemainingSeconds = 900)

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertTrue(status.isRunning)
        val startedService = org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .nextStartedService
        assertEquals(com.ziv.reminders.service.TimerService.ACTION_START, startedService?.action)
        assertEquals(2L, startedService?.getLongExtra(com.ziv.reminders.service.TimerService.EXTRA_HABIT_INSTANCE_ID, -1L))

        db.close()
    }

    @Test
    fun onToggleTimer_running_optimisticFlipUsesTheCurrentlyDisplayedRemainingSeconds_notTheStaleBaseline() = runTest {
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

        // Start — the ViewModel's own baseline stays 900; only the Composable ticks it locally.
        viewModel.onToggleTimer(2L, context, displayedRemainingSeconds = 900)

        // Simulate the Composable having ticked down locally to 841 (59s elapsed) by the time
        // the user taps Stop — the optimistic flip must carry that displayed value forward, not
        // the stale 900 baseline the ViewModel itself never updated while running.
        viewModel.onToggleTimer(2L, context, displayedRemainingSeconds = 841)

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertEquals(841, status.remainingSeconds)
        assertEquals(false, status.isRunning)

        db.close()
    }

    @Test
    fun onMarkRead_advancesTheCursorAndMarksTodayCompleted() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(3L, "SCHEDULE_CURSOR", "Tanakh", 0b1111111, "t", "b", null)
        )
        val schedule = listOf(
            com.ziv.reminders.data.ScheduleEntry("א", "א׳", java.time.LocalDate.now()),
        )
        val viewModel = DashboardViewModel(TestAppContainer(db, schedule))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        viewModel.onMarkRead(3L)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.ScheduleCursorStatus
        assertTrue(status.completed)

        db.close()
    }

    @Test
    fun onResetReadingToday_idleCompletedDay_resetsProgressBackToFullTarget() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            // resetToday() runs its writes inside db.withTransaction (TestAppContainer's real
            // transactional wiring, Task 2) — Room's transaction executor is derived from the
            // StandardTestDispatcher above, so it always drains on whatever thread calls
            // advanceUntilIdle(), which Robolectric treats as the main thread. This is a JVM
            // test artifact, not a real UI thread being blocked, so allowMainThreadQueries() is
            // the standard escape hatch — see the other two new tests below for the same reason.
            .allowMainThreadQueries()
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "TIMER", "Reading", 0b1111111, "t", "b", null, timerTargetSeconds = 900)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        viewModel.onResetReadingToday(2L, context)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertEquals(900, status.remainingSeconds)
        assertEquals(false, status.completed)

        db.close()
    }

    @Test
    fun onResetReadingToday_activeSession_stopsItFirstThenResetsToFullTarget() = runTest {
        // Starts the session directly via the repository, not via onToggleTimer — Robolectric's
        // startService() only records the Intent, it never actually invokes
        // TimerService.onStartCommand, so onToggleTimer alone would never create a real active
        // session row here. This is the only way to exercise resetToday's active-session path
        // at this (ViewModel/integration) layer; the repository layer's own equivalent test
        // (TimerHabitRepositoryTest.resetToday_activeSession_...) already covers it via fakes.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            // See onResetReadingToday_idleCompletedDay_resetsProgressBackToFullTarget above for why.
            .allowMainThreadQueries()
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "TIMER", "Reading", 0b1111111, "t", "b", null, timerTargetSeconds = 900)
        )
        val container = TestAppContainer(db)
        val instance = db.habitInstanceDao().getById(2L)!!
        container.timerHabitRepository.start(instance, java.time.LocalDate.now())
        val viewModel = DashboardViewModel(container)
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        viewModel.onResetReadingToday(2L, context)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertEquals(900, status.remainingSeconds)
        assertEquals(false, status.completed)
        assertEquals(false, status.isRunning)

        db.close()
    }

    @Test
    fun onResetReadingToday_sendsStopActionToTimerServiceAfterResetting() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            // See onResetReadingToday_idleCompletedDay_resetsProgressBackToFullTarget above for why.
            .allowMainThreadQueries()
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "TIMER", "Reading", 0b1111111, "t", "b", null, timerTargetSeconds = 900)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        viewModel.onResetReadingToday(2L, context)
        testScheduler.advanceUntilIdle()

        val startedService = org.robolectric.Shadows.shadowOf(context as android.app.Application).nextStartedService
        assertEquals(com.ziv.reminders.service.TimerService.ACTION_STOP, startedService?.action)
        assertEquals(2L, startedService?.getLongExtra(com.ziv.reminders.service.TimerService.EXTRA_HABIT_INSTANCE_ID, -1L))

        db.close()
    }

    @Test
    fun onUndoMarkRead_reversesTheMostRecentMarkRead() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(3L, "SCHEDULE_CURSOR", "Tanakh", 0b1111111, "t", "b", null)
        )
        val schedule = listOf(
            com.ziv.reminders.data.ScheduleEntry("א", "א׳", java.time.LocalDate.now()),
        )
        val viewModel = DashboardViewModel(TestAppContainer(db, schedule))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()
        viewModel.onMarkRead(3L)
        testScheduler.advanceUntilIdle()

        viewModel.onUndoMarkRead(3L)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.ScheduleCursorStatus
        assertFalse(status.completed)

        db.close()
    }

    @Test
    fun onMarkNextWatched_dueToday_advancesTheEpisodeNumberByOne() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            // markNextWatched runs its writes inside db.withTransaction (TestAppContainer's real
            // transactional wiring) — see onResetReadingToday_idleCompletedDay_... above for why
            // allowMainThreadQueries() is the standard escape hatch here.
            .allowMainThreadQueries()
            .build()
        val today = java.time.LocalDate.now()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(
                id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
                notificationTitle = "t", notificationBody = "b", counterGoal = null,
                anchorItemNumber = 542, anchorDate = today.toString(), intervalDays = 7,
            )
        )
        db.computedScheduleProgressDao().insertIfAbsent(
            com.ziv.reminders.data.ComputedScheduleProgress(habitInstanceId = 4L, nextItemNumber = 542)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val watchedEpisode = viewModel.onMarkNextWatched(4L)
        testScheduler.advanceUntilIdle()

        assertEquals(542, watchedEpisode) // the episode that WAS next-to-watch, before advancing
        val status = viewModel.uiState.value.habits[0].status as HabitStatus.ComputedScheduleStatus
        assertEquals(543, status.nextItemNumber)

        db.close()
    }

    @Test
    fun onMarkNextWatched_notYetDue_isNoOp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        val today = java.time.LocalDate.now()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(
                id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
                notificationTitle = "t", notificationBody = "b", counterGoal = null,
                anchorItemNumber = 542, anchorDate = today.plusDays(7).toString(), intervalDays = 7,
            )
        )
        db.computedScheduleProgressDao().insertIfAbsent(
            com.ziv.reminders.data.ComputedScheduleProgress(habitInstanceId = 4L, nextItemNumber = 542)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val watchedEpisode = viewModel.onMarkNextWatched(4L)
        testScheduler.advanceUntilIdle()

        assertEquals(null, watchedEpisode) // no-op — nothing due, so no Snackbar should show
        val status = viewModel.uiState.value.habits[0].status as HabitStatus.ComputedScheduleStatus
        assertEquals(542, status.nextItemNumber)

        db.close()
    }
}
