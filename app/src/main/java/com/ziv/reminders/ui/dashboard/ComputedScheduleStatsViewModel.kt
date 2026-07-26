package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.CPP_WEEKLY_HABIT_INSTANCE_ID
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.ui.activity.ActivitySectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Added per the Scope Revision (see the section below the CEO Phase 1 header). Deliberately reuses
 * ActivitySectionState/HabitStatsSummary (ui/activity/ActivityScreen.kt) rather than defining a
 * parallel data class — Exercise/Reading/Tanakh's stats already share this exact
 * streak/totalCount/completedDates shape, and this kind's stats are the same shape too (a
 * Set<LocalDate> of watched days), so a new type would just be a rename.
 *
 * Deliberately its own small ViewModel over DashboardDataSource, not a 4th section folded into
 * ActivityViewModel/ActivityDataSource — that combined ViewModel/interface already carries
 * Exercise/Reading/Tanakh-specific concerns (comboStreakThisWeek, per-session delete, etc.) this
 * kind has no equivalent of; DashboardDataSource already exposes everything this screen needs
 * (habitInstanceDao, computedScheduleRepository, habitEngine) with no interface change required.
 */
class ComputedScheduleStatsViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivitySectionState())
    val uiState: StateFlow<ActivitySectionState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instance = dataSource.habitInstanceDao.getById(CPP_WEEKLY_HABIT_INSTANCE_ID) ?: return@launch

            val dates = HabitStats.parseDates(dataSource.computedScheduleRepository.completedDates(instance))
            val streak = dataSource.habitEngine.currentStreak(instance, today)

            _uiState.value = ActivitySectionState(streak, HabitStats.totalCount(dates), dates)
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = ComputedScheduleStatsViewModel(dataSource) as T
            }
    }
}
