package com.ziv.reminders.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.ComputedScheduleProgress
import com.ziv.reminders.data.ComputedScheduleWatchLog
import com.ziv.reminders.data.HabitInstance
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComputedScheduleStatsViewModelTest {

    // Takes the test's TestCoroutineScheduler and wires it into Room's query coroutine context —
    // matching DashboardViewModelTest's own newDb() pattern exactly — so refresh()'s suspending
    // DAO calls run on a controllable virtual-time dispatcher instead of Room's real background
    // thread pool; testScheduler.advanceUntilIdle() after each refresh() call then deterministically
    // drains them before assertions run.
    private fun newDb(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(scheduler))
            .build()
    }

    @Test
    fun refresh_noWatchLogHistory_populatesEmptyState() = runTest {
        val db = newDb(testScheduler)
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(
                id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
                notificationTitle = "t", notificationBody = "b", counterGoal = null,
                anchorItemNumber = 542, anchorDate = "2026-07-14", intervalDays = 7,
            )
        )
        val viewModel = ComputedScheduleStatsViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.streak)
        assertEquals(0, state.totalCount)
        assertTrue(state.completedDates.isEmpty())

        db.close()
    }

    @Test
    fun refresh_withWatchLogHistory_populatesStreakTotalAndHeatmapDates() = runTest {
        val db = newDb(testScheduler)
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(
                id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
                notificationTitle = "t", notificationBody = "b", counterGoal = null,
                anchorItemNumber = 542, anchorDate = "2026-07-14", intervalDays = 7,
            )
        )
        db.computedScheduleProgressDao().insertIfAbsent(ComputedScheduleProgress(4L, nextItemNumber = 544))
        val today = LocalDate.now()
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = today.minusDays(1).toString(), episodeNumber = 542))
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = today.toString(), episodeNumber = 543))
        val viewModel = ComputedScheduleStatsViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.streak)
        assertEquals(2, state.totalCount)
        assertEquals(setOf(today.minusDays(1), today), state.completedDates)

        db.close()
    }
}
