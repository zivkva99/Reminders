package com.ziv.reminders.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.isEnabledDay
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
class LegoKitStatsViewModelTest {

    private fun newDb(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(scheduler))
            .build()
    }

    @Test
    fun refresh_noHistory_populatesEmptyState() = runTest {
        val db = newDb(testScheduler)
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(5L, "COUNTER", "Lego Kit", 0b0011111, "t", "b", counterGoal = 1)
        )
        val viewModel = LegoKitStatsViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.streak)
        assertEquals(0, state.totalCount)
        assertTrue(state.completedDates.isEmpty())

        db.close()
    }

    // Unlike ComputedScheduleStatsViewModelTest's equivalent test (C++ Weekly's mask is
    // all-days, so "today" and "today.minusDays(1)" are always both enabled), Lego Kit's mask
    // is Sun-Thu — a raw today/today.minusDays(1) pair would be flaky whenever the real test-run
    // date happens to land on a Sunday (yesterday = Saturday, a disabled day, would be skipped by
    // the mask-aware streak calculator rather than counted, per Task 1.5's fix), the same class
    // of wall-clock-dependence already tracked in TODOS.md for ActivityViewModelTest. Walking
    // back to the two most recent *enabled* days instead makes this test correct regardless of
    // what real-world weekday it happens to run on.
    private val mask = 0b0011111

    private fun mostRecentEnabledDayOnOrBefore(date: LocalDate): LocalDate {
        var d = date
        while (!isEnabledDay(d, mask)) d = d.minusDays(1)
        return d
    }

    @Test
    fun refresh_withCompletedDays_populatesStreakTotalAndHeatmapDates() = runTest {
        val db = newDb(testScheduler)
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(5L, "COUNTER", "Lego Kit", 0b0011111, "t", "b", counterGoal = 1)
        )
        val mostRecent = mostRecentEnabledDayOnOrBefore(LocalDate.now())
        val secondMostRecent = mostRecentEnabledDayOnOrBefore(mostRecent.minusDays(1))
        db.counterDailyProgressDao().upsert(
            CounterDailyProgress(habitInstanceId = 5L, date = secondMostRecent.toString(), count = 1, completed = true)
        )
        db.counterDailyProgressDao().upsert(
            CounterDailyProgress(habitInstanceId = 5L, date = mostRecent.toString(), count = 1, completed = true)
        )
        val viewModel = LegoKitStatsViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.streak)
        assertEquals(2, state.totalCount)
        assertEquals(setOf(secondMostRecent, mostRecent), state.completedDates)

        db.close()
    }
}
