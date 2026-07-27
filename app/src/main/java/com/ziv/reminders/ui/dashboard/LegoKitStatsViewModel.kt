package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.data.LEGO_KIT_HABIT_INSTANCE_ID
import com.ziv.reminders.ui.activity.ActivitySectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

// Mirrors ComputedScheduleStatsViewModel's shape exactly — its own small ViewModel over
// DashboardDataSource, not folded into ActivityViewModel (that combined ViewModel already
// carries Exercise/Reading/Tanakh-specific concerns this kind has none of).
class LegoKitStatsViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivitySectionState())
    val uiState: StateFlow<ActivitySectionState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instance = dataSource.habitInstanceDao.getById(LEGO_KIT_HABIT_INSTANCE_ID) ?: return@launch

            val dates = HabitStats.parseDates(dataSource.counterHabitRepository.completedDates(instance))
            val streak = dataSource.habitEngine.currentStreak(instance, today)

            _uiState.value = ActivitySectionState(streak, HabitStats.totalCount(dates), dates)
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = LegoKitStatsViewModel(dataSource) as T
            }
    }
}
