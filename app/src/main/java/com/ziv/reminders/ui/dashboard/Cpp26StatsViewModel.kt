package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.CPP26_HABIT_INSTANCE_ID
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.ui.activity.ActivitySectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Mirrors ComputedScheduleStatsViewModel's exact shape (its own doc comment explains why a small
 * per-kind ViewModel over DashboardDataSource, not a 4th/5th section folded into
 * ActivityViewModel/ActivityDataSource) — this is the same "own small stats screen" pattern,
 * now for the second SCHEDULE_CURSOR-kind instance (C++26) rather than reusing TanakhStatsScreen,
 * which is hardcoded to ActivityViewModel's `uiState.tanakh` section and Tanakh's own
 * today-only-undo day-edit dialog.
 */
class Cpp26StatsViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivitySectionState())
    val uiState: StateFlow<ActivitySectionState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instance = dataSource.habitInstanceDao.getById(CPP26_HABIT_INSTANCE_ID) ?: return@launch

            val dates = HabitStats.parseDates(dataSource.scheduleCursorRepository.completedDates(instance))
            val streak = dataSource.habitEngine.currentStreak(instance, today)

            _uiState.value = ActivitySectionState(streak, HabitStats.totalCount(dates), dates)
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = Cpp26StatsViewModel(dataSource) as T
            }
    }
}
